package com.dev.libsillycript.android.androidCrypto

/**
 * Class for data encryption.
 */
interface EncryptionManager {
    /**
     * Function for data encryption
     */
    fun encrypt(alias: String, bytes: ByteArray): ByteArray

    /**
     * Function for data decryption
     */
    fun decrypt(alias: String, data: ByteArray): ByteArray
}