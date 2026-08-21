package com.uniulm.social_media_interventions

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import kotlinx.android.synthetic.main.activity_welcome.*

/**
 * The app's launcher activity. Shows the welcome/consent screen (with a link
 * to [ToS_activity]) for new participants, or re-enters [MainActivity]
 * directly for participants who already consented and are mid-study. If the
 * study has already ended, it instead shows the "delete this app" screen.
 */
class WelcomeActivity : AppCompatActivity() {
    var checkboxclicked= false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val studyended = sharedPref.getBoolean("studyended", true)
        if (studyended == true){
            setContentView(R.layout.activity_welcome)

            val tos = this.findViewById<TextView>(R.id.tosview)

            tos.setOnClickListener {
                val intent = Intent(this, ToS_activity::class.java)
                startActivity(intent)
                finish()
            }

            val versionAPI = Build.VERSION.SDK_INT
            if (versionAPI < 26) {
                val dialogClickListener =
                    DialogInterface.OnClickListener { dialog, which ->
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                finish()
                            }
                        }
                    }

                val builder: androidx.appcompat.app.AlertDialog.Builder =
                    androidx.appcompat.app.AlertDialog.Builder(this)

                builder.setMessage("Your android version does not meet the criteria for this study. You can deinstall this app.")
                    .setPositiveButton("Ok", dialogClickListener).setCancelable(false)
                    .show()


            }

            var age = sharedPref.getString("age", "EMPTY")
            Log.e("age", age.toString())
            Log.e("SP", sharedPref.all.toString())

            var check = sharedPref.getBoolean("checkboxclicked", false)
            if (check == true) {
                Log.e("BUGFIX", "if checkboxclicked : $checkboxclicked")
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                check = false
            }
            welcomeButton.setOnClickListener {
                if (checkBox.isChecked) {
                    intent = Intent(this, PermissionActivity::class.java)
                    startActivity(intent)


                } else {
                    Toast.makeText(
                        this,
                        "Please check the box at the end of the text",
                        Toast.LENGTH_LONG
                    )
                }

            }

            val code = sharedPref.getString("CODE", "true")
            if (code != null && code != "true") {
                Log.e("CODE", code.toString())
                val intent = Intent(this, MainActivity::class.java)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                startActivity(intent)
                finish()
            }


        }else
        {
            setContentView(R.layout.activity_deleteapp)

        }



    }

    override fun onDestroy() {
        super.onDestroy()

    }
}

