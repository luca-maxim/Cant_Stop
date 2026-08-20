package com.uniulm.social_media_interventions

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.all_permissions.*
import kotlin.math.log


/**
 * Guides the participant through granting all permissions the study needs
 * (battery-optimization exemption, notifications, accessibility service,
 * "draw over other apps") before letting them continue to [StartActivity].
 * Each permission has its own button that opens the relevant system
 * settings screen; [onResume] re-checks all four every time the activity
 * regains focus (e.g. after returning from Settings) and enables the
 * "Start" button once all four are granted.
 */
class PermissionActivity : AppCompatActivity() {
    private lateinit var batteryOptimization: Button
    private lateinit var notification: Button
    private lateinit var accessibility: Button
    private lateinit var appear: Button

    lateinit var startButton: Button

    companion object {

        private const val PERMISSION_BATTERY_OPTIMIZATION_REQUEST_CODE = 101
        private const val PERMISSION_NOTIFICATION_REQUEST_CODE = 102
        private const val PERMISSION_ACCESSIBILITY_REQUEST_CODE = 103
        private const val PERMISSION_APPEAR_REQUEST_CODE = 104
        private const val RESTRICTED_SETTINGS_REQUEST_CODE = 105
        private const val PERMISSIONS_REQUEST_CODE=40
    }

    lateinit var powerManager: PowerManager

    var  appear_clicked=false
    var  access_clicked=false
    var notification_clicked = false
    var checkboxclicked = false

