package com.uniulm.social_media_interventions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


/**
 * Fires when the 7-day study-end alarm scheduled by [TimerService] triggers.
 * Shows the "study completed" notification that opens [FinalQuestionnaire]
 * and marks the study as finished in shared preferences.
 */
class NotificationReceiver : BroadcastReceiver() {
    var finalquest_started=false
    override fun onReceive(context: Context, intent: Intent) {
        // Handle the notification click by opening the questionnaire activity
        Log.e("STUDYTIMER", "I AM IN RECIEVER")

        var notificationId = 1210
         val CHANNEL_ID = "thanks"
        val name = "Study completed"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val descriptionText = "Click to open the last questionnaire"
        val questionnaireIntent = Intent(context, FinalQuestionnaire::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, questionnaireIntent, 0 or PendingIntent.FLAG_IMMUTABLE)
         val builder = NotificationCompat.Builder(context, CHANNEL_ID)
           .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Study completed")
            .setContentText("Click to confirm that you have completed the study")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
           .setFullScreenIntent(pendingIntent, true)
             .setOngoing(true)
             .setAutoCancel(false)

        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        with(NotificationManagerCompat.from(context)) {

            notify(notificationId, builder.build())
        }

        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)


        Log.e("STUDYTIMER", "AFTER"+notificationId.toString()+ CHANNEL_ID.toString())

        finalquest_started=true
        val sharedPref = context.getSharedPreferences("InfiniteScroll", 0)
        sharedPref.edit().putBoolean("finalquest_started", finalquest_started).apply()
    }
}


