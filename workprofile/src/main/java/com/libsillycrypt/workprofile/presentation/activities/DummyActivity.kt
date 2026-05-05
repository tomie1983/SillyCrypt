package com.libsillycrypt.workprofile.presentation.activities

import android.Manifest
import android.app.ComponentCaller
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.libsillycrypt.workprofile.data.utils.AuthenticationUtility
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.data.utils.WorkProfileUtils
import com.libsillycrypt.workprofile.presentation.viewmodels.DummyActivityVM
import com.libsillycrypt.workprofile.services.IAppInstallCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.Volatile

@AndroidEntryPoint
class DummyActivity: AppCompatActivity() {

    @Inject
    lateinit var authenticationUtility: AuthenticationUtility

    @Inject
    lateinit var workProfileUtils: WorkProfileUtils

    @Inject
    lateinit var serviceUtils: ServiceUtils

    private val viewModel: DummyActivityVM by viewModels()

    private var dpm: DevicePolicyManager? = null
    private var isProfileOwner: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.w("workProfile", isProfileOwner.toString())

        super.onCreate(savedInstanceState)
        dpm = getSystemService(DevicePolicyManager::class.java)
        isProfileOwner = dpm?.isProfileOwnerApp(packageName) ?: false
        Log.w("workProfile", isProfileOwner.toString())
        if (isProfileOwner) {
            // If we are the profile owner, we enforce all our policies
            // so that we can make sure those are updated with our app
            workProfileUtils.enforceWorkProfilePolicies(this)
            workProfileUtils.enforceUserRestrictions(this)
            //SettingsManager.getInstance().applyAll()
            Log.w("workProfile", "continue")

                // Do not show permission dialog during finalization -- it will conflict with the provisioning UI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRequestedPermission && (FINALIZE_PROVISION != intent.action)) {
                    // Avoid requesting permission multiple times in one session
                    // This also prevents multiple instances of DummyActivity from being blocked on each other
                    hasRequestedPermission = true
                    // We pretty much only send notifications to keep the process inside work profile alive
                    // as such, only request the notification permission from inside the profile
                    // This will ideally be shown and done when the user sees the app list UI for the first time
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(
                            arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS),
                            DummyActivity.REQUEST_PERMISSION_POST_NOTIFICATIONS
                        )
                        // Continue once the request has been completed (see onRequestPermissionResult)
                        return
                    }
                }

        }

        init()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.w("workProfile","newIntent")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSION_POST_NOTIFICATIONS) {
            // Regardless of the result, continue initialization
            // This is fine because most functionalities will work anyway; it will just be a bit buggy
            // and unreliable.
            init()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun init() {
        Log.w("workProfile", intent.toString())
        lifecycleScope.launch {
            // First check if we have a registered request from the same process
            // if it passes, we don't have to check if it has proper signature any more
            if (!checkSameProcessRequest(intent)) {
                Log.w("workProfile","correctBranch")
                // Check the intent signature first
                // Call checkIntent() first, because we might receive an auth_key from the other side any time.
                // Calling checkIntent() will ensure that the first auth_key is properly received.
                // ONLY the first received one should be stored and trusted.
                if (!authenticationUtility.checkIntent(intent)) {
                    Log.w("workProfile", "faield check")
                    // If check failed and not in allowed-without-signature list
                    if (intent.action != FINALIZE_PROVISION) {
                        // Unauthenticated! Just exit IMMEDIATELY
                        finish()
                        return@launch
                    }
                }
            }

            when (intent.action) {
                START_SERVICE -> actionStartService()
                TRY_START_SERVICE -> {
                    setResult(RESULT_OK)
                    finish()
                }

                INSTALL_PACKAGE -> actionInstallPackage()
                UNINSTALL_PACKAGE -> actionUninstallPackage()
                DELETE_PROFILE -> actionDeleteProfile()
                FINALIZE_PROVISION -> actionFinalizeProvision()
                else -> finish()
            }
        }
    }

    private fun actionDeleteProfile() {
        viewModel.deleWorkProfile()
    }

    private fun actionInstallPackage() {
        var uri: Uri? = null
        if (intent.hasExtra("package")) {
            uri = Uri.fromParts("package", intent.getStringExtra("package"), null)
        }
        val policy = StrictMode.getVmPolicy()
        if (intent.hasExtra("apk")) {
            // I really have no idea about why the "package:" uri do not work
            // after Android O, anyway we fall back to using the apk path...
            // Since I have plan to support pre-O in later versions, I keep this
            // branch in case that we reduce minSDK in the future.
            uri = Uri.fromFile(File(intent.getStringExtra("apk")))
        } else if (intent.hasExtra("direct_install_apk")) {
            // Directly install an APK inside the profile
            // The APK will be an Uri from our own FileProviderProxy
            // which points to an opened Fd in another profile.
            // We must close the Fd when we finish.
            uri = intent.getParcelableExtra<Uri?>("direct_install_apk")
        }

        // A permissive VmPolicy must be set to work around
        // the limitation on cross-application Uri
        StrictMode.setVmPolicy(VmPolicy.Builder().build())


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // For Q, since we use the more "manual" method of installation,
                // we have to also pass the split APKs ("Configuration APKs" as Google calls it)
                // Although these are available since API 26, we don't need to
                // take care of them for versions before Q since we don't actually
                // install the APKs before Q.
                actionInstallPackageQ(uri, intent.getStringArrayExtra("split_apks"))
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        } else {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, packageName)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, REQUEST_INSTALL_PACKAGE)
        }

        // Restore the VmPolicy anyway
        StrictMode.setVmPolicy(policy)
    }

    private fun actionFinalizeProvision() {
        if (isProfileOwner) {
            finish()
        } else {
            viewModel.setProvisionedStatus(true)
            finish()
        }
    }

    // On Android Q, ACTION_INSTALL_PACKAGE has been deprecated.
    // We have to switch to using PackageInstaller for the job, which isn't quite
    // as elegant because now we really need to read the entire apk and write to it
    // Keep this case only for Q for now.
    @Throws(IOException::class)
    private fun actionInstallPackageQ(uri: Uri?, splitApks: Array<String>?) {
        val pi = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = pi.createSession(params)

        val session = pi.openSession(sessionId)
        doInstallPackageQ(uri, splitApks, session) {
            // We have finished piping the streams, show the progress as 10%
            session.setStagingProgress(0.1f)

            // Commit the session
            val intent = Intent(this, DummyActivity::class.java)
            intent.setAction(PACKAGEINSTALLER_CALLBACK)
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                intent, PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.getIntentSender())
        }
    }

    // The background part of the installation process on Q (reading APKs etc)
    // that must be executed on another thread
    // Put them in background to avoid stalling the UI thread
    private fun doInstallPackageQ(
        baseUri: Uri?,
        splitApks: Array<String>?,
        session: PackageInstaller.Session,
        callback: Runnable?
    ) {
        val uris = ArrayList<Uri>()
        uris.add(baseUri!!)
        if (!splitApks.isNullOrEmpty()) {
            for (apk in splitApks) {
                uris.add(Uri.fromFile(File(apk)))
            }
        }

        Thread {
            for (uri in uris) {
                try {
                    contentResolver.openInputStream(uri).use { `is` ->
                        session.openWrite(
                            UUID.randomUUID().toString(),
                            0,
                            `is`!!.available().toLong()
                        ).use { os ->
                            workProfileUtils.pipe(`is`, os)
                            session.fsync(os)
                        }
                    }
                } catch (e: IOException) {
                }
            }
            runOnUiThread(callback)
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_INSTALL_PACKAGE) {
            appInstallFinished(resultCode)
        }
    }

    private fun appInstallFinished(resultCode: Int) {
        if (!intent.hasExtra("callback")) return

        // Send the result code back to the caller
        val callbackExtra = intent.getBundleExtra("callback")
        val callback: IAppInstallCallback = IAppInstallCallback.Stub
            .asInterface(callbackExtra!!.getBinder("callback"))

        try {
            callback.callback(resultCode)
        } catch (e: RemoteException) {
            // do nothing
        }

        finish()
    }

    private fun actionUninstallPackage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            actionUninstallPackageQ()
            return
        }

        val uri = Uri.fromParts("package", intent.getStringExtra("package"), null)
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri)
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        // Currently, Install & Uninstall share the same logic
        // after starting the system PackageInstaller
        // because the only thing to do is to call the callback
        // with the result code.
        // If ANY separate logic is added for any of them,
        // the request code should be separated.
        startActivityForResult(intent, REQUEST_INSTALL_PACKAGE)
    }

    private fun actionUninstallPackageQ() {
        val pi = packageManager.packageInstaller
        val intent = Intent(this, DummyActivity::class.java)
        intent.setAction(PACKAGEINSTALLER_CALLBACK)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            intent, PendingIntent.FLAG_MUTABLE
        )
        pi.uninstall(getIntent().getStringExtra("package")!!, pendingIntent.intentSender)
    }

    private fun actionStartService() {
        // This needs to be foreground because this activity won't be able to hold
        // the ServiceConnection to it.
        serviceUtils.bindShelterService(object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val data = Intent()
                val bundle = Bundle()
                bundle.putBinder("service", service)
                data.putExtra("extra", bundle)
                setResult(RESULT_OK, data)
                finish()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // dummy
            }
        }, true)
    }

    companion object {
        const val FINALIZE_PROVISION: String = "com.libsillycrypt.workprofile.FINALIZE_PROVISION"
        const val START_SERVICE: String = "com.libsillycrypt.workprofile.START_SERVICE"
        const val TRY_START_SERVICE: String = "com.libsillycrypt.workprofile.TRY_START_SERVICE"
        const val INSTALL_PACKAGE: String = "com.libsillycrypt.workprofile.INSTALL_PACKAGE"
        const val UNINSTALL_PACKAGE: String = "com.libsillycrypt.workprofile.UNINSTALL_PACKAGE"
        const val DELETE_PROFILE: String = "com.libsillycrypt.workprofile.DELETE_PROFILE"
        const val SYNCHRONIZE_PREFERENCE: String =
            "com.libsillycrypt.workprofile.SYNCHRONIZE_PREFERENCE"
        const val PACKAGEINSTALLER_CALLBACK: String =
            "com.libsillycrypt.workprofile.PACKAGEINSTALLER_CALLBACK"

        private var hasRequestedPermission: Boolean = false

        private val ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS: List<String?> =
            listOf<String?>(
                INSTALL_PACKAGE,
                UNINSTALL_PACKAGE,
            )

        private const val REQUEST_PERMISSION_POST_NOTIFICATIONS = 3

        private const val REQUEST_INSTALL_PACKAGE = 1

        @Volatile
        private var sLastSameProcessRequest: Long = -1

        // Register that an intent will be sent to this Activity without signature
        // from the same process. Each registration is allowed for at most 5 seconds.
        @Synchronized
        fun registerSameProcessRequest(intent: Intent) {
            sLastSameProcessRequest = Date().time
            intent.putExtra("is_same_process", true)
        }

        @Synchronized
        private fun checkSameProcessRequest(intent: Intent): Boolean {
            if (!intent.getBooleanExtra("is_same_process", false)) return false
            if (sLastSameProcessRequest == -1L) return false

            val ret = Date().time - sLastSameProcessRequest <= 5000 // Timeout 5s
                    && ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS.contains(intent.action)
            if (ret) {
                sLastSameProcessRequest = -1 // Revoke the registered request
            }

            return ret
        }

    }
}