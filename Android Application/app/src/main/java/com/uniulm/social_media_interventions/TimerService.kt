package com.uniulm.social_media_interventions

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.*
import java.text.SimpleDateFormat

/**
 * Schedules the alarm that ends the 7-day study: sets an exact [AlarmManager]
 * trigger that fires [NotificationReceiver] once the study period is over.
 */
class TimerService : Service() {
    var startstudy= 0L
    var sevdaytimer = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleNotification()

        // Return START_NOT_STICKY to indicate that the service should not be restarted if it's killed
        return START_NOT_STICKY
    }

    /**
     * Records the study start time and schedules the 7-day end-of-study alarm.
     */
   private fun scheduleNotification() {
        val sharedPreferences = getSharedPreferences( "InfiniteScroll", 0)
        startstudy = Calendar.getInstance().timeInMillis

        Log.e("STUDYTIMER","TIMERSERVICE")
       val sevenDaystimer = 7L * 24 * 60 * 60 * 1000L // 7 days in milliseconds for study

        val triggerTimeMillis = sevenDaystimer + startstudy
        val notificationIntent = Intent(this, NotificationReceiver::class.java)

       val pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        Log.e("STUDYTIMER", "TIMERSERVICE AFTER")
        sevdaytimer = true
        sharedPreferences.edit().putBoolean("startstudy", sevdaytimer).apply()

        val formattedExpectedEndDate = convertMillisToDate(triggerTimeMillis)
        sharedPreferences.edit().putString("expectedEndStudy", formattedExpectedEndDate).apply()

        if(sevdaytimer){
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
       Log.d("TimerService", "STUDY END: $formattedExpectedEndDate")

    }

    /**
     * Converts a millisecond timestamp into a human-readable date string.
     */
    fun convertMillisToDate(millis: Long): String {
        val date = Date(millis)
        val format = SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())
        return format.format(date)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}


