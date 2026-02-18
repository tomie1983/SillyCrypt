package com.dev.libsillycript.android.keystore


import com.dev.libsillycript.android.androidCrypto.EncryptionManagerImpl
import com.dev.libsillycript.core.keyStore.KeyStore
import com.dev.libsillycript.core.keyStore.KeyStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class AndroidKeyStoreFactory(private val paramsFlow: Flow<Long>): KeyStoreFactory {
    override fun get(): KeyStore {
        return AndroidKeyStore(
            EncryptionManagerImpl(),
            CoroutineScope(Dispatchers.IO),
            paramsFlow
        )
    }
}