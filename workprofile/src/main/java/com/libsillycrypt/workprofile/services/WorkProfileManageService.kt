package com.libsillycrypt.workprofile.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.libsillycrypt.workprofile.R
import com.libsillycrypt.workprofile.data.manager.WorkProfileManager
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.domain.entities.ApplicationInfoWrapper
import com.libsillycrypt.workprofile.presentation.activities.DummyActivity
import com.libsillycrypt.workprofile.presentation.receivers.SillyCryptDeviceAdminReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class WorkProfileManageService: Service() {

    @Inject
    lateinit var serviceUtils: ServiceUtils

    @Inject
    lateinit var workProfileManager: WorkProfileManager

    private var startActivityProxy: IStartActivityProxy? = null
    private var isProfileOwner = false

    private var dpm: DevicePolicyManager? = null

    private var adminComponent: ComponentName? = null
    private val binder = object: IWorkProfileManageService.Stub() {
        override fun ping() {
            //do nothing
        }

        override fun deleteWorkProfile(): Boolean {
            Log.w("deleteWorkProfile","service")
            return workProfileManager.deleteProfile()
        }

        override fun stopShelterService(kill: Boolean) {


            // dirty: just wait for some time and kill this service itself
            Thread {
                try {
                    Thread.sleep(1)
                } catch (e: Exception) {
                }
                serviceUtils.unbindWorkProfileService()
                if (kill && !isProfileOwner) {
                    // Just kill the entire process if this signal is received and the process has nothing to do
                    exitProcess(0)
                }
            }.start()
        }

        override fun installApp(
            app: ApplicationInfoWrapper,
            callback: IAppInstallCallback
        ) {
            if (!app.isSystem) {
                // Installing a non-system app requires firing up PackageInstaller
                // Delegate this operation to DummyActivity because
                // Only it can receive a result
                val intent: Intent = Intent(DummyActivity.INSTALL_PACKAGE)
                intent.setComponent(ComponentName(this@WorkProfileManageService, DummyActivity::class.java))
                intent.putExtra("package", app.packageName)
                intent.putExtra("apk", app.sourceDir)
                intent.putExtra(
                    "split_apks",
                    app.splitApks
                )

                // Send the callback to the DummyActivity
                val callbackExtra = Bundle()
                callbackExtra.putBinder("callback", callback.asBinder())
                intent.putExtra("callback", callbackExtra)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                DummyActivity.registerSameProcessRequest(intent)
                if (startActivityProxy != null) startActivityProxy!!.startActivity(intent)
            } else {
                if (isProfileOwner) {
                    adminComponent?.let {
                        // We can only enable system apps in our own profile
                        dpm?.enableSystemApp(
                            it,
                            app.packageName
                        )
                    }

                    // Also set the hidden state to false.
                    dpm?.setApplicationHidden(
                        adminComponent,
                        app.packageName, false
                    )

                    callback.callback(Activity.RESULT_OK)
                } else {
                    callback.callback(RESULT_CANNOT_INSTALL_SYSTEM_APP)
                }
            }
        }

        override fun uninstallApp(
            app: ApplicationInfoWrapper,
            callback: IAppInstallCallback
        ) {
            if (!app.isSystem) {
                // Similarly, fire up DummyActivity to do uninstallation for us
                val intent = Intent(DummyActivity.UNINSTALL_PACKAGE)
                intent.setComponent(ComponentName(this@WorkProfileManageService, DummyActivity::class.java))
                intent.putExtra("package", app.packageName)

                // Send the callback to the DummyActivity
                val callbackExtra = Bundle()
                callbackExtra.putBinder("callback", callback.asBinder())
                intent.putExtra("callback", callbackExtra)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                DummyActivity.registerSameProcessRequest(intent)

                startActivityProxy?.startActivity(intent)
            } else {
                if (isProfileOwner) {
                    // This is essentially the same as disabling the system app
                    // There is no way to reverse the "enableSystemApp" operation here
                    dpm?.setApplicationHidden(
                        adminComponent,
                        app.packageName, true
                    )
                    callback.callback(Activity.RESULT_OK)
                } else {
                    callback.callback(RESULT_CANNOT_INSTALL_SYSTEM_APP)
                }
            }
        }

        override fun setStartActivityProxy(proxy: IStartActivityProxy?) {
            startActivityProxy = proxy
        }

    }

    override fun onBind(intent: Intent): IBinder {
        if (intent.getBooleanExtra("foreground", false)) {
            setForeground()
        }
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {

        // Stop our foreground notification (if it was created at all) when
        // all clients have disconnected.
        // This helps to ensure no notification is left when the Shelter activity
        // is closed.
        stopForeground(true)
        return false
    }

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService<DevicePolicyManager?>(DevicePolicyManager::class.java)
        adminComponent = ComponentName(applicationContext, SillyCryptDeviceAdminReceiver::class.java)
        dpm?.let {
            isProfileOwner = it.isProfileOwnerApp(packageName)
        }
    }

    fun buildNotification(
        context: Context,
        ticker: String?,
        title: String?,
        desc: String?,
        icon: Int
    ): Notification {
        return buildNotificationOreo(context, ticker, title, desc, icon)
    }

    private fun buildNotificationOreo(
        context: Context,
        ticker: String?,
        title: String?,
        desc: String?,
        icon: Int
    ): Notification {
        // Android O and later: Notification Channel
        val nm: NotificationManager =
            context.getSystemService<NotificationManager?>(NotificationManager::class.java)!!
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val chan = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(chan)
        }

        // Disable everything: do not disturb the user
        val chan = nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        chan.enableVibration(true)
        chan.importance = NotificationManager.IMPORTANCE_HIGH
        nm.createNotificationChannel(chan)

        // Create foreground notification to keep the service alive
        return Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setTicker(ticker)
            .setContentTitle(title)
            .setContentText(desc)
            .setSmallIcon(icon)
            .build()
    }

    private fun setForeground() {
        startForeground(
            NOTIFICATION_ID, buildNotification(
                this,
                getString(R.string.channel_name),
                getString(R.string.service_title),
                getString(R.string.service_desc),
                R.drawable.ic_android_black_24dp
            )
        )
    }

    companion object {
        // Utilities to build notifications for cross-version compatibility
        private const val NOTIFICATION_CHANNEL_ID: String = "SillyCryptService"
        private const val RESULT_CANNOT_INSTALL_SYSTEM_APP: Int = 100001
        private const val NOTIFICATION_ID: Int = 0x49a11
    }
}