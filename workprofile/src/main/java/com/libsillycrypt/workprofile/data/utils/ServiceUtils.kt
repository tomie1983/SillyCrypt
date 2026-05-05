package com.libsillycrypt.workprofile.data.utils

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import com.libsillycrypt.workprofile.services.WorkProfileManageService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private var shelterServiceConnection: ServiceConnection? = null


    fun bindShelterService(conn: ServiceConnection, foreground: Boolean) {
        unbindShelterService()
        val intent = Intent(context, WorkProfileManageService::class.java)
        intent.putExtra("foreground", foreground)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        shelterServiceConnection = conn
    }

    fun unbindShelterService() {
        shelterServiceConnection?.let {
            try {
                context.unbindService(it)
            } catch (e: Exception) {
                // This method call might fail if the service is already unbound
                // just ignore anything that might happen.
                // We will be stopping already if this would ever happen.
            }
        }

        shelterServiceConnection = null
    }
}