package com.libsillycrypt.workprofile.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class FinalizeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.w("isProfileOwner","finalSetup")
        super.onCreate(savedInstanceState)
        val i = Intent(applicationContext, DummyActivity::class.java)
        i.setAction(DummyActivity.FINALIZE_PROVISION)
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        finish()
    }
}