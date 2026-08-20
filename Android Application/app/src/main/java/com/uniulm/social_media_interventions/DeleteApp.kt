package com.uniulm.social_media_interventions

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Screen shown at the very end of the study, prompting the participant to
 * uninstall the app. Opened via the "Request sent" notification created in
 * [FinalQuestionnaire].
 */
class DeleteApp: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deleteapp)

        val sharedPref = getSharedPreferences("InfiniteScroll", 0)


    }
}