/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zimmob.zimlx.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.android.launcher3.*
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.CustomIconUtils
import com.google.android.apps.nexuslauncher.clock.CustomClock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import org.zimmob.zimlx.get
import org.zimmob.zimlx.iconpack.*
import org.zimmob.zimlx.toTitleCase
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class IconPackImpl(context: Context, packPackageName: String) : IconPack(context, packPackageName) {

    private val TAG = "IconPackImpl"

    private val packComponents: MutableMap<ComponentName, Entry> = HashMap()
    private val packCalendars: MutableMap<ComponentName, String> = HashMap()
    private val packClocks: MutableMap<Int, CustomClock.Metadata> = HashMap()
    private val packDynamicDrawables: MutableMap<Int, DynamicDrawable.Metadata> = HashMap()
    private var packMask: IconMask = IconMask()
    private val defaultPack = DefaultPack(context)
    private val packResources = context.packageManager.getResourcesForApplication(packPackageName)
    private val prefs by lazy { Utilities.getZimPrefs(context) }
    override val entries get() = packComponents.values.toList()

    init {
        if (prefs.showDebugInfo) {
            Log.d(TAG, "init pack $packPackageName on ${Looper.myLooper()!!.thread.name}", Throwable())
        }
        executeLoadPack()
    }

    override val packInfo = IconPackList.PackInfoImpl(context, packPackageName)

    override fun onDateChanged() {
        val apps = LauncherAppsCompat.getInstance(context)
        val model = LauncherAppState.getInstance(context).model
        val shortcutManager = DeepShortcutManager.getInstance(context)
        for (user in UserManagerCompat.getInstance(context).userProfiles) {
            packCalendars.keys.forEach {
                val pkg = it.packageName
                if (!apps.getActivityList(pkg, user).isEmpty()) {
                    CustomIconUtils.reloadIcon(shortcutManager, model, user, pkg)
                }
            }
        }
    }

    override fun loadPack() {
        try {
            val startTime = System.currentTimeMillis()
            val res = packResources
            val compStart = "ComponentInfo{"
            val compStartlength = compStart.length
            val compEnd = "}"
            val compEndLength = compEnd.length

            val parseXml = getXml("appfilter") ?: throw IllegalStateException("parser is null")
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType == XmlPullParser.START_TAG) {
                    val name = parseXml.name
                    val isCalendar = name == "calendar"
                    when {
                        isCalendar || name == "item" -> {
                            var componentName: String? = parseXml[null, "component"]
                            val drawableName = parseXml[if (isCalendar) "prefix" else "drawable"]
                            if (componentName != null && drawableName != null) {
                                if (componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                                    componentName = componentName.substring(compStartlength, componentName.length - compEndLength)
                                }
                                val parsed = ComponentName.unflattenFromString(componentName)
                                if (parsed != null) {
                                    if (isCalendar) {
                                        packCalendars[parsed] = drawableName
                                    } else {
                                        packComponents[parsed] = Entry(drawableName)
                                    }
                                }
                            }
                        }
                        name == "dynamic-clock" -> {
                            val drawableName = parseXml["drawable"]
                            if (drawableName != null) {
                                val drawableId = getDrawableId(drawableName)
                                if (parseXml is XmlResourceParser && drawableId != 0) {
                                    packClocks[drawableId] = CustomClock.Metadata(
                                            parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
                                            parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
                                            parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
                                            parseXml.getAttributeIntValue(null, "defaultHour", 0),
                                            parseXml.getAttributeIntValue(null, "defaultMinute", 0),
                                            parseXml.getAttributeIntValue(null, "defaultSecond", 0))
                                }
                            }
                        }
                        name == "scale" -> {
                            packMask.scale = parseXml["factor"]!!.toFloat()
                            if (packMask.scale > 0x7f070000) {
                                packMask.scale = packResources.getDimension(packMask.scale.toInt())
                            }
                        }
                        name == "iconback" -> {
                            // TODO: handle packs with multiple masks
                            val drawableName = parseXml["img1"]
                            if (drawableName != null && !TextUtils.isEmpty(drawableName)) {
                                // Try if we can actually load the drawable. (Some icon packs define
                                // a resource for this which doesn't actually exist
                                // TODO: actually handle this in mask code
                                // entry.drawable
                                packMask.hasMask = true
                                packMask.iconBack = Entry(drawableName)
                            }
                        }
                        name == "iconmask" -> {
                            val drawableName = parseXml["img1"]
                            if (drawableName != null && !TextUtils.isEmpty(drawableName)) {
                                // Try if we can actually load the drawable. (Some icon packs define
                                // a resource for this which doesn't actually exist
                                // TODO: actually handle this in mask code
                                // entry.drawable
                                packMask.hasMask = true
                                packMask.iconMask = Entry(drawableName)
                            }
                        }
                        name == "iconupon" -> {
                            val drawableName = parseXml["img1"]
                            if (drawableName != null && !TextUtils.isEmpty(drawableName)) {
                                // Try if we can actually load the drawable. (Some icon packs define
                                // a resource for this which doesn't actually exist
                                // TODO: actually handle this in mask code
                                // entry.drawable
                                packMask.hasMask = true
                                packMask.iconUpon = Entry(drawableName)
                            }
                        }
                        name == "config" -> {
                            val onlyMaskLegacy = parseXml["onlyMaskLegacy"]
                            if (!TextUtils.isEmpty(onlyMaskLegacy)) {
                                packMask.onlyMaskLegacy = onlyMaskLegacy!!.toBoolean()
                            }
                        }
                    }
                }
            }
            // TODO: only run this on icon packs with oneplus intent filter to reduce overhead for others
            val parseDrawableXml = getXml("drawable")
            if (parseDrawableXml != null) {
                while (parseDrawableXml.next() != XmlPullParser.END_DOCUMENT) {
                    if (parseDrawableXml.eventType == XmlPullParser.START_TAG) {
                        val name = parseDrawableXml.name
                        if (name == "item") {
                            val dynamicDrawable = parseDrawableXml["dynamic_drawable"]
                            if (dynamicDrawable != null) {
                                val drawableId = res.getIdentifier(dynamicDrawable, "drawable",
                                        packPackageName)
                                if (drawableId != 0) {
                                    packDynamicDrawables[drawableId] = DynamicDrawable.Metadata(
                                            parseDrawableXml["xml"]!!,
                                            packPackageName)
                                }
                            }
                        }
                    }
                }
            }
            val endTime = System.currentTimeMillis()
            Log.d("IconPackImpl", "completed parsing pack $packPackageName in ${endTime - startTime}ms")
            return
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        Toast.makeText(context, "Failed to parse AppFilter", Toast.LENGTH_SHORT).show()
    }

    override fun getEntryForComponent(key: ComponentKey) = packComponents[key.componentName]

    override fun getIcon(name: String, iconDpi: Int): Drawable? {
        val drawableId = getDrawableId(name)
        if (drawableId != 0) {
            try {
                var drawable = packResources.getDrawable(drawableId)
                if (Utilities.ATLEAST_OREO && packClocks.containsKey(drawableId)) {
                    drawable = CustomClock.getClock(context, drawable, packClocks[drawableId], iconDpi)
                } else if (packDynamicDrawables.containsKey(drawableId)) {
                    drawable = DynamicDrawable.getIcon(context, drawable, packDynamicDrawables[drawableId]!!, iconDpi)
                }
                return drawable.mutate()
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Can't get drawable for name $name ($drawableId)", e)
            }
        }
        return null
    }

    override fun getIcon(launcherActivityInfo: LauncherActivityInfo, iconDpi: Int,
                         flattenDrawable: Boolean,
                         customIconEntry: IconPackManager.CustomIconEntry?,
                         iconProvider: ZimIconProvider?): Drawable? {
        ensureInitialLoadComplete()

        val component = launcherActivityInfo.componentName
        val drawableId = when {
            customIconEntry?.icon != null -> getDrawableId(customIconEntry.icon)
            packCalendars.containsKey(component) -> getDrawableId(packCalendars[component] + Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
            packComponents.containsKey(component) -> packComponents[component]!!.drawableId
            else -> 0
        }

        if (drawableId != 0) {
            try {
                var drawable = packResources.getDrawable(drawableId)
                if (Utilities.ATLEAST_OREO && packClocks.containsKey(drawableId)) {
                    drawable = CustomClock.getClock(context, drawable, packClocks[drawableId], iconDpi)
                } else if (packDynamicDrawables.containsKey(drawableId)) {
                    drawable = DynamicDrawable.getIcon(context, drawable, packDynamicDrawables[drawableId]!!, iconDpi)
                }
                return drawable.mutate()
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Can't get drawable for $component ($drawableId)", e)
            }
        }

        if (prefs.iconPackMasking && packMask.hasMask) {
            val baseIcon = defaultPack.getIcon(launcherActivityInfo, iconDpi, flattenDrawable,
                    customIconEntry, iconProvider)
            return packMask.getIcon(context, baseIcon)
        }

        return null
    }

    override fun getIcon(shortcutInfo: ShortcutInfoCompat, iconDpi: Int): Drawable? {
        ensureInitialLoadComplete()

        if (prefs.iconPackMasking && packMask.hasMask) {
            val baseIcon = defaultPack.getIcon(shortcutInfo, iconDpi)
            if (baseIcon != null) {
                return packMask.getIcon(context, baseIcon)
            }
        }

        return null
    }

    override fun newIcon(icon: Bitmap, itemInfo: ItemInfo,
                         customIconEntry: IconPackManager.CustomIconEntry?,
                         drawableFactory: ZimDrawableFactory): FastBitmapDrawable? {
        ensureInitialLoadComplete()

        if (Utilities.ATLEAST_OREO && itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            val component = itemInfo.targetComponent
            val drawableId = when {
                customIconEntry?.icon != null -> getDrawableId(customIconEntry.icon)
                packComponents.containsKey(component) -> packComponents[component]!!.drawableId
                else -> 0
            }
            if (packClocks.containsKey(drawableId)) {
                val drawable = packResources.getDrawable(drawableId)
                return drawableFactory.customClockDrawer.drawIcon(icon, drawable, packClocks[drawableId])
            } else if (packDynamicDrawables.containsKey(drawableId)) {
                val iconDpi = LauncherAppState.getIDP(context).fillResIconDpi
                val icn = DynamicDrawable.drawIcon(context, icon, packDynamicDrawables[drawableId]!!,
                        drawableFactory, iconDpi)
                if (icn != null) return icn
            }
            if (drawableId != 0) {
                return FastBitmapDrawable(icon)
            }
        }
        return null
    }

    override fun getAllIcons(callback: (List<PackEntry>) -> Unit, cancel: () -> Boolean, filter: (item: String) -> Boolean) {
        var lastSend = System.currentTimeMillis() - 900
        var tmpList = ArrayList<PackEntry>()
        val sendResults = { force: Boolean ->
            val current = System.currentTimeMillis()
            if (force || current - lastSend >= 1000) {
                callback(tmpList)
                tmpList = ArrayList()
                lastSend = current
            }
        }
        var found = false
        val startTime = System.currentTimeMillis()
        var entry: Entry
        try {
            val parser = getXml("drawable")
            Log.d("IconPackImpl", "initialized parser for pack $packPackageName in ${System.currentTimeMillis() - startTime}ms")
            while (parser != null && parser.next() != XmlPullParser.END_DOCUMENT) {
                if (cancel()) return
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if ("category" == parser.name) {
                    tmpList.add(CategoryTitle(parser["title"]!!))
                    sendResults(false)
                } else if ("item" == parser.name) {
                    val drawableName = parser["drawable"]!!
                    if (filter(drawableName)) {
                        val resId = getDrawableId(drawableName)
                        if (resId != 0) {
                            entry = Entry(drawableName, resId)
                            tmpList.add(entry)
                            sendResults(false)
                            found = true
                        }
                    }
                }
            }
            sendResults(true)
            if (found) {
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.getAllIcons(callback, cancel, filter)
    }

    override fun supportsMasking(): Boolean = packMask.hasMask

    private fun getXml(name: String): XmlPullParser? {
        val res: Resources
        try {
            res = context.packageManager.getResourcesForApplication(packPackageName)
            val resourceId = res.getIdentifier(name, "xml", packPackageName)
            return if (0 != resourceId) {
                context.packageManager.getXml(packPackageName, resourceId, null)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(res.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
                parser
            }
        } catch (e: PackageManager.NameNotFoundException) {
        } catch (e: IOException) {
        } catch (e: XmlPullParserException) {
        }
        return null
    }

    fun getDrawable(name: String, density: Int): Drawable? {
        val id = getDrawableId(name)
        return try {
            if (id != 0) packResources.getDrawableForDensity(id, density) else null
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't get drawable $id($name) from $packPackageName", e)
            null
        }
    }

    private val idCache = mutableMapOf<String, Int>()
    private fun getDrawableId(name: String) = packResources.getIdentifier(name, "drawable", packPackageName)// idCache.getOrPut(name) {    }

    fun createEntry(icon: Intent.ShortcutIconResource): Entry {
        val id = packResources.getIdentifier(icon.resourceName, null, null)
        val simpleName = packResources.getResourceEntryName(id)
        return Entry(simpleName, id)
    }

    inner class Entry(private val drawableName: String, val id: Int? = null) : IconPack.Entry() {

        override val displayName by lazy { drawableName.replace(Regex("""_+"""), " ").trim().toTitleCase() }
        override val identifierName = drawableName
        override val drawable: Drawable
            get() {
                try {
                    return packResources.getDrawable(drawableId)
                } catch (e: Resources.NotFoundException) {
                    throw Exception("Failed to get drawable $drawableId ($drawableName) from $packPackageName", e)
                }
            }
        val drawableId: Int by lazy { id ?: getDrawableId(drawableName) }

        override fun toCustomEntry() = IconPackManager.CustomIconEntry(packPackageName, drawableName)
    }

}