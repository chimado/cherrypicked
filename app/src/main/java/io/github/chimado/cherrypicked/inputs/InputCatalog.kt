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

    fun launchIntent(id: String): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
