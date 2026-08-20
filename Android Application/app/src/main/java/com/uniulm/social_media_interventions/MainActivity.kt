package com.uniulm.social_media_interventions

import android.Manifest
import android.app.*
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings.*
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.android.synthetic.main.activity_finalquestionnaire.*
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*


/**
 * Central "study is active" activity, entered from [WelcomeActivity]/
 * [PermissionActivity] and re-entered on every app restart. Shows an
 * ongoing notification appropriate to the current study state (mid-study,
 * study completed, or permissions need re-checking after being killed),
 * makes sure [TimerService] and [AppCheckerService] are running, and
 * restarts [AppCheckerService] again in [onDestroy] if the study isn't over.
 */
class MainActivity : AppCompatActivity() {

    var startstudy = Calendar.getInstance()
    var app_destroyed = false
    val CHANNEL_ID = "thanks"
    var notificationId = 1210

    /**
     * Shows the appropriate ongoing notification for the current state
     * (study still running vs. final questionnaire pending), then makes
     * sure [TimerService] and [AppCheckerService] are started.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("InfiniteScroll", 0)

        var finalquest_started = sharedPref.getBoolean("finalquest_started", false)

        if (!finalquest_started) {
            Log.e("destroy", "appdestroyed and finalquest not there yet in onCreate()")
            Log.e("destroy", "onCreate should be falsee : $finalquest_started")

            if (app_destroyed) {
                setContentView((R.layout.all_permissions))

                val notificationIntent = Intent(applicationContext, PermissionActivity::class.java)
                val sharedPref = getSharedPreferences("InfiniteScroll", 0)
                sharedPref.edit().putBoolean("app_destroyed", app_destroyed).apply()

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0, notificationIntent, 0 or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = Notification.Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle("InfiniteScape")
                    .setContentText("Please click here and check if all permissions are still given")
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(false)

                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }

                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                notificationManager.notify(notificationId, builder.build())
                notificationManager.createNotificationChannel(channel)

            }

            else {
                setContentView((R.layout.activity_main))
                val notificationIntent = Intent(applicationContext, MainActivity::class.java)

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0, notificationIntent, 0 or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = Notification.Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle("InfiniteScape")
                    .setContentText("Thank you for participating in this study. You can quit anytime by deleting the app")
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setAutoCancel(false)
                    .setFullScreenIntent(pendingIntent, true)

                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }

                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, builder.build())
                notificationManager.createNotificationChannel(channel)
            }
        }

        else if (finalquest_started) {
            Log.e("destroy", "appdestroyed and finalquest done in OnCreate ")
            Log.e("destroy", "else if onCreate should be true: $finalquest_started")
            setContentView(R.layout.activity_finalquestionnaire)
            val name = "Study completed"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val descriptionText = "Click to confirm that you have completed the study"
            val questionnaireIntent =
                Intent(applicationContext, FinalQuestionnaire::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                questionnaireIntent,
                0 or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(notificationId, builder.build())
            }

            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)

            finalquest_started = true
            sharedPref.edit().putBoolean("finalquest_started", finalquest_started);

            if (app_destroyed) {

                setContentView(R.layout.activity_finalquestionnaire)
                val name = "Study completed"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val descriptionText = "Click to open the last questionnaire"
                val questionnaireIntent =
                    Intent(applicationContext, FinalQuestionnaire::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    questionnaireIntent,
                    0 or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(notificationId, builder.build())
                }

                val notificationManager =
                    applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                notificationManager.createNotificationChannel(channel)

                //  finalquest_started=true
                // sharedPref.edit().putBoolean("finelquest_started",finalquest_started);

            }
        }

        var intent = Intent(this, AppCheckerService::class.java)

        var started = sharedPref.getBoolean("startstudy", false)

        if (started == false) {
            Log.e("BUGFIX", "timer hasnt started yet: $started")
            val timerServiceIntent = Intent(this, TimerService::class.java)
            startService(timerServiceIntent)
        }

        val startStudyDateFormat = SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())
        val formattedStartStudy = startStudyDateFormat.format(startstudy.time)

        val editor = sharedPref.edit()
        Log.d("TimerService", "STUDY START: $formattedStartStudy")
        editor.putString("startStudy", formattedStartStudy)
        editor.apply()

        // Check if permissions are given
        if (getGrantStatus()) {
            // Check if the service is already running. If not start the foreground service
            if (!isAppCheckerServiceRunning()) {
                //quitButton.setEnabled(false)
                // Start the app checker service
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startService(intent);
                }
            }
        }


    }

    /**
     * check if PACKAGE_USAGE_STATS permission is allowed for this application
     * @return true if permission granted
     */
    private fun getGrantStatus(): Boolean {
        val appOps = applicationContext
            .getSystemService(APP_OPS_SERVICE) as AppOpsManager

        val mode = appOps.checkOpNoThrow(
            OPSTR_GET_USAGE_STATS,
            Process.myUid(), applicationContext.packageName
        )

        return if (mode == AppOpsManager.MODE_DEFAULT) {
            (applicationContext.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED) &&
                    (applicationContext.checkCallingOrSelfPermission((Manifest.permission.SYSTEM_ALERT_WINDOW)) == PackageManager.PERMISSION_GRANTED)

        }

        else {
            mode == MODE_ALLOWED
        }
    }

