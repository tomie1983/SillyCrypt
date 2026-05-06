package com.libsillycrypt.workprofile.presentation.activities

import android.Manifest
import android.R.attr.action
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.libsillycrypt.workprofile.data.utils.AuthenticationUtility
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.data.utils.WorkProfileUtils
import com.libsillycrypt.workprofile.domain.entities.ApplicationInfoWrapper
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

    private val installQueue = ArrayDeque<ApplicationInfoWrapper>()
    private var installCallback: IAppInstallCallback? = null

    private var waitingForPackageInstallerCallback = false

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
        setIntent(intent)

        if (intent.action == PACKAGEINSTALLER_CALLBACK) {
            handlePackageInstallerCallback(intent)
        }
    }

    private fun handlePackageInstallerCallback(callbackIntent: Intent) {
        callbackIntent.extras?.keySet()?.forEach { key ->
            Log.w("installQueue", "$key = ${callbackIntent.extras?.get(key)}")
        }

        val status = callbackIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        val message = callbackIntent.getStringExtra(
            PackageInstaller.EXTRA_STATUS_MESSAGE
        )

        val installedPackageName = callbackIntent.getStringExtra(
            PackageInstaller.EXTRA_PACKAGE_NAME
        )

        Log.w(
            "installQueue",
            "status=$status package=$installedPackageName message=$message"
        )

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = callbackIntent.getParcelableExtra<Intent>(
                    Intent.EXTRA_INTENT
                )

                Log.w("installQueue", "pending user action intent=$confirmationIntent")

                if (confirmationIntent != null) {
                    startActivityForResult(
                        confirmationIntent,
                        REQUEST_CONFIRM_INSTALL
                    )
                } else {
                    waitingForPackageInstallerCallback = false
                    installNextFromQueue()
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.w("installQueue", "install success package=$installedPackageName")

                waitingForPackageInstallerCallback = false
                installNextFromQueue()
            }

            else -> {
                Log.w("installQueue", "install failed: $message")

                waitingForPackageInstallerCallback = false
                installNextFromQueue()
            }
        }
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
                INSTALL_PACKAGES -> actionInstallPackages()
                FINALIZE_PROVISION -> actionFinalizeProvision()
                else -> finish()
            }
        }
    }

    private fun actionInstallPackages() {
        val apps = intent.getParcelableArrayListExtra<ApplicationInfoWrapper>(EXTRA_APPS)
            ?: arrayListOf()

        val callbackExtra = intent.getBundleExtra("callback")
        installCallback = IAppInstallCallback.Stub.asInterface(
            callbackExtra?.getBinder("callback")
        )

        installQueue.clear()
        installQueue.addAll(apps)

        installNextFromQueue()
    }

    private fun installNextFromQueue() {
        if (waitingForPackageInstallerCallback) {
            Log.w("installQueue", "Already waiting for PackageInstaller callback")
            return
        }

        val app = installQueue.removeFirstOrNull()

        if (app == null) {
            try {
                installCallback?.callback(RESULT_OK)
            } catch (e: RemoteException) {
                Log.w("installQueue", e.stackTraceToString())
            }

            finish()
            return
        }

        Log.w("installQueue", "installing ${app.packageName}")

        val uri = Uri.fromFile(File(app.sourceDir))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                waitingForPackageInstallerCallback = true

                actionInstallPackageQ(
                    packageName = app.packageName,
                    uri = uri,
                    splitApks = app.splitApks?.filterNotNull()?.toTypedArray()
                )
            } else {
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                    putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, packageName)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivityForResult(installIntent, REQUEST_LEGACY_INSTALL_PACKAGE)
            }
        } catch (e: Exception) {
            waitingForPackageInstallerCallback = false

            Log.w("installQueue", e.stackTraceToString())

            installNextFromQueue()
        }
    }

    private fun actionInstallPackage() {
        var uri: Uri? = null

        val packageName = intent.getStringExtra("package")
        val apkPath = intent.getStringExtra("apk")
        val splitApks = intent.getStringArrayExtra("split_apks")

        Log.w("installPackage", "package=$packageName")
        Log.w("installPackage", "apk=$apkPath")
        Log.w("installPackage", "splitApks=${splitApks?.contentToString()}")

        if (packageName != null) {
            uri = Uri.fromParts("package", packageName, null)
        }

        val oldPolicy = StrictMode.getVmPolicy()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (apkPath.isNullOrBlank()) {
                    Log.w("installPackage", "apk path is null, cannot install via PackageInstaller")
                    appInstallFinished(Activity.RESULT_CANCELED)
                    return
                }

                val apkFile = File(apkPath)

                Log.w("installPackage", "apk exists=${apkFile.exists()} canRead=${apkFile.canRead()} length=${apkFile.length()}")

                uri = Uri.fromFile(apkFile)

                StrictMode.setVmPolicy(
                    StrictMode.VmPolicy.Builder().build()
                )
            }

            Log.w("installPackage", "final uri=$uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                actionInstallPackageQ(packageName, uri, splitApks)
            } else {
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                    putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, packageName)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivityForResult(installIntent, REQUEST_LEGACY_INSTALL_PACKAGE)
            }
        } catch (e: Exception) {
            Log.w("installPackage", e.stackTraceToString())
            appInstallFinished(Activity.RESULT_CANCELED)
        } finally {
            StrictMode.setVmPolicy(oldPolicy)
        }
    }

    private fun actionFinalizeProvision() {
        if (isProfileOwner) {
            finish()
        } else {
            finish()
        }
    }

    // On Android Q, ACTION_INSTALL_PACKAGE has been deprecated.
    // We have to switch to using PackageInstaller for the job, which isn't quite
    // as elegant because now we really need to read the entire apk and write to it
    // Keep this case only for Q for now.
    @Throws(IOException::class)
    private fun actionInstallPackageQ(
        packageName: String?,
        uri: Uri?,
        splitApks: Array<String>?
    ) {
        val pi = packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            if (!packageName.isNullOrBlank()) {
                setAppPackageName(packageName)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                )
            }
        }

        val sessionId = pi.createSession(params)
        val session = pi.openSession(sessionId)

        doInstallPackageQ(uri, splitApks, session) {
            session.setStagingProgress(0.1f)

            val callbackIntent = Intent(this, DummyActivity::class.java).apply {
                action = PACKAGEINSTALLER_CALLBACK
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.makeBasic().apply {
                        setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        )
                    }
                } else {
                    ActivityOptions.makeBasic().apply {
                        setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                }

                PendingIntent.getActivity(
                    this,
                    sessionId,
                    callbackIntent,
                    pendingIntentFlags,
                    options.toBundle()
                )
            } else {
                PendingIntent.getActivity(
                    this,
                    sessionId,
                    callbackIntent,
                    pendingIntentFlags
                )
            }

            session.commit(pendingIntent.intentSender)
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
        if (baseUri == null) {
            Log.w("doInstallPackageQ", "baseUri is null")
            appInstallFinished(Activity.RESULT_CANCELED)
            return
        }
        val uris = ArrayList<Uri>()
        uris.add(baseUri)
        if (!splitApks.isNullOrEmpty()) {
            for (apk in splitApks) {
                uris.add(Uri.fromFile(File(apk)))
            }
        }

        Thread {
            for (uri in uris) {
                try {
                    Log.w("doInstallPackageQ", "copy uri=$uri")

                    contentResolver.openInputStream(uri).use { `is` ->
                        if (`is` == null) {
                            throw IOException("Cannot open input stream for $uri")
                        }
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
                    Log.w("doInstallPackageQ", e.stackTraceToString())
                }
            }
            runOnUiThread(callback)
        }.start()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CONFIRM_INSTALL -> {
                Log.w(
                    "installQueue",
                    "confirm install result=$resultCode; waiting for PackageInstaller final callback"
                )

                // ВАЖНО:
                // Ничего не делаем.
                // После подтверждения PackageInstaller сам пришлёт STATUS_SUCCESS или STATUS_FAILURE.
                return
            }

            REQUEST_LEGACY_INSTALL_PACKAGE -> {
                Log.w("installQueue", "legacy install result=$resultCode")

                // Только для старого Intent.ACTION_INSTALL_PACKAGE.
                installNextFromQueue()
            }
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
        startActivityForResult(intent, REQUEST_LEGACY_INSTALL_PACKAGE)
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
        serviceUtils.bindService(object : ServiceConnection {
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
        const val INSTALL_PACKAGES: String = "com.libsillycrypt.workprofile.INSTALL_PACKAGES"
        const val SYNCHRONIZE_PREFERENCE: String =
            "com.libsillycrypt.workprofile.SYNCHRONIZE_PREFERENCE"
        const val PACKAGEINSTALLER_CALLBACK: String =
            "com.libsillycrypt.workprofile.PACKAGEINSTALLER_CALLBACK"

        const val EXTRA_APPS = "appsData"

        private var hasRequestedPermission: Boolean = false

        private val ACTIONS_ALLOWED_WITHOUT_SIGNATURE_SAME_PROCESS: List<String?> =
            listOf<String?>(
                INSTALL_PACKAGE,
                UNINSTALL_PACKAGE,
                INSTALL_PACKAGES
            )

        private const val REQUEST_PERMISSION_POST_NOTIFICATIONS = 3

        private const val REQUEST_LEGACY_INSTALL_PACKAGE = 1
        private const val REQUEST_CONFIRM_INSTALL = 2

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