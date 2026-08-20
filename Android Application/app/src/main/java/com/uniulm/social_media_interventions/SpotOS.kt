package com.uniulm.social_media_interventions

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.random.Random

/**
 * Implements the visual "gradual intervention": a full-screen overlay
 * [Service] that scatters semi-transparent black spots ([SpotOverlayView])
 * over the screen with increasing speed, gradually obscuring the scrolling
 * content. Started/stopped by [AppCheckerService] via
 * `startSpotOverlayService()`/`stopSpotOverlayService()`.
 */
class SpotOS : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var spotOverlayView: SpotOverlayView
    private val handler = Handler(Looper.getMainLooper())
    private val spotBitmaps = mutableListOf<Bitmap>()
    private val totalSpots = 3000 // Total Spots to cover whole Screen
    private val totalTime = 211000L // Total time of 3 minutes 31 seconds in milliseconds

    override fun onCreate() {
        super.onCreate()
        Log.d("Spot", "Service started")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create the overlay view for displaying spots
        spotOverlayView = SpotOverlayView(this)

        // Load spot images and set up overlay parameters
        loadSpotBitmaps()
        setupOverlay()
        graduallyCreateSpots()
    }

    /**
     * Handles the "ACTION_REMOVE_SPOTS" command sent by [AppCheckerService]
     * to clear the overlay and stop the service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_REMOVE_SPOTS") {
            removeAllSpots()
            stopSelf()
        }
        return START_STICKY
    }

    /**
     * Adds the transparent, full-screen [spotOverlayView] to the window
     * manager as a system overlay.
     */
    private fun setupOverlay() {
        val screenSize = Point()
        windowManager.defaultDisplay.getSize(screenSize)
        val layoutParams = WindowManager.LayoutParams(
            screenSize.x + 50,
            screenSize.y + 400,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        windowManager.addView(spotOverlayView, layoutParams)
    }

    /**
     * Decodes the twelve spot drawables used to render the overlay.
     */
    private fun loadSpotBitmaps() {
        val drawableIds = listOf(
            R.drawable.spot1_2, R.drawable.spot2_2, R.drawable.spot3_2,
            R.drawable.spot4_2, R.drawable.spot5_2, R.drawable.spot6_2,
            R.drawable.spot7_2, R.drawable.spot8_2, R.drawable.spot9_2,
            R.drawable.spot10_2, R.drawable.spot11_2, R.drawable.spot12_2
        )
        drawableIds.forEach { id ->
            spotBitmaps.add(BitmapFactory.decodeResource(resources, id))
        }
    }

    /**
     * Schedules all 3000 spots to appear over [totalTime], split into three
     * phases that speed up over time (1% of spots slowly, 10% at medium
     * speed, then the remaining 89% quickly) to create the "gradually
     * intensifying" effect.
     */
    private fun graduallyCreateSpots() {
        val phaseOneDuration = (totalTime * 0.45).toLong() // 50% of the total time for the slow initial phase
        val phaseTwoDuration = (totalTime * 0.4).toLong() // 40% of the total time for the middle phase
        val phaseThreeDuration = totalTime - phaseOneDuration - phaseTwoDuration // 10% of the total time for the fast final phase

        val phaseOneSpots = (totalSpots * 0.01).toInt() // 1% of the spots appear very slowly
        val phaseTwoSpots = (totalSpots * 0.10).toInt() // 10% of the spots for the middle phase
        val phaseThreeSpots = totalSpots - phaseOneSpots - phaseTwoSpots // 89% of the spots for the fast phase

        var nextDelay = 0L

        // Very slow appearance in Phase One
        for (i in 0 until phaseOneSpots) {
            val delay = phaseOneDuration / phaseOneSpots // Spread the time evenly over the number of spots
            nextDelay += delay
            handler.postDelayed({ addSpot() }, nextDelay)
        }

        // Medium speed in Phase Two
        for (i in phaseOneSpots until (phaseOneSpots + phaseTwoSpots)) {
            val delay = phaseTwoDuration / phaseTwoSpots
            nextDelay += delay
            handler.postDelayed({ addSpot() }, nextDelay)
        }

        // Faster appearance in Phase Three
        for (i in (phaseOneSpots + phaseTwoSpots) until totalSpots) {
            val delay = phaseThreeDuration / phaseThreeSpots
            nextDelay += delay
            handler.postDelayed({ addSpot() }, nextDelay)
        }
    }

    /**
     * Adds a single spot at a random screen position with a random bitmap.
     */
    private fun addSpot() {
        val screenSize = Point().apply { windowManager.defaultDisplay.getSize(this) }
        val randomBitmap = spotBitmaps[Random.nextInt(spotBitmaps.size)]
        val x = Random.nextInt(-randomBitmap.width, screenSize.x + randomBitmap.width)
        val y = Random.nextInt(-randomBitmap.height, screenSize.y + randomBitmap.height)

        // Add the spot with fade-in animation
        val maxAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 200 else 150
        spotOverlayView.addSpotWithAnimation(randomBitmap, x, y, maxAlpha)
    }

    /**
     * Cancels any pending spot-creation callbacks and clears the overlay.
     */
    private fun removeAllSpots() {
        handler.removeCallbacksAndMessages(null)
        spotOverlayView.clearSpots()
        Log.d("SpotOS", "All spots removed and creation stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllSpots()
        windowManager.removeView(spotOverlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * Custom [View] that draws and animates the collection of spots for [SpotOS].
 */
class SpotOverlayView(context: Context) : View(context) {
    private val spots = mutableListOf<Spot>()
    private val paint = Paint().apply { isAntiAlias = true }

    /**
     * Adds a new spot at ([x], [y]) and animates it from fully transparent
     * and 10% scale up to [maxAlpha] and full scale over 15 seconds.
     */
    fun addSpotWithAnimation(bitmap: Bitmap, x: Int, y: Int, maxAlpha: Int) {
        val initialScale = 0.1f // Start with 10% of the original size
        val finalScale = 1.0f  // Scale up to 100% of the original size
        val spot = Spot(bitmap, x, y, alpha = 0, scale = initialScale) // Start with alpha 0 (invisible) and initial scale
        spots.add(spot)

        val animationHandler = Handler(Looper.getMainLooper())
        val animationDuration = 15000L // Animation duration (in milliseconds)
        val animationSteps = 100 // Number of steps in the fade-in and scaling animation
        val alphaIncrement = maxAlpha / animationSteps
        val scaleIncrement = (finalScale - initialScale) / animationSteps

        val animationRunnable = object : Runnable {
            var currentStep = 0
            override fun run() {
                if (currentStep < animationSteps) {
                    spot.alpha += alphaIncrement
                    spot.scale += scaleIncrement
                    invalidate() // Redraw the view
                    currentStep++
                    animationHandler.postDelayed(this, animationDuration / animationSteps)
                } else {
                    spot.alpha = maxAlpha
                    spot.scale = finalScale
                    invalidate()
                }
            }
        }

        animationHandler.post(animationRunnable)
    }

    /**
     * Removes all spots and redraws the (now empty) overlay.
     */
    fun clearSpots() {
        spots.clear()
        invalidate()
        Log.d("SpotOverlayView", "All spots cleared")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        spots.forEach { spot ->
            paint.alpha = spot.alpha
            val centerX = spot.x + spot.bitmap.width / 2
            val centerY = spot.y + spot.bitmap.height / 2
            canvas.save()
            canvas.scale(spot.scale, spot.scale, centerX.toFloat(), centerY.toFloat())
            canvas.drawBitmap(spot.bitmap, spot.x.toFloat(), spot.y.toFloat(), paint)
            canvas.restore()
        }
    }
}

/**
 * A single animated spot drawn by [SpotOverlayView]: a bitmap at a fixed
 * screen position with mutable [alpha]/[scale] driven by its fade-in
 * animation.
 */
data class Spot(val bitmap: Bitmap, val x: Int, val y: Int, var alpha: Int, var scale: Float)
