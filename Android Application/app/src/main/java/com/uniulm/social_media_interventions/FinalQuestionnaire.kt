package com.uniulm.social_media_interventions

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*


/**
 * The last screen of the study, opened from the [NotificationReceiver]
 * "study completed" notification. On submit it records the final Firestore
 * entry, stops [AppCheckerService] and [TimerService], marks the study as
 * ended in shared preferences, and shows a follow-up notification that opens
 * [DeleteApp].
 */
class FinalQuestionnaire : AppCompatActivity() {
var formattedExpectedEndStudy = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalquestionnaire)

        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        Log.e("STUDYTIMER", "I AM IN FINALQUEST")
        val pid_last = sharedPref.getString("pID", "")

        val submitButton = findViewById<Button>(R.id.submitButton)


       submitButton.setOnClickListener {

           var last_checkout: Calendar = Calendar.getInstance()
           val last_checkout_2= SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())
           val pid_val= pid_last.toString()
           var last_checkout_format  = last_checkout_2.format(last_checkout.time)

           formattedExpectedEndStudy =  sharedPref.getString("expectedEndStudy", "").toString()
           val androidID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
           FirebaseApp.initializeApp(this)
           val db = FirebaseFirestore.getInstance()

           data class FinalTimer(
               val formattedExpectedEndStudy: String,
               val last_checkout_format: String,
               val pid_val: String,
               val androidID: String
           )
           val finalTimer = FinalTimer(formattedExpectedEndStudy, last_checkout_format, pid_val, androidID)

           db.collection("final")
               .add(finalTimer)
               .addOnSuccessListener { documentReference -> Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")}
               .addOnFailureListener { e ->Log.w("Firestore", "Error adding document", e) }

           val i = baseContext.packageManager.getLaunchIntentForPackage(
               baseContext.packageName
           )
           val sharedPref = getSharedPreferences("InfiniteScroll", 0)
           sharedPref.edit().remove("checkboxclicked").apply()
           sharedPref.edit().remove("permissionsgiven").apply()



           val intent = Intent(this, AppCheckerService::class.java)
           stopService(intent)
           val timerServiceIntent = Intent(this, TimerService::class.java)
           stopService(timerServiceIntent)

           editor.putBoolean("studyended", true).apply()
           Log.e("QUIT", "true")
           val notificationIntent = Intent(applicationContext, DeleteApp::class.java)
           val pendingIntent = PendingIntent.getActivity(
               applicationContext,
               0, notificationIntent, 0 or PendingIntent.FLAG_IMMUTABLE
           )
           val builder = Notification.Builder(applicationContext, CHANNEL_ID)
               .setContentTitle("Request sent")
               .setContentText("You can delete the application now")
               .setSmallIcon(R.drawable.ic_stat_name)
               .setContentIntent(pendingIntent)
               .setOngoing(true)
               .setVisibility(Notification.VISIBILITY_PUBLIC)
               .setFullScreenIntent(pendingIntent, true)
               .setAutoCancel(false)




           val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
               description = descriptionText
           finish()

           }

           val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
           notificationManager.notify(notificationId, builder.build())
           notificationManager.createNotificationChannel(channel)
       }
    }
}


