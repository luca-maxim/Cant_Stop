package com.uniulm.social_media_interventions

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.uniulm.social_media_interventions.*
import kotlinx.android.synthetic.main.activity_sess_feeling_after.*
import org.json.JSONArray

class FeelingAfterLockActivity : AppCompatActivity() {

    val question = "How do you feel after the session?"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_res_feeling_after)

        this.button_submit.setOnClickListener {

            if ((radGroup.checkedRadioButtonId == -1) || (radGroup2.checkedRadioButtonId == -1) || (radGroup3.checkedRadioButtonId == -1)) {
                Toast.makeText(
                    this,
                    "Please make a choice in every row!", Toast.LENGTH_SHORT
                ).show()

            } else {

                val appsArray = JSONArray()

                val checkedRadioButtonId1 = radGroup.checkedRadioButtonId
                val checkedRadioButtonId2 = radGroup2.checkedRadioButtonId
                val checkedRadioButtonId3 = radGroup3.checkedRadioButtonId
                val radio1 = findViewById<RadioButton>(checkedRadioButtonId1)
                appsArray.put(radio1.text)
                val radio2 = findViewById<RadioButton>(checkedRadioButtonId2)
                appsArray.put(radio2.text)
                val radio3 = findViewById<RadioButton>(checkedRadioButtonId3)
                appsArray.put(radio3.text)

                Toast.makeText(
                    this,
                    "Thank you!", Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()

            }
        }
        this.button_back.setOnClickListener {
            val intent = Intent(this, rhsci1_activity::class.java)
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
}