package com.dev.libsillycript.core.keyStore

class UnsafeKeyStore: KeyStore {

    private var key: ByteArray? = null

    override suspend fun setKey(newKey: ByteArray) {
        key = ByteArray(newKey.size)
        System.arraycopy(newKey, 0, key,0,newKey.size)
        newKey.fill(Byte.MIN_VALUE)
    }

    override suspend fun getKey(): ByteArray {
        return key?.let {
            val result = ByteArray(it.size)
            System.arraycopy(it, 0, result,0,it.size)
            result
        }?: throw KeyNotInitializedException()
    }

    override suspend fun clearKey() {
        key = null
    }
}