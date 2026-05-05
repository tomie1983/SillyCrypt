package com.libsillycrypt.workprofile.presentation.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import com.libsillycrypt.workprofile.domain.router.WorkProfileRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class SillyCryptDeviceAdminReceiver: DeviceAdminReceiver() {

    @Inject
    lateinit var workProfileRepository: WorkProfileRepository

    @Inject
    lateinit var workProfileRouter: WorkProfileRouter

    @Inject
    lateinit var dispatcherIo: CoroutineDispatcher

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.w("isProfileOwner","onProfileProvisioningComplete")
        super.onProfileProvisioningComplete(context, intent)
        return
//        CoroutineScope(SupervisorJob() + dispatcherIo).launch {
//            if (workProfileRepository.isProfileOwner()) {
//                workProfileRepository.loadFromSettings()
//            }
//            withContext(Dispatchers.Main) {
//                workProfileRouter.openActivity()
//            }
//        }
    }
}