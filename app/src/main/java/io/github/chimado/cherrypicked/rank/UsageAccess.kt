package io.github.chimado.cherrypicked.rank

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

private const val DAY_MS = 24L * 3600 * 1000

/** Foreground time per package over 14 days, only if the user granted usage access; never prompts. */
@Suppress("DEPRECATION") // unsafeCheckOpNoThrow: the plan's choice; still works on API 36
fun foregroundLast14d(ctx: Context): Map<String, Long> {
    val ops = ctx.getSystemService(AppOpsManager::class.java)
    if (ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) != AppOpsManager.MODE_ALLOWED)
        return emptyMap()
    val now = System.currentTimeMillis()
    return ctx.getSystemService(UsageStatsManager::class.java)
        .queryAndAggregateUsageStats(now - 14 * DAY_MS, now)
        .mapValues { it.value.totalTimeInForeground }
}
