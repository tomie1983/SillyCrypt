package com.dev.sillycrypt

import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.dev.sillycrypt.presentation.navigation.AppNavHost
import com.dev.sillycrypt.presentation.viewmodels.MainActivityVM
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.data.utils.WorkProfileUtils
import com.libsillycrypt.workprofile.presentation.activities.DummyActivity
import com.libsillycrypt.workprofile.presentation.contracts.ProfileProvisionContract
import com.libsillycrypt.workprofile.services.IStartActivityProxy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainActivityVM>()

    @Inject
    lateinit var workProfileUtils: WorkProfileUtils

    private val mTryStartWorkService = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult -> tryStartWorkServiceCb(result) }

    private val mBindWorkService = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult -> bindWorkServiceCb(result) }

    private val mProvisionProfile = registerForActivityResult(
        ProfileProvisionContract()
    ) { result: Boolean? -> setupProfileCb(result) }

    @Inject
    lateinit var serviceUtils: ServiceUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeProfileStatus()
        setContent {
            MaterialTheme {
                AppNavHost(
                    viewModel.isWorkProfileAvailable.collectAsState(),
                    ::finish,
                    ::createProfile)
            }
        }
    }

    private fun observeProfileStatus() {
        lifecycleScope.launch {
            viewModel.isWorkProfileAvailable.collect {
                if (it) {
                    // Bind to the service provided by this app in main user
                    // The service in main profile doesn't need to be foreground
                    // because this activity will hold a ServiceConnection to the service
                    serviceUtils.bindMainService(::tryStartWorkService)
                }
            }
        }
    }

    private fun createProfile() {
        mProvisionProfile.launch(null)
    }

    private fun tryStartWorkServiceCb(result: ActivityResult) {
        Log.w("tryStartWorkServiceCb", result.toString())
        if (result.resultCode == RESULT_OK) {
            // RESULT_OK is from DummyActivity. The work profile is enabled!
            bindWorkService()
        } else {
            // In this case, the user has been presented with a prompt
            // to enable work mode, but we have no means to distinguish
            // "ok" and "cancel", so the only way is to tell the user
            // to start again.
            Log.w("tryStartWorkServiceCb", "finish")
            finish()
        }
    }

    private fun setupProfileCb(result: Boolean?) {
        if (result == true) {
            Log.w("refreshWorkProfileStatus", "setupProfileCb")
            viewModel.refreshWorkProfileStatus()
            viewModel.clearKey()
        }
    }

    private fun bindWorkService() {
        // Bind to the ShelterService in work profile
        val intent = Intent(DummyActivity.START_SERVICE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        lifecycleScope.launch {
            workProfileUtils.transferIntentToProfile(this@MainActivity, intent)
            mBindWorkService.launch(intent)
        }
    }

    private fun tryStartWorkService() {
        // Send a dummy intent to the work profile first
        // to determine if work mode is enabled and we CAN start something in that profile.
        // If work mode is disabled when starting this app, we will receive RESULT_CANCELED
        // in the activity result, and we can then prompt the user to enable it
        val intent = Intent(DummyActivity.TRY_START_SERVICE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        lifecycleScope.launch {
            try {
                workProfileUtils.transferIntentToProfile(this@MainActivity, intent)
            } catch (e: IllegalStateException) {
                Log.w("IllegalStateException", "finish")
                finish()
                return@launch
            }
            Log.w("tryStartWorkServiceCb", intent.toString())
            mTryStartWorkService.launch(intent)
        }
    }

    private fun bindWorkServiceCb(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val extra: Bundle? = result.data?.getBundleExtra("extra")
            val binder = extra!!.getBinder("service")
            serviceUtils.bindWorkService(binder)
            registerStartActivityProxies()
        }
    }

    private fun registerStartActivityProxies() {
        try {
            serviceUtils.mainServiceSetStartActivityProxy(object : IStartActivityProxy.Stub() {
                @Throws(RemoteException::class)
                override fun startActivity(intent: Intent?) {
                    this@MainActivity.startActivity(intent)
                }
            })

            serviceUtils.workServiceSetStartActivityProxy(object : IStartActivityProxy.Stub() {
                @Throws(RemoteException::class)
                override fun startActivity(intent: Intent) {
                    // Using the full intent may cause the package manager to
                    // fail to find the DummyActivity inside profile.
                    // Instead we try to use an empty intent with only the action
                    // and then extract the correct component name
                    val dummyIntent = Intent(intent.action)
                    workProfileUtils.transferIntentToProfileUnsigned(this@MainActivity, dummyIntent)
                    intent.setComponent(dummyIntent.component)
                    this@MainActivity.startActivity(intent)
                }
            })
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

}
