package com.uniulm.social_media_interventions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the app's own "YouWillNeverKillMe" broadcast (sent by
 * [AppCheckerService] and [MainActivity] when the accessibility service is
 * being torn down) and immediately restarts [MainActivity] to keep the study
 * running.
 */
class RestartServiceReceiver:BroadcastReceiver() {
    val TAG = "RestartServiceReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        Log.e(TAG, "onReceive")
        context.startService(Intent(context.applicationContext, MainActivity::class.java))
    }

}