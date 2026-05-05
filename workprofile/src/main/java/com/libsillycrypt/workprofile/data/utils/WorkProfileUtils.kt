package com.libsillycrypt.workprofile.data.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.content.pm.ResolveInfo
import android.os.UserManager
import android.util.Log
import com.libsillycrypt.workprofile.presentation.activities.DummyActivity
import com.libsillycrypt.workprofile.presentation.receivers.SillyCryptDeviceAdminReceiver
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkProfileUtils @Inject constructor(
    private val authenticationUtility: AuthenticationUtility
) {
    // Determine if the work profile is already available
    // If so, return true and set all the corresponding flags to true
    // This is for scenarios where the asynchronous part of the
    // setup process might be finished before the synchronous part
    fun isWorkProfileAvailable(context: Context): Boolean {
        val intent = Intent(DummyActivity.TRY_START_SERVICE)
        try {
            // DO NOT sign this request, because this won't be actually sent to work profile
            // If this is signed, and is the first request to be signed,
            // then the other side would never receive the auth_key
            transferIntentToProfileUnsigned(context, intent)
            Log.w("refreshWorkProfileStatus","true")
            return true
        } catch (e: IllegalStateException) {
            // If any exception is thrown, this means that the profile is not available
            Log.w("refreshWorkProfileStatus","false")
            return false
        }
    }

    // Pipe an InputStream to OutputStream
    @Throws(IOException::class)
    fun pipe(`is`: InputStream, os: OutputStream) {
        var n: Int
        val buffer = ByteArray(65536)
        while ((`is`.read(buffer).also { n = it }) > -1) {
            os.write(buffer, 0, n)
        }
    }

    suspend fun transferIntentToProfile(context: Context, intent: Intent) {
        transferIntentToProfileUnsigned(context, intent)
        // Add signature
        authenticationUtility.signIntent(intent)
    }


    fun transferIntentToProfileUnsigned(context: Context, intent: Intent) {
        val pm = context.packageManager
        val info = pm.queryIntentActivities(intent, 0)
        Log.w("tryStartWorkServiceCb", info.toString())
        val i = info.stream()
            .filter { r: ResolveInfo? -> r?.activityInfo?.packageName != context.packageName }
            .findFirst()
        if (i.isPresent) {
            intent.setComponent(
                ComponentName(
                    i.get().activityInfo.packageName,
                    i.get().activityInfo.name
                )
            )
        } else {
            throw java.lang.IllegalStateException("Cannot find an intent in other profile")
        }
    }

    // Enforce policies and configurations in the work profile
    fun enforceWorkProfilePolicies(context: Context) {
        val manager: DevicePolicyManager =
            context.getSystemService<DevicePolicyManager?>(DevicePolicyManager::class.java)!!
        val adminComponent: ComponentName =
            ComponentName(context.applicationContext, SillyCryptDeviceAdminReceiver::class.java)

        // Clear everything first to ensure our policies are set properly
        manager.clearCrossProfileIntentFilters(adminComponent)

        // Allow cross-profile intents for START_SERVICE
        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.START_SERVICE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        )

        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.TRY_START_SERVICE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        )

        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.FINALIZE_PROVISION),
            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        )

        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.SYNCHRONIZE_PREFERENCE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        )

        // Needed by ShelterService and has to be proxied by the MainActivity in main profile
        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.INSTALL_PACKAGE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        )

        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.UNINSTALL_PACKAGE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        )
        manager.addCrossProfileIntentFilter(
            adminComponent,
            IntentFilter(DummyActivity.DELETE_PROFILE),
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
        )

        // Allow ACTION_SEND and ACTION_SEND_MULTIPLE to cross from managed to parent
        val actionSendFilter = IntentFilter()
        actionSendFilter.addAction(Intent.ACTION_SEND)
        actionSendFilter.addAction(Intent.ACTION_SEND_MULTIPLE)
        try {
            actionSendFilter.addDataType("*/*")
        } catch (ignored: MalformedMimeTypeException) {
            // WTF?
        }
        actionSendFilter.addCategory(Intent.CATEGORY_DEFAULT)
        manager.addCrossProfileIntentFilter(
            adminComponent,
            actionSendFilter,
            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        )


        // Browser intents are allowed from work profile to parent
        val browsableIntentFilter = IntentFilter(Intent.ACTION_VIEW)
        browsableIntentFilter.addCategory(Intent.CATEGORY_BROWSABLE)
        browsableIntentFilter.addDataScheme("http")
        browsableIntentFilter.addDataScheme("https")
        manager.addCrossProfileIntentFilter(
            adminComponent,
            browsableIntentFilter,
            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        )
        val browsableDefaultIntentFilter = IntentFilter(Intent.ACTION_VIEW)
        browsableDefaultIntentFilter.addCategory(Intent.CATEGORY_BROWSABLE)
        browsableDefaultIntentFilter.addCategory(Intent.CATEGORY_DEFAULT)
        browsableDefaultIntentFilter.addDataScheme("http")
        browsableDefaultIntentFilter.addDataScheme("https")
        manager.addCrossProfileIntentFilter(
            adminComponent,
            browsableDefaultIntentFilter,
            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
        )
        manager.setProfileEnabled(adminComponent)
    }

    fun enforceUserRestrictions(context: Context) {
        val manager: DevicePolicyManager =
            context.getSystemService<DevicePolicyManager?>(DevicePolicyManager::class.java)!!
        val adminComponent =
            ComponentName(context.applicationContext, SillyCryptDeviceAdminReceiver::class.java)
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
        manager.addUserRestriction(adminComponent, UserManager.ALLOW_PARENT_PROFILE_APP_LINKING)
    }
}