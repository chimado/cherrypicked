package io.github.chimado.cherrypicked.inputs

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import io.github.chimado.cherrypicked.home.TvInput

/** Lists passthrough (HDMI) TV inputs. No permission needed; no bitmaps, so it is safe in :a11y. */
class InputCatalog(private val ctx: Context) {
    fun inputs(): List<TvInput> =
        ctx.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
            .filter { it.isPassthroughInput }
            .mapNotNull { i ->
                val label = (i.loadCustomLabel(ctx) ?: i.loadLabel(ctx))?.toString().orEmpty()
                if (label.isBlank()) null else TvInput(i.id, label)
            }
            .sortedWith(naturalByLabel)

    fun launchIntent(id: String): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private val chunk = Regex("\\d+|\\D+")

/** Case-insensitive, ASCII digit runs compared numerically: "HDMI 2" < "HDMI 10". Never throws (it runs in :a11y). */
val naturalByLabel = Comparator<TvInput> { a, b ->
    val x = chunk.findAll(a.label.lowercase()).map { it.value }.toList()
    val y = chunk.findAll(b.label.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val r = if (x[i][0] in '0'..'9' && y[i][0] in '0'..'9') x[i].toBigInteger().compareTo(y[i].toBigInteger()) else x[i].compareTo(y[i])
        if (r != 0) return@Comparator r
    }
    x.size - y.size
}
