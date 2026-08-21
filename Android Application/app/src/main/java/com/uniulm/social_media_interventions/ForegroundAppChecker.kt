package com.uniulm.social_media_interventions

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Determines which app is currently in the foreground, via
 * [UsageStatsManager]. Requires the PACKAGE_USAGE_STATS ("Usage Access")
 * permission, which the app requests through [PermissionActivity].
 *
 * Used by [AppCheckerService] to find out which app the participant is
 * using. Replaces the third-party `com.rvalerio:fgchecker` library, which
 * is no longer resolvable now that JCenter (where it was hosted) has shut
 * down.
 */
class ForegroundAppChecker {

    /**
     * Returns the package name of the app most recently moved to the
     * foreground within the last 10 seconds, or `null` if none is found.
     */
    fun getForegroundApp(context: Context): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

        val end = System.currentTimeMillis()
        val start = end - 10_000

        val events = usageStatsManager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }

        return lastForegroundPackage
    }
}
