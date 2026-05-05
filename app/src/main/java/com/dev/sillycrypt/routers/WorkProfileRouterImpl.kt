package com.dev.sillycrypt.routers

import android.content.Context
import android.content.Intent
import com.dev.sillycrypt.MainActivity
import com.libsillycrypt.workprofile.domain.router.WorkProfileRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkProfileRouterImpl @Inject constructor(
    @ApplicationContext private val context: Context
): WorkProfileRouter {
    override fun openActivity() {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launchIntent)
    }
}