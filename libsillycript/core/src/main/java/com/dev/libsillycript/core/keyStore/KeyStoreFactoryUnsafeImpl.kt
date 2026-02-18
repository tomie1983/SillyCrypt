package com.dev.libsillycript.core.keyStore

class KeyStoreFactoryUnsafeImpl: KeyStoreFactory {
    override fun get(): KeyStore {
        return UnsafeKeyStore()
    }
}