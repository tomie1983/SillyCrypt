package com.libsillycrypt.workprofile.presentation.contracts

import android.app.Activity.RESULT_OK
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.libsillycrypt.workprofile.presentation.receivers.SillyCryptDeviceAdminReceiver

class ProfileProvisionContract : ActivityResultContract<Void?, Boolean?>() {
    override fun createIntent(context: Context, input: Void?): Intent {
        val admin = ComponentName(
            context.applicationContext,
            SillyCryptDeviceAdminReceiver::class.java
        )
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        intent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            admin
        )
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == RESULT_OK
    }
}