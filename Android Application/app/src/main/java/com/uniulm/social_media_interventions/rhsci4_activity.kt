package com.uniulm.social_media_interventions

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.uniulm.social_media_interventions.*
import kotlinx.android.synthetic.main.activity_after_rshci.*
import kotlinx.android.synthetic.main.activity_after_rshci.button_continue
import kotlinx.android.synthetic.main.activity_after_rshci4.*
import kotlinx.android.synthetic.main.activity_sess_feeling_after.*
import kotlinx.android.synthetic.main.activity_sess_feeling_after.button_back
import kotlinx.android.synthetic.main.activity_sess_feeling_after.radGroup
import org.json.JSONArray

class rhsci4_activity : AppCompatActivity() {

    val question = "How do you feel after the session?"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_after_rshci4)

        this.button_continue.setOnClickListener {


            var answer1 = 42
            if(this.rad1.isChecked){
                answer1 = 5
                saveres(answer1)
            }else if (rad2.isChecked){
                answer1 = 4
                saveres(answer1)
            }else if (rad3.isChecked){
                answer1 = 3
                saveres(answer1)
            }else if (rad4.isChecked){
                answer1 = 2
                saveres(answer1)
            }else if (rad5.isChecked){
                answer1 = 1
                saveres(answer1)
            }else{
                Toast.makeText(
                    this,
                    "Please make a choice!", Toast.LENGTH_SHORT).show()
            }

        }

        this.button_back.setOnClickListener {

            val intent = Intent(this, rhsci3_activity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

    }

    override fun onBackPressed() {
        val intent = Intent(this, rhsci3_activity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    fun gon(){
        val intent = Intent(this, rhsci5_activity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    fun saveres(answer1:Int){
        val sharedPref = getSharedPreferences("InfiniteScroll", 0)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.putInt("RSHCI4", answer1)
        editor.apply()
        gon()
    }
}