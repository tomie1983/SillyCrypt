package com.dev.libsillycript.core.keyStore

interface KeyStore {

    suspend fun setKey(newKey: ByteArray)

    suspend fun getKey(): ByteArray

    suspend fun clearKey()
}