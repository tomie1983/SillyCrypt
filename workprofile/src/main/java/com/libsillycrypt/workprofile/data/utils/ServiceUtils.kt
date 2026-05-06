package com.libsillycrypt.workprofile.data.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.libsillycrypt.workprofile.services.IStartActivityProxy
import com.libsillycrypt.workprofile.services.IWorkProfileManageService
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

    private var serviceMain: IWorkProfileManageService? = null
    private var serviceWork: IWorkProfileManageService? = null

    fun deleteWorkProfile(): Boolean {
        return serviceWork?.deleteWorkProfile() == true
    }

    fun bindMainService(tryStartWorkService: () -> Unit) {
        bindService(
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    serviceMain = IWorkProfileManageService.Stub.asInterface(service)
                    tryStartWorkService()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // dummy
                }
            }, false
        )
    }


    fun mainServiceSetStartActivityProxy(proxy: IStartActivityProxy) {
        serviceMain?.setStartActivityProxy(proxy)
    }

    fun workServiceSetStartActivityProxy(proxy: IStartActivityProxy) {
        serviceWork?.setStartActivityProxy(proxy)
    }

    fun bindService(conn: ServiceConnection, foreground: Boolean) {
        unbindWorkProfileService()
        val intent = Intent(context, WorkProfileManageService::class.java)
        intent.putExtra("foreground", foreground)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        shelterServiceConnection = conn
    }

    fun unbindWorkProfileService() {
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

    fun bindWorkService(binder: IBinder?) {
        serviceWork = IWorkProfileManageService.Stub.asInterface(binder)
    }
}