package com.syntra.nexus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

data class AppUsageSummary(
    val packageName: String,
    val appLabel: String,
    val foregroundMillis: Long
)

data class ScreenUsageSummary(
    val totalScreenOnMillis: Long?,
    val appUsage: List<AppUsageSummary>,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val screenTimelineAvailable: Boolean
)

object ScreenSessionAggregator {
    data class Event(val timestampMillis: Long, val interactive: Boolean)

    fun totalInteractiveMillis(
        events: List<Event>,
        startMillis: Long,
        endMillis: Long
    ): Long {
        require(endMillis >= startMillis)
        var interactive = false
        var sessionStart = startMillis
        var total = 0L

        events.asSequence()
            .filter { it.timestampMillis in startMillis..endMillis }
            .sortedBy { it.timestampMillis }
            .forEach { event ->
                if (event.interactive && !interactive) {
                    interactive = true
                    sessionStart = event.timestampMillis
                } else if (!event.interactive && interactive) {
                    total += (event.timestampMillis - sessionStart).coerceAtLeast(0L)
                    interactive = false
                }
            }

        if (interactive) total += (endMillis - sessionStart).coerceAtLeast(0L)
        return total
    }
}

class ActivityUsageRepository(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadToday(): ScreenUsageSummary {
        check(hasUsageAccess()) { "Usage Access permission has not been granted." }

        val end = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = end
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val appUsage = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start,
            end
        ).orEmpty()
            .asSequence()
            .filter { it.totalTimeInForeground > 0L }
            .map { usage ->
                val label = try {
                    val appInfo = context.packageManager.getApplicationInfo(usage.packageName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    usage.packageName
                }
                AppUsageSummary(
                    packageName = usage.packageName,
                    appLabel = label,
                    foregroundMillis = usage.totalTimeInForeground
                )
            }
            .sortedByDescending { it.foregroundMillis }
            .toList()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return ScreenUsageSummary(null, appUsage, start, end, false)
        }

        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val screenEvents = mutableListOf<ScreenSessionAggregator.Event>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE ->
                    screenEvents += ScreenSessionAggregator.Event(event.timeStamp, true)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    screenEvents += ScreenSessionAggregator.Event(event.timeStamp, false)
            }
        }

        return ScreenUsageSummary(
            totalScreenOnMillis = ScreenSessionAggregator.totalInteractiveMillis(
                screenEvents,
                start,
                end
            ),
            appUsage = appUsage,
            periodStartMillis = start,
            periodEndMillis = end,
            screenTimelineAvailable = screenEvents.isNotEmpty()
        )
    }
}

fun formatDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} giờ ${minutes} phút" else "${minutes} phút"
}
