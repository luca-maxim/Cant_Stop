package com.uniulm.social_media_interventions

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startActivity
import kotlinx.android.synthetic.main.activity_after_rshci.*

/**
 * First screen of the post-session questionnaire, shown right after
 * [AppCheckerService] or [MainActivity] detects that the tracked app was
 * closed. Continuing hands off to [rhsci1_activity], which runs the rest of
 * the questionnaire.
 */
class rhsci_activity : AppCompatActivity() {

    val question = "How do you feel after the session?"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_after_rshci)
        Log.e("I AM IN rhsci","BEFORE BUTTON")
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        var pid = sharedPref.getString("App_Name", "App_Name")

        var view = findViewById<TextView>(R.id.tvQuestion)
        view.text = pid.toString()+" has been closed"

        var sub = findViewById<TextView>(R.id.tvSubtext)
        sub.text = "Please continue and answer some questions about this incident!"

        val delayTimeInSeconds = intent.getLongExtra("delayTimeinSeconds", 0L)

        Log.e("I AM IN rhsci",delayTimeInSeconds.toString())
        this.button_continue.setOnClickListener {
            val intent = Intent(this, rhsci1_activity::class.java)
            intent.putExtra("delayTimeinSeconds", delayTimeInSeconds)

            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()

        }
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

}


