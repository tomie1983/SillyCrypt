package com.dev.libsillycript.core.keyStore

interface KeyStoreFactory {
    fun get(): KeyStore
}