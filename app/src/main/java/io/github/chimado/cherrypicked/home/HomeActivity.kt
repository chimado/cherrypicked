package io.github.chimado.cherrypicked.home

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import io.github.chimado.cherrypicked.Theme

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Theme.BG_TOP, Theme.BG_BOTTOM),
            )
        })
    }
}