    /**
     * Checks if the service is already running
     */
    private fun isAppCheckerServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if ("com.uniulm.social_media_interventions.AppCheckerService" == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * Wired via `android:onClick` in the layout. Prompts for confirmation,
     * then clears the study's shared-preferences state and stops
     * [AppCheckerService] — used to reset the app during testing/debugging.
     */
    fun deleteSharedPrefs(view: View) {
        val dialogClickListener =
            DialogInterface.OnClickListener { dialog, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
                        sharedPref.edit().remove("RequestQueue").apply()
                        sharedPref.edit().remove("CODE").apply()
                        sharedPref.edit().remove("Registered").apply()
                        sharedPref.edit().remove("NOTIFICATION_TIMER").apply()
                        sharedPref.edit().remove("REFRESH_TOKEN_TIMER").apply()
                        sharedPref.edit().remove("STUDY_END_TIMER").apply()
                        sharedPref.edit().remove("WhyStoppedOtherAnswers").apply()
                        val editor: SharedPreferences.Editor = sharedPref.edit()
                        editor.putString("QUIT", "true")
                        editor.apply()

                        val intent = Intent(this, AppCheckerService::class.java)
                        stopService(intent)
                        finish()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {

                    }
                }
            }

        val builder: AlertDialog.Builder =
            AlertDialog.Builder(this)

        builder.setMessage("Delete Preferences?").setPositiveButton("Yes", dialogClickListener)
            .setNegativeButton("No", dialogClickListener)
            .show()
    }

    /**
     * If the study is still running, restarts [AppCheckerService] and shows
     * a "check permissions" notification (the accessibility service is
     * killed along with the activity, so it needs to be relaunched). If the
     * study has already finished, shows the "study completed" notification
     * instead.
     */
    override fun onDestroy() {
        super.onDestroy()

        app_destroyed = true
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        sharedPref.edit().putBoolean("app_destroyed", app_destroyed).apply()
        var finalquest = sharedPref.getBoolean("finalquest_started", false)

        if (finalquest) {
            Log.e("destroy", "ondestroy should be true: $finalquest")
            Log.e("destroy", "finalqueststarted in onDestroy()")
            setContentView(R.layout.activity_finalquestionnaire)
            val name = "Study completed"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val descriptionText = "Click to open the last questionnaire"
            val questionnaireIntent =
                Intent(applicationContext, FinalQuestionnaire::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                questionnaireIntent,
                0 or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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
            with(NotificationManagerCompat.from(applicationContext)) {

                notify(notificationId, builder.build())
            }

            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
            finalquest = true
            sharedPref.edit().putBoolean("finalquest_started", finalquest);


        }

        else if (!finalquest) {
            Log.e("destroy", "no finalqueststarted in onDestroy()")
            Log.e("destroy", "ondestroy should be false: $finalquest")
            setContentView(R.layout.all_permissions)
            val notificationIntent = Intent(applicationContext, PermissionActivity::class.java)
            val sharedPref = getSharedPreferences("InfiniteScroll", 0)
            sharedPref.edit().putBoolean("app_destroyed", app_destroyed).apply()
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0, notificationIntent, 0 or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = Notification.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("InfinteScape")
                .setContentText("Please click here and check if all permissions are still given")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(false)

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.notify(notificationId, builder.build())
            notificationManager.createNotificationChannel(channel)

            app_destroyed = true
            sendBroadcast(Intent("YouWillNeverKillMe"))

            Log.e("AppDestroy", "true")
        }

    }

}


