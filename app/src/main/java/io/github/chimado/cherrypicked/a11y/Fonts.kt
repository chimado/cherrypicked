package io.github.chimado.cherrypicked.a11y

import android.content.Context
import android.graphics.Typeface

/** One Inter Regular per process (:a11y or :ui), shared by the panel and the notice (createFromAsset each time costs memory). */
object Fonts {
    private var regular: Typeface? = null
    fun regular(ctx: Context): Typeface = regular ?: Typeface.createFromAsset(ctx.assets, "Inter-Regular.ttf").also { regular = it }
}
