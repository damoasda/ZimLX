package org.zimmob.zimlx.backup

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.zimmob.zimlx.R
import org.zimmob.zimlx.Utilities
import org.zimmob.zimlx.blur.BlurWallpaperProvider
import org.zimmob.zimlx.config.FeatureFlags
import org.zimmob.zimlx.views.ThemeActivity

@SuppressLint("Registered")
open class BackupBaseActivity : ThemeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(Utilities.getPrefs(this).primaryColor)
        toolbar.setTitleTextColor(resources.getColor(R.color.white))
        toolbar.navigationIcon = resources.getDrawable(R.drawable.ic_arrow_back_white_24px)
        setSupportActionBar(toolbar)

        if (FeatureFlags.getCurrentTheme() != 2)
            BlurWallpaperProvider.applyBlurBackground(this)
    }

    override fun setContentView(v: View) {
        val contentParent = findViewById<ViewGroup>(R.id.content)
        contentParent.removeAllViews()
        contentParent.addView(v)
    }

    override fun setContentView(resId: Int) {
        val contentParent = findViewById<ViewGroup>(R.id.content)
        contentParent.removeAllViews()
        LayoutInflater.from(this).inflate(resId, contentParent)
    }

    override fun setContentView(v: View, lp: ViewGroup.LayoutParams) {
        val contentParent = findViewById<ViewGroup>(R.id.content)
        contentParent.removeAllViews()
        contentParent.addView(v, lp)
    }
}