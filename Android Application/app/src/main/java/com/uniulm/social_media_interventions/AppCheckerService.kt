package com.uniulm.social_media_interventions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.ContentValues.TAG
import android.graphics.PixelFormat
import android.os.*
import android.os.Build.VERSION_CODES
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.FirebaseFirestore
import com.rvalerio.fgchecker.AppChecker
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random


/**
 * The core Accessibility Service: watches which app is in the foreground,
 * detects when it's one of the tracked social-media apps
 * ([packageIsRelevantApp]), and once the participant has been scrolling
 * there for [scrollingTimer] seconds, randomly fires one of three
 * interventions ([startOverlay], [startVibration],
 * [startSpotOverlayService]). When the relevant app is left, opens the
 * post-session questionnaire ([startQuestionnaire]).
 *
 * Runs as a foreground/accessibility service for the whole study duration;
 * [onDestroy] restarts itself unless explicitly told to quit via the
 * `"QUIT"` shared-preference flag (set by [ThankActivity.deleteSharedPrefs]).
 */
public class AppCheckerService : AccessibilityService() {
    private val CHANNEL_ID = "id_smi01"

    companion object {
        var shouldStopOldNotification = false
    }

    var screenOn = true;
    var isRelevantApp = false;
    var currentRelevantApp = ""
    var iteration: Int = 0;

    val cooldown = 600
    val maxdur = cooldown + 120

    var questionnaireDisplayed = false;
    var intervention = 42


    private var floatingView: View? = null
    private var timerTextView: TextView? = null

    lateinit var mainHandler: Handler

    var d_timer_value = 0
    var dtimerHandler: Handler? = null
    private var d_timer: Runnable? = null
    var startDelayTime: Long = 0
    var delayTimeInSeconds = 0L
    var pID = ""
    var package_name = ""
    var startdelaytime_started = false


    //    VARs Luca
    var infinte = true
    var timerStarted = false
    var isRelevantAppOpen = false
    var previousValue: String? = "NO IS"
    var timeStampScrollTimerStarted = LocalDateTime.now()
    var scrollingTimer = 900L // 15 minutes in seconds, matches the study
    var OverlayShowed = false
    val packageNameQueue: ArrayDeque<String> = ArrayDeque(10)
    var lastForegroundApp: String? = null
    var OverlayClicked = false
    var AppNameToBeShownInOverlay = ""

    //    VARs Luca
    var lastRelevantApp: String? = null
    var isOverlayBeingDismissed = false

    var isRelevantcontentOpen = false

    // This represents the task which will run every 5 sec to check the current app
    private val getAppTask = object : Runnable {
        @RequiresApi(VERSION_CODES.O)
        override fun run() {
            // Send a ping to the server, that the app is still working/user has not closed it
            var delayMillis = 1000;
            if (checkSendAliveTimer()) {
                sendAliveTag()
            }
            Log.e("runfunction", "run")
            getActiveApp(delayMillis)
            mainHandler.postDelayed(this, delayMillis.toLong())
        }
    }

    lateinit var screenReceiver: BroadcastReceiver
    val screenFilter = IntentFilter()


    // VARS MANU
    var interventionType: String = ""

    // Spot Overlay
    lateinit var spotServiceIntent: Intent
    var SpotOverlayShowed = false

    // Vibration
    lateinit var vibrator: Vibrator
    var VibrationStarted = false
    lateinit var vibrationHandler: Handler
    lateinit var vibrationRunnable: Runnable


