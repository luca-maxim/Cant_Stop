package com.uniulm.social_media_interventions

import android.Manifest
import android.app.*
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Context.APP_OPS_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.provider.Settings.*
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.System
import java.util.*

/**
 * The main "study is running" screen participants land on between sessions.
 * Ensures [AppCheckerService] (the accessibility service that detects and
 * triggers interventions) is running, and makes sure a `STUDY_END_TIMER` is
 * set so the study reliably ends after 7 days even if [TimerService]'s alarm
 * is missed.
 */
class ThankActivity : AppCompatActivity() {

    private val CHANNEL_ID = "channel_id_example_01"

    @RequiresApi(Build.VERSION_CODES.N)

    override fun onCreate(savedInstanceState: Bundle?) {
        createNotificationChannel()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thank)
        var intent = Intent(this, AppCheckerService::class.java)

        // Check if permissions are given
        if (getGrantStatus()) {
            // Check if the service is already running. If not start the foreground service
            if (!isAppCheckerServiceRunning()) {
                // Start the app checker service

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent)
                }
            }
        } else {


        }


        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val isRegistered = sharedPref.getBoolean("Registered", false)

        // Set study end timer should it be missing
        if (sharedPref.getString("STUDY_END_TIMER", "DEFAULT") == "DEFAULT") {
            val editor: SharedPreferences.Editor = sharedPref.edit()
            val date = Date()
            date.time = (date.time + 7L * 24 * 60 * 60 * 1000) // todo change to 7 days
            editor.putString("STUDY_END_TIMER", date.toString())
            editor.apply()
        }

        if (!isRegistered) {
            val editor: SharedPreferences.Editor = sharedPref.edit()
            // Set the end of the study
            val date = Date()
            date.time = (date.time + 7L * 24 * 60 * 60 * 1000)
            editor.putString("STUDY_END_TIMER", date.toString())
            editor.apply()
        }
    }

    override fun onStart() {
        super.onStart()
        var intent = Intent(this, AppCheckerService::class.java)
        // Check if permissions are given
        if (getGrantStatus()) {
            // Check if the service is already running. If not start the foreground service
            if (!isAppCheckerServiceRunning()) {
                // Start the app checker service

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent)
                }
            }
        }



    }

    override fun onPause() {
        super.onPause()
        finish()
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

        } else {
            mode == MODE_ALLOWED
        }
    }

    /**
     * Creates the Channel used for notifications.
     */
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notfication title"
            val descriptionText = "Notfication desc"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
     * Wired via `android:onClick` in `activity_thank.xml`/`activity_main.xml`.
     * Prompts for confirmation, then clears the study's shared-preferences
     * state and stops [AppCheckerService] — used to reset the app during
     * testing/debugging.
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

        val builder: androidx.appcompat.app.AlertDialog.Builder =
            androidx.appcompat.app.AlertDialog.Builder(this)

        builder.setMessage("Delete Preferences?").setPositiveButton("Yes", dialogClickListener)
            .setNegativeButton("No", dialogClickListener)
            .show()
    }

}
