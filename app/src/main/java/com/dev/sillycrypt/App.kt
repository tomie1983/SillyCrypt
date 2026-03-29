package com.dev.sillycrypt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


@HiltAndroidApp
class App: Application() {
    override fun onCreate() {
        super.onCreate()
        Security.addProvider(BouncyCastleProvider())

    }
}