    /**
     * Registers a receiver that starts [startChecker] when the screen is
     * unlocked, and tears down any running intervention/timer when the
     * screen turns off.
     */
    private fun registerBroadcastReceivers() {
        // Register a broadcast receiver to listen for screen off/on events to stop and start the timer
        // as it does not need to listen when the screen is off. Also stops a running session if the screen
        // is locked
        screenReceiver = object : BroadcastReceiver() {
            @RequiresApi(VERSION_CODES.O)
            override fun onReceive(context: Context?, intent: Intent) {
                when (Objects.requireNonNull(intent.action)) {
                    Intent.ACTION_USER_PRESENT -> {
                        Log.e("SCREEN", Intent.ACTION_SCREEN_ON)
                        screenOn = true;
                        startChecker()
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        Log.e("SCREEN", Intent.ACTION_SCREEN_OFF)
                        if (OverlayShowed) {
                            OverlayShowed = false
                        }

                        if (SpotOverlayShowed) {
                            SpotOverlayShowed = false
                        }

                        if (VibrationStarted) {
                            VibrationStarted = false
                        }

                        screenOn = false
                        isRelevantAppOpen = false

                        stopOverlay()
                        stopSpotOverlayService()
                        stopVibration()
                        stopDelayTimer()

                        mainHandler.removeCallbacks(getAppTask)
                    }


                }
            }
        }
        // User present instead of screen on because this way the timer is only started once the phone is unlocked
        screenFilter.addAction(Intent.ACTION_USER_PRESENT)
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, screenFilter)
    }

    @RequiresApi(VERSION_CODES.O)


            /**
             * Starts the loop which checks the foreground app
             */
    fun startChecker() {
        // todo change timer to observable
        mainHandler.post(getAppTask)
    }

    /**
     * Called when the system tries to destroy this service. Restarts the
     * service via a fresh [startForegroundService] call unless the
     * `"QUIT"` shared-preference flag was explicitly set (e.g. by
     * [ThankActivity.deleteSharedPrefs]), in which case it's allowed to stop.
     */
    @RequiresApi(VERSION_CODES.O)
    override fun onDestroy() {
        // As we don't want the service to be destroyed, check the source which wants to destroy the
        // service and if its not from us restart the service again
        stopVibration()
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val quitService = sharedPref.getString("QUIT", "")
        if (quitService == "true") {
            Log.e("DESTROY", "true " + quitService.toString())
            val editor: SharedPreferences.Editor = sharedPref.edit()
            editor.putString("QUIT", "false")
            editor.apply()
            mainHandler.removeCallbacks(getAppTask)
            stopForeground(true);
            unregisterReceiver(screenReceiver)
            sendBroadcast(Intent("YouWillNeverKillMe"))
            stopSelf();
            super.onDestroy()

        } else {
            Log.e("DESTROY", "other " + quitService.toString())
            stopVibration()
            val intent = Intent(this, AppCheckerService::class.java)
            startForegroundService(intent);
            mainHandler.removeCallbacks(getAppTask)
            stopForeground(true);
            unregisterReceiver(screenReceiver)
            stopSelf();
            super.onDestroy()
        }
    }

    /**
     * Gets the active app and handles it.
     */
    @RequiresApi(VERSION_CODES.O)
    fun getActiveApp(delayMillis: Int) {
        val appChecker = AppChecker()
        val timeRunning: Long
        if (appChecker.getForegroundApp(this) == null) {
            return
        }
        val packageName: String = appChecker.getForegroundApp(this)
        var app_name = getAppName(packageName)

        Log.e("appname", "hallo")
        // Current foreground app is important
        if (packageIsRelevantApp(packageName)) {


            // Not yet a opened app active -> Create app JSON
            if (!isRelevantApp) {
                startAppJSON(packageName);
                iteration = 1
            } else {
                val sharedPref = getSharedPreferences("InfiniteScroll", 0)
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putString("App_Name", app_name)
                editor.apply()

            }
            when (getAppName(packageName)) {
                "Youtube" -> {
                    if (currentRelevantApp == "com.google.android.youtube") {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++

                        Log.d("SAME", "YouTube Time Running: $timeRunning Seconds")
                    } else {
                        startQuestionnaire()
                    }
                }

                "com.facebook.android" -> {
                    if (currentRelevantApp == "com.facebook.android" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Facebook Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }

                "com.facebook.katana" -> {
                    if (currentRelevantApp == "com.facebook.katana" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }


                        Log.d("SAME", "Facebook Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {
                        Log.d("Double", "Same app again")
                    }
                }

                "com.reddit.frontpage" -> {
                    if (currentRelevantApp == "com.reddit.frontpage" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Reddit Time Running: $timeRunning Seconds\")")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }

                "free.reddit.news" -> {
                    if (currentRelevantApp == "free.reddit.news" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Reddit Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }

                //
                "com.rubenmayayo.reddit" -> {
                    if (currentRelevantApp == "com.rubenmayayo.reddit" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Reddit Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }

                "com.andrewshu.android.reddit" -> {
                    if (currentRelevantApp == "com.andrewshu.android.reddit" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Reddit Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }

                "com.instagram.android" -> {
                    if (currentRelevantApp == "com.instagram.android" && iteration < maxdur) {
                        // Same app all good

                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Instagram Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {
                        Log.d("Double", "Same app again")
                    }
                }

                "com.zhiliaoapp.musically" -> {
                    if (currentRelevantApp == "com.zhiliaoapp.musically" && iteration < maxdur) {
                        // Same app all good

                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "TikTok Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }

                "com.twitter.android" -> {
                    if (currentRelevantApp == "com.twitter.android" && iteration < maxdur) {
                        // Same app all good
                        timeRunning = refreshTimeRunning(delayMillis, iteration)
                        iteration++
                        if (iteration >= cooldown && iteration % 5 == 0) {

                        }
                        Log.d("SAME", "Twitter Time Running: $timeRunning Seconds")
                    } else if (iteration >= cooldown) {
                        startQuestionnaire()
                    } else {

                    }
                }
            }
        } else {

            Log.d(
                "App",
                Date().hours.toString() + ":" + Date().minutes.toString() + ":" + Date().seconds.toString() + ": " + packageName
            )


            // Relevant app was open before
            if (isRelevantApp) {

                if (iteration >= cooldown) {
                    startQuestionnaire()
                } else {
                    reset()
                }
            }
            iteration = 1
        }
    }


    /**
     * Marks the given package as the currently tracked relevant app.
     */
    fun startAppJSON(packageName: String) {
        val appName = getAppName(packageName)

        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.putString("App_Name", appName)
        editor.apply()

        isRelevantApp = true;
        currentRelevantApp = packageName;
    }


    /**
     * Computes the elapsed running time from [iteration]/[delayMillis] and
     * records the current timestamp as `"t2"` in shared preferences.
     */
    fun refreshTimeRunning(delayMillis: Int, iteration: Int): Long {
        var timeRunning: Long = (iteration * delayMillis / 1000).toLong()
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        //T2 & Delta
        var current2 = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("dd-MM HH:mm:ss")
        var formatted = current2.format(formatter)
        editor.putString("t2", formatted)
        editor.apply()
        return timeRunning
    }


    /**
     * Clears the currently tracked relevant-app state without opening the
     * questionnaire (used when the user switches away before the cooldown
     * has elapsed).
     */
    fun reset() {
        intervention = 42
        isRelevantApp = false;
        currentRelevantApp = "";
    }

    /**
     * Stops the current session and opens the questionnaire.
     * Also resets app variables.
     */
    @RequiresApi(VERSION_CODES.O)
    fun startQuestionnaire() {
        Log.d("TAG", "Different app -> Stopped scrolling")

        intervention = 42
        isRelevantApp = false;
        currentRelevantApp = "";

        // openQuestionaire
        val intent = Intent(this, rhsci1_activity::class.java)
        intent.putExtra("delayTimeinSeconds", delayTimeInSeconds)
        intent.putExtra(
            "sideActivityText",
            "Did you do anything else besides being on $AppNameToBeShownInOverlay?"
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.e("ACCESSSERVICE", delayTimeInSeconds.toString())
        Log.e("ifloop", "startActivity")
        startActivity(intent)
        questionnaireDisplayed = true

        stopOverlay()
        stopSpotOverlayService()
        stopVibration()
        stopDelayTimer()
    }

    /**
     * Returns a simplified name using the package name.
     */
    fun getAppName(packageName: String): String {
        if (packageName == "com.instagram.android") {
            return "Instagram"
        } else if (packageName == "com.reddit.frontpage") {
            return "Reddit"
        } else if (packageName == "free.reddit.news") {
            return "Reddit"
        } else if (packageName == "com.andrewshu.android.reddit") {
            return "Reddit"
        } else if (packageName == "com.rubenmayayo.reddit") {
            return "Reddit"
        } else if (packageName == "com.reddit.frontpage") {
            return "Reddit"
        } else if (packageName == "com.facebook.android") {
            return "Facebook"
        } else if (packageName == "com.facebook.katana") {
            return "Facebook"
        } else if (packageName == "com.ninegag.android.app") {
            return "9gag"
        } else if (packageName == "com.zhiliaoapp.musically") {
            return "TikTok"
        } else if (packageName == "com.pinterest") {
            return "pinterest"
        } else if (packageName == "com.twitter.android") {
            return "Twitter"
        } else if (packageName == "com.google.android.youtube") {
            return "YouTube"
        } else if (packageName == "com.uniulm.social_media_interventions") {
            return "InfiniteScape"
        } else {
            return ""
        }
    }

    /**
     * Returns a boolean whether the current app is a relevant app.
     */
    fun packageIsRelevantApp(packageName: String): Boolean {
        return when (packageName) {
            "com.facebook.android" -> {
                true
            }

            "com.google.android.youtube" -> {
                true
            }

            "com.facebook.katana" -> {
                true
            }

            "com.instagram.android" -> {
                true
            }

            "com.reddit.frontpage" -> {
                true
            }

            "free.reddit.news" -> {
                true
            }

            "com.andrewshu.android.reddit" -> {
                true
            }

            "com.rubenmayayo.reddit" -> {
                true
            }

            "com.zhiliaoapp.musically" -> {
                true
            }
            "com.google.android.youtube" -> {
                true
            }

            "com.twitter.android" -> {
                true
            }

            else -> {
                false
            }
        }
    }


    /**
     * Sets the send alive timer every 24 hours.
     */
    @RequiresApi(VERSION_CODES.O)
    fun setSendAliveTimer() {
        val sharedPref: SharedPreferences = this.getSharedPreferences("InfiniteScroll", 0)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        val date = Date()
        date.time = (date.time + 1 * 60 * 60 * 1000)
        editor.putString("SEND_ALIVE_TIMER", date.toString())
        editor.apply()
    }


    /**
     * Checks the current status of the send alive timer. If over send ping to server
     */
    @RequiresApi(VERSION_CODES.O)
    fun checkSendAliveTimer(): Boolean {
        val sharedPref: SharedPreferences = this.getSharedPreferences("InfiniteScroll", 0)
        val sendAliveTimer = sharedPref.getString("SEND_ALIVE_TIMER", "true")
        if (sendAliveTimer == null || sendAliveTimer == "true") {
            return true
        }
        val now = Date()
        val date = Date(sendAliveTimer)
        return date < now
    }


    /**
     * Re-arms the send-alive timer; called once [checkSendAliveTimer]
     * reports it has expired.
     */
    @RequiresApi(VERSION_CODES.O)
    fun sendAliveTag() {
        setSendAliveTimer()
    }

    /**
     * Creates the Channel used for notifications.
     */
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            val name = "Social Media Interventions"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.enableVibration(false) // Disable vibration for this notification channel
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Returns whether "draw over other apps" is currently granted, used to
     * check the permission overlay is still running.
     */
    fun Context.drawOverOtherAppsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT < VERSION_CODES.M) {
            true
        } else {
            Settings.canDrawOverlays(this)
        }
    }


    /**
     * Classifies an Instagram accessibility-event content description as
     * `"IS"` (infinite-scroll feed content), `"NO IS"` (a non-feed screen
     * like search or messages), or `""` (unrecognized).
     */
    fun isRelevantInstagramContent(content: String): String {

        if (content.contains("Home")) {
            return "IS"
        }
        if (content.contains("Reel")) {
            return "IS"
        }
        if (content.contains("@")) {
            return "IS"
        }

        // wenn Videos auf der Explore Page starten startets trotzdem
        if (content.contains("Search")) {
            return "NO IS"
        }
        if (content.contains("Camera")) {
            return "NO IS"
        }
        if (content.contains("message")) {
            return "NO IS"
        }
        if (content.contains("Notifications")) {
            return "NO IS"
        }
        if (content.contains("Create")) {
            return "NO IS"
        }
        if (content.contains("POST")) {
            return "NO IS"
        }
        return ""
    }

    /**
     * Classifies a YouTube accessibility-event content description as
     * `"IS"` (Shorts, an infinite-scroll feed), `"NO IS"` (a non-feed
     * screen), or `""` (unrecognized). See [isRelevantInstagramContent].
     */
    fun isRelevantYoutubeContent(content: String): String {

        if (content.contains("Shorts")) {
            return "IS"
        }

        // Search klappt nicht
        if (content.contains("Search")) {
            return "NO IS"
        }
        if (content.contains("Home")) {
            return "NO IS"
        }
        if (content.contains("Create")) {
            return "NO IS"
        }
        if (content.contains("Subscriptions")) {
            return "NO IS"
        }
        if (content.contains("You")) {
            return "NO IS"
        }
        // ?
        if (content.contains("Library")) {
            return "NO IS"
        }
        return ""
    }


    /**
     * Classifies a TikTok accessibility-event content description as
     * `"IS"` (an infinite-scroll feed tab), `"NO IS"` (a non-feed screen),
     * or `""` (unrecognized). See [isRelevantInstagramContent].
     */
    fun isRelevantTiktokContent(content: String): String {

        if (content.contains("Home")) {
            return "IS"
        }
        if (content.contains("For You")) {
            return "IS"
        }
        if (content.contains("Following")) {
            return "IS"
        }
        if (content.contains("Discover")) {
            return "IS"
        }
        if (content.contains("Friends")) {
            return "IS"
        }

        // Search klappt nicht
        if (content.contains("Search")) {
            return "NO IS"
        }
        if (content.contains("Explore")) {
            return "NO IS"
        }
        if (content.contains("Create")) {
            return "NO IS"
        }
        if (content.contains("Inbox")) {
            return "NO IS"
        }
        if (content.contains("Profile")) {
            return "NO IS"
        }
        return ""
    }


    /**
     * Classifies a Facebook accessibility-event content description as
     * `"IS"` (video/reel feed content), `"NO IS"` (a non-feed screen), or
     * `""` (unrecognized). See [isRelevantInstagramContent].
     */
    fun isRelevantFacebookContent(content: String): String {

        if (content.contains("Video")) {
            return "IS"
        }
        if (content.contains("reel")) {
            return "IS"
        }
        if (content.contains("Reel")) {
            return "IS"
        }

        // IS wird getriggert wenn Videos auf Home starten
        // home rein oder Reel raus?
        if (content.contains("Home")) {
            return "NO IS"
        }
        if (content.contains("Friends")) {
            return "NO IS"
        }
        if (content.contains("Notifications")) {
            return "NO IS"
        }
        if (content.contains("Menu")) {
            return "NO IS"
        }
        if (content.contains("Marketplace")) {
            return "NO IS"
        }
        if (content.contains("Chats")) {
            return "NO IS"
        }
        return ""
    }


    /**
     * Configures which accessibility events this service receives (clicks,
     * scrolls, window/content changes) and registers the screen on/off
     * broadcast receivers once the system connects the service.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e(TAG, "Try to connect")
        val info = AccessibilityServiceInfo()
        info.apply {
            // Set the type of events that this service wants to listen to. Others
            // won't be passed to this service.
            eventTypes =
                AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC



            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;

            registerBroadcastReceivers()

            if (shouldStopOldNotification) {
                shouldStopOldNotification = false
            }
        }

        this.setServiceInfo(info)
        Log.e(TAG, "Service connected")

    }

    /**
     * Returns the package name that occurs most often in the recent-events
     * queue [packageNameQueue], used to smooth over noisy/transient
     * foreground-app readings.
     */
    fun findMostFrequentPackageName(packageNames: ArrayDeque<String>): String? {
        if (packageNames.isEmpty()) return null

        val packageNameCount = mutableMapOf<String, Int>()

        // Count occurrences of each package name
        for (packageName in packageNames) {
            packageNameCount[packageName] = packageNameCount.getOrDefault(packageName, 0) + 1
        }

        // Find the package name with the highest count
        var mostFrequentPackageName: String? = null
        var maxCount = 0
        for ((name, count) in packageNameCount) {
            if (count > maxCount) {
                maxCount = count
                mostFrequentPackageName = name
            }
        }

        return mostFrequentPackageName
    }

    /**
     * The central event handler, fired on every relevant accessibility
     * event (clicks, scrolls, window/content changes) across the whole
     * device. Per event, it:
     * 1. Tracks the foreground package via [addPackageNameToArray] /
     *    [findMostFrequentPackageName], and detects when a tracked app was
     *    closed (event type 32) to tear down any active overlay/vibration
     *    and stop the scroll/delay timers.
     * 2. If [scrollingTimer] has elapsed while a tracked app is open,
     *    randomly triggers one of [startOverlay], [startVibration], or
     *    [startSpotOverlayService].
     * 3. Runs a per-app `when` block (YouTube, Facebook, Instagram, TikTok,
     *    Reddit, Twitter) that starts the scroll/delay timers on first
     *    entry and uses the app-specific `isRelevant*Content` classifier to
     *    detect transitions into/out of infinite-scroll content.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        var content = event?.contentDescription.toString()
        var packageName = event?.packageName.toString()
        var eventType = event?.eventType
        val className = event?.className.toString()
        var getText = event?.text.toString()
        addPackageNameToArray(packageName)
        var currentAppClosed = getAppName(findMostFrequentPackageName(packageNameQueue).toString())

        mainHandler = Handler(Looper.getMainLooper())
        var eventTypes =
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        Log.e("detect Close", eventTypes.toString())
        Log.e("PackageName", packageName)
        Log.e("content", content)
        Log.e("eventType", eventType.toString())
        Log.e("booleans", "")
        Log.e("booleans", "Infinite: $infinte")
        Log.e("booleans", "timerStarted: $timerStarted, $packageName")
        Log.e("booleans", "delaytimeStarted: $startdelaytime_started")
        Log.e("booleans", "isRelevantAppOpen: $isRelevantAppOpen")
        Log.e("booleans", "OverlayShowed: $OverlayShowed")
        Log.e("booleans", "SpotOverlayShowed: $SpotOverlayShowed")
        Log.e("booleans", "VibrationStarted: $VibrationStarted")
        Log.e("booleans", "floatingView: $floatingView")
        Log.e("booleans", "lastForegroundApp: $lastForegroundApp")
        Log.e("booleans", "lastRelevantApp: $lastRelevantApp")
        Log.e("booleans", "packageName: " + getAppName(packageName).toString())
        Log.e("booleans", "currentAppname:$currentAppClosed")
        Log.e("booleans", "className:$className")
        Log.e("booleans", "eventType:$eventType")
        Log.e("booleans", "getText:$getText")
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val ignoringBatteryOptimizations =
            powerManager.isIgnoringBatteryOptimizations(getPackageName())
        Log.e("booleans", ignoringBatteryOptimizations.toString())

        if (lastRelevantApp != packageName) {
            lastRelevantApp = packageName
        }

        if (event != null) {
            val currentTime = System.currentTimeMillis()
            val currentPackageName = packageName

            // Check if an App was closed and debounce time has passed
            if (event.eventType == 32 && packageName != "com.uniulm.social_media_interventions" && packageName != "com.android.systemui" && packageName != "com.samsung.android.app.cocktailbarservice") {
                Log.e("notificationProblem", "packageName: $packageName eventType : $eventType")
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed({

                    Log.e("ifloop", "in 32 if")

                    if (getAppName(packageName) == "") {
                        Log.e("ifloop", "in 1 if, $OverlayShowed, $OverlayClicked")

                        /*--- Overlay ---*/
                        if (OverlayShowed && !OverlayClicked) {
                            Log.e("ifloop", "in 2 if")
                            isRelevantAppOpen = false
                            Log.e("Relevant App closed", "start Questionnaire")
                            startQuestionnaire()
                            OverlayShowed = false
                        }
                        if (!isOverlayBeingDismissed) {  // Add this condition
                            Log.e("ifloop", "in 3 if")

                            if (OverlayShowed && lastRelevantApp == packageName && !OverlayClicked) {
                                Log.e("ifloop", "in 4 if")
                                Log.e("Relevant App closed", "start Questionnaire")
                                startQuestionnaire()
                                OverlayShowed = false
                            }

                            // Reset the overlayDismissed flag only if the app being closed is the relevant app
                            if (lastRelevantApp == packageName) {
                                Log.e("ifloop", "in 5 if")
                                OverlayClicked = false
                            }
                        }

                        /*--- SpotOverlay ---*/
                        if (SpotOverlayShowed) {
                            Log.e("ifloop", "in 6 if")
                            isRelevantAppOpen = false
                            Log.e("Relevant App closed", "start Questionnaire")
                            startQuestionnaire()
                            SpotOverlayShowed = false
                        }

                        /*--- Vibration ---*/
                        if (VibrationStarted) {
                            Log.e("ifloop", "in 7 if")
                            isRelevantAppOpen = false
                            Log.e("Relevant App closed", "start Questionnaire")
                            startQuestionnaire()
                            VibrationStarted = false
                        }

                        timerStarted = false
                        isRelevantAppOpen = false
                        stopDelayTimer()
                    }
                }, 200)
            }
        }


        // Check if the scrollingTimer is expired
        if (timerStarted && isRelevantAppOpen && infinte) {
            Log.e("checkIS", "yes")
            // new random interventions added
            if (isScrollTimerExpired(timeStampScrollTimerStarted) && !OverlayShowed && !SpotOverlayShowed && !VibrationStarted) {

                val choice = Random.nextInt(3)
                when (choice) {
                    0 -> {
                        startOverlay()
                    }

                    1 -> {
                        startVibration()
                    }

                    2 -> {
                        startSpotOverlayService()
                    }
                }
            }
        }


        when (getAppName(packageName)) {

            "YouTube" -> {

                try {
                    if (!isRelevantAppOpen) {
                        isRelevantAppOpen = true
                    }
                    if (!timerStarted && infinte) {
                        Log.e("10minTimer", "Start")
                        timerStarted = true
                        startScrollTimer()
                    }
                    if (!startdelaytime_started && infinte) {
                        Log.e("10minTimer", "Start")
                        startDelayTimer()
                    }

                    if (isRelevantYoutubeContent(content) != "" && isRelevantYoutubeContent(content) != previousValue) {
                        if ((isRelevantYoutubeContent(content) == "IS" && previousValue == "NO IS") || (isRelevantYoutubeContent(content) == "NO IS" && previousValue == "IS")) {
                            if (previousValue == "NO IS") {
                                infinte = true
                            }
                            else if (previousValue == "IS") {
                                infinte = false
                                timerStarted = false
                                if (OverlayShowed || OverlayClicked || SpotOverlayShowed || VibrationStarted) {
                                    stopOverlay()
                                    stopSpotOverlayService()
                                    stopVibration()
                                    stopDelayTimer()
                                    startQuestionnaire()
                                }
                                else {
                                    stopDelayTimer()
                                }
                            }
                            if (infinte) {
                                if (!timerStarted && !isRelevantcontentOpen) {
                                    Log.e("10minTimer", "Start, $packageName")
                                    isRelevantcontentOpen = true;
                                    startScrollTimer()
                                    timerStarted = true
                                }
                                if (!startdelaytime_started) {
                                    startDelayTimer()
                                }
                            }
                            else {
                                if (timerStarted) {
                                    Log.e("10minTimer", "Stop")
                                    isRelevantcontentOpen = false
                                    timerStarted = false
                                }
                                if (startdelaytime_started) {
                                    stopDelayTimer()
                                }
                            }
                        }
                        previousValue = isRelevantYoutubeContent(content)
                    }
                } catch (e: Exception) {
                    Log.e("CONTENTEXCEPTION", "An exception occurred: ${e.message}")
                }
            }

            "Facebook" -> {

                try {
                    if (!isRelevantAppOpen) {
                        isRelevantAppOpen = true
                    }
                    if (!timerStarted && infinte) {
                        Log.e("10minTimer", "Start")
                        startScrollTimer()
                        timerStarted = true
                    }
                    if (!startdelaytime_started && infinte) {
                        Log.e("10minTimer", "Start")
                        startDelayTimer()
                    }

                    if (isRelevantFacebookContent(content) != "" && isRelevantFacebookContent(content) != previousValue) {
                        if ((isRelevantFacebookContent(content) == "IS" && previousValue == "NO IS") || (isRelevantFacebookContent(content) == "NO IS" && previousValue == "IS")) {
                            if (previousValue == "NO IS") {
                                infinte = true
                            }
                            else if (previousValue == "IS") {
                                infinte = false
                                timerStarted = false
                                if (OverlayShowed || OverlayClicked || SpotOverlayShowed || VibrationStarted) {
                                    stopOverlay()
                                    stopSpotOverlayService()
                                    stopVibration()
                                    stopDelayTimer()
                                    startQuestionnaire()
                                }
                                else {
                                    stopDelayTimer()
                                }
                            }
                            if (infinte) {
                                if (!timerStarted && !isRelevantcontentOpen) {
                                    isRelevantcontentOpen = true
                                    Log.e("10minTimer", "Start")
                                    startScrollTimer()
                                    timerStarted = true
                                }
                                if (!startdelaytime_started) {
                                    startDelayTimer()
                                }
                            }
                            else {
                                if (timerStarted) {
                                    Log.e("10minTimer", "Stop")
                                    isRelevantcontentOpen = false
                                    timerStarted = false
                                }
                                if (startdelaytime_started) {
                                    stopDelayTimer()
                                }
                            }

                        }
                        previousValue = isRelevantFacebookContent(content)
                    }
                } catch (e: Exception) {
                    Log.e("CONTENTEXCEPTION", "An exception occurred: ${e.message}")
                }
            }

            "Instagram" -> {

                try {
                    if (!isRelevantAppOpen) {
                        isRelevantAppOpen = true
                    }
                    if (!timerStarted && infinte) {
                        Log.e("10minTimer", "Start")
                        startScrollTimer()
                        timerStarted = true
                    }
                    if (!startdelaytime_started && infinte) {
                        Log.e("10minTimer", "Start")
                        startDelayTimer()
                    }

                    if (isRelevantInstagramContent(content) != "" && isRelevantInstagramContent(content) != previousValue) {
                        if ((isRelevantInstagramContent(content) == "IS" && previousValue == "NO IS") || (isRelevantInstagramContent(content) == "NO IS" && previousValue == "IS")) {
                            if (previousValue == "NO IS") {
                                infinte = true
                            }
                            else if (previousValue == "IS") {
                                infinte = false
                                timerStarted = false
                                if (OverlayShowed || OverlayClicked || SpotOverlayShowed || VibrationStarted) {
                                    stopOverlay()
                                    stopSpotOverlayService()
                                    stopVibration()
                                    stopDelayTimer()
                                    startQuestionnaire()
                                }
                                else {
                                    stopDelayTimer()
                                }
                            }
                            if (infinte) {
                                if (!timerStarted && !isRelevantcontentOpen) {
                                    isRelevantcontentOpen = true
                                    Log.e("10minTimer", "Start")
                                    startScrollTimer()
                                    timerStarted = true
                                }
                                if (!startdelaytime_started) {
                                    startDelayTimer()
                                }
                            }
                            else {
                                if (timerStarted) {
                                    Log.e("10minTimer", "Stop")
                                    isRelevantcontentOpen = false
                                    timerStarted = false
                                }
                                if (startdelaytime_started) {
                                    stopDelayTimer()
                                }
                            }
                        }
                        previousValue = isRelevantInstagramContent(content)
                    }
                } catch (e: Exception) {
                    Log.e("CONTENTEXCEPTION", "An exception occurred: ${e.message}")
                }
            }

            "TikTok" -> {

                try {
                    if (!isRelevantAppOpen) {
                        isRelevantAppOpen = true
                    }
                    if (!timerStarted && infinte) {
                        Log.e("10minTimer", "Start")
                        startScrollTimer()
                        timerStarted = true
                    }
                    if (!startdelaytime_started && infinte) {
                        Log.e("10minTimer", "Start")
                        startDelayTimer()
                    }

                    if (isRelevantTiktokContent(content) != "" && isRelevantTiktokContent(content) != previousValue) {
                        if ((isRelevantTiktokContent(content) == "IS" && previousValue == "NO IS") || (isRelevantTiktokContent(content) == "NO IS" && previousValue == "IS")) {
                            if (previousValue == "NO IS") {
                                infinte = true
                            }
                            else if (previousValue == "IS") {
                                infinte = false
                                timerStarted = false
                                if (OverlayShowed || OverlayClicked || SpotOverlayShowed || VibrationStarted) {
                                    stopOverlay()
                                    stopSpotOverlayService()
                                    stopVibration()
                                    stopDelayTimer()
                                    startQuestionnaire()
                                }
                                else {
                                    stopDelayTimer()
                                }
                            }
                            if (infinte) {
                                if (!timerStarted && !isRelevantcontentOpen) {
                                    isRelevantcontentOpen = true
                                    Log.e("10minTimer", "Start")
                                    startScrollTimer()
                                    timerStarted = true
                                }
                                if (!startdelaytime_started) {
                                    startDelayTimer()
                                }
                            }
                            else {
                                if (timerStarted) {
                                    Log.e("10minTimer", "Stop")
                                    isRelevantcontentOpen = false
                                    timerStarted = false
                                }
                                if (startdelaytime_started) {
                                    stopDelayTimer()
                                }
                            }
                        }
                        previousValue = isRelevantTiktokContent(content)
                    }
                } catch (e: Exception) {
                    Log.e("CONTENTEXCEPTION", "An exception occurred: ${e.message}")
                }
            }

            // do nothing when InfiniteScape is opened including the overlay
            "InfiniteScape" -> {
                isRelevantAppOpen = false
                timerStarted = false
            }

            else -> {
                isRelevantAppOpen = false
            }
        }
    }

    /**
     * Appends [packageName] to [packageNameQueue], a fixed-size (10) FIFO
     * history of recently seen foreground packages.
     */
    fun addPackageNameToArray(packageName: String) {
        // Check if the queue is already at max capacity
        if (packageNameQueue.size == 10) {
            // Remove the oldest package name
            packageNameQueue.removeFirst()
        }
        // Add the new package name to the end of the queue
        packageNameQueue.addLast(packageName)
    }

    /**
     * Records the current time as the start of the scrolling session, used
     * by [isScrollTimerExpired] to decide when to trigger an intervention.
     */
    private fun startScrollTimer() {
        timeStampScrollTimerStarted = LocalDateTime.now()
        Log.e("startScrollTimer", "started,  $packageName")
    }

    /**
     * Returns whether [scrollingTimer] seconds have elapsed since [otherTime].
     */
    private fun isScrollTimerExpired(otherTime: LocalDateTime): Boolean {
        // Adding scrollingTimer to the input LocalDateTime instance
        val addScrollingTimer = otherTime.plus(scrollingTimer, ChronoUnit.SECONDS)

        // Current LocalDateTime instance
        val currentDateTime = LocalDateTime.now()

        // Return true if the current time is after the added time, false otherwise
        return currentDateTime.isAfter(addScrollingTimer)
    }



    /**
     * The "Pop-Up" intervention: shows a full-screen overlay with a
     * "Dismiss" button over the tracked app, prompting the participant to
     * close it. Also starts the delay timer that measures how long they
     * take to actually close the app afterward.
     */
    @SuppressLint("SuspiciousIndentation")
    fun startOverlay() {
        // Sets the intervention type
        interventionType = "Pop-Up"

        Log.e("OVERLAY", "START")
        startdelaytime_started = true
        startDelayTimer()
        OverlayShowed = true
        isOverlayBeingDismissed = true
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.intervention_overlay, null)
        timerTextView = floatingView?.findViewById(R.id.textHeadline)
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        var appname = sharedPref.getString("App_Name", "App_Name")

        val editor = sharedPref.edit()
        editor.putString("interventionType", interventionType)
        editor.apply()

        AppNameToBeShownInOverlay =
            getAppName(findMostFrequentPackageName(packageNameQueue).toString())
        timerTextView?.text = "Time to close \n" + AppNameToBeShownInOverlay

        Log.d(
            "OverlayTimerText",
            getAppName(findMostFrequentPackageName(packageNameQueue).toString())
        )

        // Add the floating view to the window manager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE,
            PixelFormat.TRANSLUCENT

        )

        params.gravity = Gravity.CENTER

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        val closeButton = floatingView?.findViewById<Button>(R.id.buttonCloseOverlay)

        // Set button text to "Dismiss"
        closeButton?.text = "Dismiss"
        closeButton?.setOnClickListener {
            windowManager.removeView(floatingView)
            floatingView = null
            infinte = true
            isRelevantApp = true
            lastForegroundApp = ""
            OverlayClicked = true
            isOverlayBeingDismissed = false
        }

        Log.d("AppCheckerService", "Start overlay")
    }


    /**
     * Starts a per-second counter that tracks how long the participant
     * remains in the tracked app after an intervention was triggered;
     * elapsed time is read back out (and logged to Firestore) by
     * [stopDelayTimer].
     */
    fun startDelayTimer() {
        d_timer_value = 0
        d_timer = Runnable {
            d_timer_value++
            println("DelayTime: $d_timer_value")
            dtimerHandler?.postDelayed(d_timer!!, 1000)
        }

        dtimerHandler = Handler()

        startDelayTime = System.currentTimeMillis()
        dtimerHandler?.postDelayed(d_timer!!, 1000)

        Log.e("DelayTimer", "Started + $")
        startdelaytime_started = true
        AppNameToBeShownInOverlay = getAppName(findMostFrequentPackageName(packageNameQueue).toString())
    }

    /**
     * Stops the timer started by [startDelayTimer], computes the elapsed
     * "time to close" duration, and uploads it to Firestore's `delay_time`
     * collection.
     */
    fun stopDelayTimer() {
        var elapsedTimeInMinutes = 0L
        if (startdelaytime_started == true) {

            val dID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            dtimerHandler?.removeCallbacks(d_timer!!)

            Log.d("DelayTimer", "Stopped")



            val currentTime = System.currentTimeMillis()
            delayTimeInSeconds = (currentTime - startDelayTime) / 1000
            elapsedTimeInMinutes = delayTimeInSeconds / 60
            val remainingSeconds = delayTimeInSeconds % 60

            Log.d(
                "DelayTimer",
                "User closed app after : $elapsedTimeInMinutes:$remainingSeconds minutes"
            )

            val sharedPref = getSharedPreferences("InfiniteScroll", 0)
            pID = sharedPref.getString("pID", "").toString()

            val formattedTime = String.format("%02d:%02d minutes", elapsedTimeInMinutes, remainingSeconds)

            var sdf_1: Calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())
            var formattedTimestamp = sdf.format(sdf_1.time)


            val editor = sharedPref.edit()
            editor.putString("timestamp", formattedTimestamp)
            editor.putLong("delayTime", delayTimeInSeconds)
            Log.e("CheckEditorDelayTime", delayTimeInSeconds.toString())
            package_name = AppNameToBeShownInOverlay
            Log.e("AppNameToBeShownInOverlay", AppNameToBeShownInOverlay)
            Log.e("AppNameToBeShownInOverlayPackage", package_name)

            editor.putString("appName", package_name)
            editor.apply()

            val db = FirebaseFirestore.getInstance()
            val data = hashMapOf(
                "delayTime" to delayTimeInSeconds,
                "delayTimeFormatted" to formattedTime,
                "Android ID" to dID,
                "pID" to pID,
                "timestamp" to formattedTimestamp,
                "appName" to package_name
            )
            db.collection("delay_time").add(data)
            Log.d("DelayTimer", "DelayTime added $delayTimeInSeconds")
            startdelaytime_started = false
        }
        else {
            Log.d("DelayTimer", "DelayTime set to zero: $delayTimeInSeconds $elapsedTimeInMinutes")
        }

        startdelaytime_started = false
        delayTimeInSeconds = 0
        elapsedTimeInMinutes = 0
    }

    /**
     * Removes the [startOverlay] pop-up view from the window, if showing.
     */
    fun stopOverlay() {
        floatingView?.let {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.removeViewImmediate(it)
            floatingView = null
        }
        OverlayShowed = false
        Log.e("OVERLAY", "STOP" + isRelevantcontentOpen.toString())

        Log.d("AppCheckerService", "overlay stopped")
    }


    /**
     * The "Vibration" intervention: vibrates the device in a gradually
     * intensifying pulse pattern (increasing amplitude, shrinking pauses)
     * for about 3.5 minutes to nudge the participant to put the phone down.
     */
    fun startVibration() {

        if (VibrationStarted) {
            return
        }

        // remove all old planed vibrations!
        if (::vibrationHandler.isInitialized) {
                vibrationHandler.removeCallbacksAndMessages(null)
        }

        // Sets the intervention type
        interventionType = "Vibration"
        VibrationStarted = true

        Log.e("VIBRATION", "START")
        startdelaytime_started = true
        startDelayTimer()

        // Initialize shared preferences
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val editor = sharedPref.edit()
        editor.putString("interventionType", interventionType)
        editor.apply()

        // Get the app name for display
        AppNameToBeShownInOverlay = getAppName(findMostFrequentPackageName(packageNameQueue).toString())

        // Initialize the vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Check if the device has a vibrator
        if (vibrator.hasVibrator()) {
            vibrationHandler = Handler(Looper.getMainLooper())

            val totalDuration = 211000L // Total vibration duration of 3 minutes 31 seconds
            val maxAmplitude = 255 // Maximum vibration intensity
            val initialAmplitude = 30 // Initial vibration intensity
            val amplitudeIncrement = 3 // Increment of amplitude in each step
            val initialPauseDuration = 5000L // Initial pause duration
            val pulseDuration = 500L // Vibration pulse duration
            var elapsedTime = 0L // Tracks the total elapsed time
            var currentAmplitude = initialAmplitude // Start with the initial amplitude

            var currentPauseDuration = initialPauseDuration // Start with the initial pause duration
            val pauseDecrement = 70 // Decrement for the pause duration

            // Runnable to handle the vibration logic
            vibrationRunnable = object : Runnable {
                override fun run() {

                    if (!VibrationStarted) {
                        Log.d("AppCheckerService", "Vibration gestoppt, Runnable wird nicht mehr ausgeführt.")
                        return
                    }

                    // Vibrate with current settings
                    vibrator.vibrate(VibrationEffect.createOneShot(pulseDuration, currentAmplitude))

                    // Log current settings and update elapsed time
                    Log.d("AppCheckerService", "Amplitude: $currentAmplitude, Pause Duration: $currentPauseDuration ms, Elapsed Time: $elapsedTime ms")

                    // Increment amplitude up to the maximum
                    if (currentAmplitude < maxAmplitude) {
                        currentAmplitude += amplitudeIncrement
                    }

                    // Decrease pause duration but do not let it go below zero
                    if (currentPauseDuration > 200) {
                        currentPauseDuration -= pauseDecrement
                    }
                    else {
                        currentPauseDuration = 0
                    }

                    elapsedTime += pulseDuration + currentPauseDuration

                    // Check if the total duration has been reached
                    if (elapsedTime < totalDuration) {
                        vibrationHandler.postDelayed(this, pulseDuration + currentPauseDuration)
                    }
                    // Continue vibrating indefinitely at full strength
                    else {
                        vibrationHandler.postDelayed(this, pulseDuration)
                    }
                }
            }

            // Start the initial vibration cycle
            vibrationHandler.postDelayed(vibrationRunnable, currentPauseDuration)
        }

        Log.d("AppCheckerService", "Vibration started")
    }


    /**
     * Cancels an in-progress [startVibration] intervention.
     */
    fun stopVibration() {
        if (::vibrationHandler.isInitialized && VibrationStarted) {
            VibrationStarted = false;
            vibrator.cancel()
            vibrationHandler.removeCallbacksAndMessages(null)
            Log.d("AppCheckerService", "vibration stopped")
        }
    }


    /**
     * The "SpotOverlay" intervention: starts [SpotOS], the service that
     * draws gradually appearing/animating spots over the tracked app.
     */
    fun startSpotOverlayService() {

        // Sets the intervention type
        interventionType = "SpotOverlay"

        // delaytimer starten
        Log.e("SPOTOVERLAY", "START")
        startdelaytime_started = true
        startDelayTimer()

        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        var appname = sharedPref.getString("App_Name", "App_Name")
        val editor = sharedPref.edit()
        editor.putString("interventionType", interventionType)
        editor.apply()

        // Get the appName
        AppNameToBeShownInOverlay =
            getAppName(findMostFrequentPackageName(packageNameQueue).toString())

        // starts the Service
        spotServiceIntent = Intent(this, SpotOS::class.java)
        startService(spotServiceIntent)
        SpotOverlayShowed = true
        Log.d("AppCheckerService", "Spot overlay service started")
    }


    /**
     * Tells [SpotOS] to remove all spots and stop, ending an in-progress
     * [startSpotOverlayService] intervention.
     */
    fun stopSpotOverlayService() {

        if (SpotOverlayShowed) {
            // intent to remove Spots
            val intent = Intent(this, SpotOS::class.java).apply {
                action = "ACTION_REMOVE_SPOTS"
            }

            // send Intent to remove all Spots and stop the service
            startService(intent)
            SpotOverlayShowed = false
            Log.d("AppCheckerService", "Spot overlay service stopped")
        }

    }

    /**
     * Required by [AccessibilityService]; unused, as this service doesn't
     * need to react to being interrupted.
     */
    override fun onInterrupt() {

    }

}



