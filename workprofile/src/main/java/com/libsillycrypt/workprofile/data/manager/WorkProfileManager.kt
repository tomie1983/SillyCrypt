package com.libsillycrypt.workprofile.data.manager

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Build.VERSION
import android.os.UserManager
import com.libsillycrypt.workprofile.data.utils.AuthenticationUtility
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import com.libsillycrypt.workprofile.presentation.receivers.SillyCryptDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authenticationUtility: AuthenticationUtility
) {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    fun adminComponent(): ComponentName {
        return ComponentName(context, SillyCryptDeviceAdminReceiver::class.java)
    }

    fun isProfileOwner(): Boolean {
        return dpm.isProfileOwnerApp(context.packageName)
    }

    fun setProfileName(name: String) {
        dpm.setProfileName(adminComponent(), name)
    }

    fun setCameraDisabled(disabled: Boolean) {
        dpm.setCameraDisabled(adminComponent(), disabled)
    }

    fun setScreenCaptureDisabled(disabled: Boolean) {
        dpm.setScreenCaptureDisabled(adminComponent(), disabled)
    }

    fun applySettings(
        settings: WorkProfileSettings
    ) {
        if (!isProfileOwner()) {
            return
        }

        setProfileName(settings.name)

        applySecurityPolicies(
            settings = settings
        )

        setAllowClipboardSharing(
            value = settings.allowClipBoardSharing
        )

        installAppsIntoWorkProfile(
            apps = settings.appsList
        )
    }

    private fun applySecurityPolicies(
        settings: WorkProfileSettings
    ) {

        setCameraDisabled(settings.isCameraDisabled)
        setScreenCaptureDisabled(settings.isScreenCaptureDisabled)
    }

    private fun installAppsIntoWorkProfile(
        apps: List<String>
    ) {
        apps.forEach { packageName ->
            installExistingPackageSafely(
                packageName = packageName
            )
        }
    }

    fun installExistingPackageSafely(
        packageName: String
    ) {
        val admin = adminComponent()
        try {
            dpm.installExistingPackage(
                admin,
                packageName
            )
        } catch (_: Throwable) {
            try {
                dpm.enableSystemApp(
                    admin,
                    packageName
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun removeAppFromWorkProfile(
        packageName: String
    ) {
    }

    fun setAllowClipboardSharing(value: Boolean) {
        val admin = adminComponent()
        if (value) {
            dpm.clearUserRestriction(
                admin,
                UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE
            )
        } else {
            dpm.addUserRestriction(
                admin,
                UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE
            )
        }
    }

    suspend fun startProfileCreation() {
        require(dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            "Work profile provision is not allowed"
        }
        authenticationUtility.reset()
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        intent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            adminComponent()
        )
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun deleteProfile(): Boolean {
        var flags = 0
        if (VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            flags = flags.or(DevicePolicyManager.WIPE_SILENTLY)
        try {
            dpm.wipeData(flags)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    companion object {

    }
}