    /**
     * Wires up the four permission-request buttons and the "Start" button
     * (which routes to [StartActivity] for a fresh participant, or shows the
     * "study running" notification and re-enters [MainActivity] if the app
     * was previously force-stopped mid-study).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("SuspiciousIndentation", "MissingInflatedId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.all_permissions)

        powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        startButton = findViewById(R.id.btnStart)
        batteryOptimization = findViewById(R.id.batterybutton)
        notification = findViewById(R.id.notificationbutton)
        appear = findViewById(R.id.ontopbutton)
        accessibility = findViewById(R.id.accessbutton)
        startButton.isEnabled = false



        checkboxclicked = true
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        sharedPref.edit().putBoolean("checkboxclicked", checkboxclicked).apply()

        var app_destroyed = sharedPref.getBoolean("app_destroyed", false)





        appear.setOnClickListener {
            Log.e("Permissions", "i am in appear clicklistener")

            if (isAppearOnTopPermissionGranted()) {

                appear.isEnabled = false
                appear.text = "Appear-On-Top Permission set ✓"
                Log.e("Permissions", "Appearontop granted")
                appear_clicked = true

            } else {
                appear_clicked = false
                appear.isEnabled = true
                appear.text = "Request Apear-on-top Permmsion"
                Log.e("Permissions", "Appearontop not granted")
                openAppearontopsettings()


            }


        }

        notification.setOnClickListener {
            if (isNotificationPermissionGranted_sdk30()) {

                notification.isEnabled = false
                notification.text = "Notification Permission set ✓"
                Log.e("Permissions", "Notification granted_sdk30")


            } else {

                notification.isEnabled = true
                notification.text = "Request Notification Permission"
                Log.e("Permissions", "Notification not granted_sdk30")
                requestNotificationPermission_sdk30()


            }

        }

        accessibility.setOnClickListener {

            Log.e("Permissions", "i am in clicklistener")
            // Directly guide to Accessibility for Android 12 and lower
            // (Restricted settings do not exist before Android 13)
            if (Build.VERSION.SDK_INT < 33) {
                guideBackToAccessibilitySettings()
            }
            // For Android 13 or higher, guide through the steps
            else {
                openAccessibilitySettingsDialog()
            }


        }



        batteryOptimization.setOnClickListener {


            if(isBatteryOptimizationGranted()){

                batteryOptimization.isEnabled=false
                batteryOptimization.text = "Battery Permission set \u2713"
                Log.e("Permissions", "Battery granted")


            }else{

                batteryOptimization.isEnabled=true
                batteryOptimization.text = "Request Battery Permission"
                Log.e("Permissions", "Appearontop not granted")
                requestBatteryOptimizationPermission()
            }

        }





        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if(app_destroyed==false){


                val i = Intent(this, StartActivity::class.java)
                startActivity(i)
                finish()
            } else if(app_destroyed==true){
                setContentView(R.layout.activity_main)
                val notificationIntent = Intent(applicationContext, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0, notificationIntent, 0 or PendingIntent.FLAG_IMMUTABLE
                )
                val builder = Notification.Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle("InfinteScape")
                    .setContentText("Thank you for participating in this study. You can quit anytime by deleting the app")
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
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
    }
    /**
     * Updates each permission button's enabled/text state based on the
     * result of its runtime permission request.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //Check if all permissions were granted
        if (requestCode == PERMISSION_NOTIFICATION_REQUEST_CODE) {

            // Notifications are enabled for your app
            // You can enable the button or perform other actions here

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
                if (notificationsEnabled) {
                    // Notifications are enabled
                    Log.e("Permissions", "Notifications are enabled")
                    notification.isEnabled=false
                    notification.text = "Notification Permission set ✓"
                    notification_clicked=true
                } else {
                    // Notifications are not enabled
                    Log.e("Permissions", "Notifications are not enabled")
                    notification.isEnabled=true
                    notification.text = "Request Notification Permission"
                    notification_clicked=false
                }
            } else {
                // Notification permission denied
                Log.d("Permissions", "Notification permission denied")
            }
        }else if(requestCode == PERMISSION_ACCESSIBILITY_REQUEST_CODE){

            if (grantResults.isNotEmpty() && grantResults[0] !== PackageManager.PERMISSION_GRANTED) {

                Log.e("Permissions", "i am in OnRequestPermissions")
                if(!requestAccessibilityPermission()){
                    Log.e("Permissions", "Access denied")
                    accessibility.isEnabled=true
                    accessibility.text = "Request Accessibility Permission"
                    access_clicked=false

                    //  requestAccessibilityPermission()


                }else if(requestAccessibilityPermission()){
                    Log.e("Permissions", "Access enabled")
                    accessibility.isEnabled=false
                    accessibility.text = "Accessibility Permission set \u2713"
                    access_clicked=true

                }


            } else {

                Log.e("Permissions", "Access permission denied")
                accessibility.isEnabled=true
                accessibility.text = "Request Accessibility Permission"
                access_clicked=false
            }
        }else if(requestCode == PERMISSION_BATTERY_OPTIMIZATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] !== PackageManager.PERMISSION_GRANTED) {
                Log.e("Permissions", "i am in OnRequestPermissions")
                if(!isBatteryOptimizationGranted()){
                    Log.e("Permissions", "Battery denied")
                    batteryOptimization.isEnabled=true
                    batteryOptimization.text = "Request Battery Permission"
                }else if(isBatteryOptimizationGranted()){
                    Log.e("Permissions", "Battery enabled")
                    batteryOptimization.isEnabled=false
                    batteryOptimization.text = "Battery Permission set ✓"
                }
            } else {
                Log.e("Permissions", "Battery permission denied")
                batteryOptimization.isEnabled=true
                batteryOptimization.text = "Request Battery Permission"
            }
        }
    }


    /**
     * Requests [permission] via the standard runtime-permission dialog if
     * it isn't already granted.
     */
    fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this@PermissionActivity, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(this@PermissionActivity, arrayOf(permission), requestCode)
        } else {

            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * Returns whether the app is exempt from battery optimization (required
     * so the accessibility service keeps running in the background).
     */
    private fun isBatteryOptimizationGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(getPackageName())
            val permissionStatus = packageManager.checkPermission(
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                packageName
            )
            return ignoringBatteryOptimizations
        }
        return true
    }

    /**
     * Opens the system dialog to request battery-optimization exemption.
     */
    private fun requestBatteryOptimizationPermission(): Boolean {

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${packageName}")
        startActivityForResult(intent, PERMISSION_BATTERY_OPTIMIZATION_REQUEST_CODE)

        return true
    }

    /**
     * Opens the app's notification settings screen (used on SDK 30, where
     * the runtime POST_NOTIFICATIONS permission dialog isn't available yet).
     */
    private fun requestNotificationPermission_sdk30(): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivityForResult(intent, PERMISSION_NOTIFICATION_REQUEST_CODE)
        return true

    }

    /**
     * Returns whether [AppCheckerService] is currently listed among the
     * device's enabled accessibility services.
     */
    private fun requestAccessibilityPermission() : Boolean{

        Log.e("Permissions", "i am in requestpermission")

        val enabledServices =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val packageName = packageName
        return enabledServices?.contains(packageName) == true

    }

    /**
     * STEP 1: Shows an explanatory dialog, then opens Accessibility Settings.
     */
    private fun openAccessibilitySettingsDialog() {

        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility")
            .setMessage(
                "1. Go to 'Installed Apps'.\n" +
                        "2. Tap 'InfiniteScape' (even if greyed out).\n" +
                        "3. If a 'Restricted Setting' warning appears, press OK.\n\n" +
                        "Return to this screen to finalize the setup."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                openAccessibilitySettings()
            }.show()

    }

    /**
     * STEP 2: Shows an explanatory dialog, then opens the app's system
     * settings screen so the user can allow restricted settings.
     */
    private fun openRestrictedSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Restricted Settings")
            .setMessage(
                "1. Go to 'Apps' > 'InfiniteScape'.\n" +
                        "2. Tap the three dots in the top-right corner.\n" +
                        "3. Select 'Allow Restricted Settings'.\n\n" +
                        "Return to this screen to finalize the setup."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                openSettings()
            }
            .show()
    }

    /**
     * STEP 3: Shows a dialog guiding the user back to Accessibility Settings
     * to finally enable the service.
     */
    private fun guideBackToAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility")
            .setMessage(
                "1. Go to 'Installed Apps'.\n" +
                        "2. Tap 'InfiniteScape' and enable it."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                openAccessibilitySettings2()
            }
            .show()
    }


    /**
     * Placeholder for detecting whether the user tapped "InfiniteScape" in
     * the Accessibility Settings list; always returns true.
     */
    private fun didInteractWithAppInAccessibilitySettings(): Boolean {
        // Placeholder for checking user interaction
        return true
    }

    /**
     * Placeholder for detecting whether the user interacted with the
     * restricted-settings dialog; always returns true.
     */
    private fun didInteractWithRestrictedSettings(): Boolean {
        // Placeholder for checking user interaction
        return true
    }

    /**
     * Returns whether Android's "restricted settings" access-op is granted
     * for this app, via the hidden `access_restricted_settings` app-op.
     */
    private fun hasAccessRestrictedPerm(context: Context): Boolean {
        return try {
            Log.d("PermissionCheck", "Checking Restricted Settings Permission")
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.unsafeCheckOpNoThrow(
                "android:access_restricted_settings",
                applicationInfo.uid,
                applicationInfo.packageName
            )
            Log.d("PermissionCheck", "App UID: ${applicationInfo.uid}")
            Log.d("PermissionCheck", "Package Name: ${applicationInfo.packageName}")
            Log.d("PermissionCheck", "Restricted Settings Check Mode: $mode")
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e("PermissionCheck", "Error checking restricted settings", e)
            false
        }
    }


    /**
     * Chains the accessibility-settings walkthrough: after returning from
     * Accessibility Settings, prompts for restricted settings; after
     * returning from restricted settings, guides the user back to finish
     * enabling the accessibility service.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PERMISSION_ACCESSIBILITY_REQUEST_CODE -> {
                if (didInteractWithAppInAccessibilitySettings()) { // methode die true zurück gibt zum testen
                    openRestrictedSettingsDialog()
                } else {
                    Toast.makeText(
                        this,
                        "Please click 'InfiniteScape' in Installed Apps.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            RESTRICTED_SETTINGS_REQUEST_CODE -> {
                if (hasAccessRestrictedPerm(this) || didInteractWithRestrictedSettings()) { // methode die true zurück gibt zum testen
                    guideBackToAccessibilitySettings()
                } else {
                    Toast.makeText(
                        this,
                        "Please enable Restricted Settings for InfiniteScape.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Opens the device's general Settings app.
     */
    private fun openSettings():Boolean {
        val intent = Intent(Settings.ACTION_SETTINGS)
        startActivityForResult(intent, RESTRICTED_SETTINGS_REQUEST_CODE)
        return true
    }

    /**
     * Opens the Accessibility Settings screen.
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivityForResult(intent, PERMISSION_ACCESSIBILITY_REQUEST_CODE)
    }

    /**
     * Opens the Accessibility Settings screen and also requests the
     * `BIND_ACCESSIBILITY_SERVICE` permission directly.
     */
    private fun openAccessibilitySettings2(){
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)

        val requestedPermissions = arrayOf(
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE
        )

        requestPermissions(requestedPermissions, PERMISSION_ACCESSIBILITY_REQUEST_CODE)
    }


    /**
     * Opens the system "draw over other apps" permission screen.
     */
    private fun openAppearontopsettings() : Boolean {

        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        startActivity(intent)
        return true

    }

    /**
     * Returns whether the "draw over other apps" (SYSTEM_ALERT_WINDOW)
     * permission is granted.
     */
    private fun isAppearOnTopPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this)
        }
        return true
    }


    /**
     * Returns whether the POST_NOTIFICATIONS runtime permission is granted.
     */
    private fun isNotificationPermissionGranted(): Boolean {
        val notification = PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )
        return notification

    }
    /**
     * Returns whether notifications are enabled system-wide for the app
     * (used on SDK 30, where POST_NOTIFICATIONS isn't a runtime permission
     * yet), and updates the notification button's enabled state/label to
     * match.
     */
    private fun isNotificationPermissionGranted_sdk30(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (notificationsEnabled) {
            // Notifications are enabled
            Log.e("Permissions", "Notifications are enabled")
            notification.isEnabled=false
            notification.text = "Notification Permission set ✓"
            notification_clicked=true
        } else {
            // Notifications are not enabled
            Log.e("Permissions", "Notifications are not enabled")
            notification.isEnabled=true
            notification.text = "Request Notification Permission"
            notification_clicked=false
        }

        return notificationsEnabled
    }

    /**
     * Re-checks all four required permissions every time the activity
     * regains focus (e.g. after returning from Settings), updating each
     * button's state/label and enabling the "Start" button once all four
     * are granted.
     */
    override fun onResume() {
        super.onResume()
        if(isAppearOnTopPermissionGranted()){
            appear.isEnabled=false
            appear.text = "Appear-On-Top Permission set ✓"
        }
        if(isBatteryOptimizationGranted()){
            batteryOptimization.isEnabled=false
            batteryOptimization.text = "Battery Permission set ✓"
        }
        if(isNotificationPermissionGranted()){
            notification.isEnabled=false
            notification.text = "Notification Permission set ✓"
        }
        if(isNotificationPermissionGranted_sdk30()){
            notification.isEnabled=false
            notification.text = "Notification Permission set ✓"
        }
        if(requestAccessibilityPermission()){
            accessibility.isEnabled=false
            accessibility.text = "Accessibility Permission set ✓"
        }

        if(isAppearOnTopPermissionGranted()&&isBatteryOptimizationGranted()&& requestAccessibilityPermission() &&  isNotificationPermissionGranted_sdk30()){
            Log.e("Permissions", "Button activating")

            startButton.isEnabled=true
        }
        Log.e("Permissions", isAppearOnTopPermissionGranted().toString() + requestAccessibilityPermission() + isBatteryOptimizationGranted()+isNotificationPermissionGranted_sdk30())
    }
}