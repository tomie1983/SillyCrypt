package com.libsillycrypt.workprofile.data.utils

import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import com.libsillycrypt.workprofile.domain.entities.AuthData
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Date
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationUtility @Inject constructor(
    private val dataStore: DataStore<AuthData>
){
    suspend fun signIntent(intent: Intent) {
        var key: String? = dataStore.data.first().authKey
        Log.w("keyStoredsignIntent", key.toString())
        if (key == null) {
            // Generate the key if we don't have one yet
            try {
                val keyGen = KeyGenerator.getInstance("HmacSHA256")
                keyGen.init(256)
                key = bytesToHex(keyGen.generateKey().encoded)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("WTF?")
            }

            dataStore.updateData { AuthData(key) }
            Log.w("keyStoredNewKey", key)
            // If this is the first time, we just send the key to the other side
            intent.putExtra(AUTH_KEY, key)
        } else {
            val timestamp = Date().time
            intent.putExtra(TIMESTAMP, timestamp)
            intent.putExtra(SIGNATURE, sign(key, timestamp))
        }
    }

    suspend fun checkIntent(intent: Intent): Boolean {
        val key: String? = dataStore.data.first().authKey
        Log.w("keyStored",key.toString())
        Log.w("keyStoredAuth", intent.getStringExtra(AUTH_KEY)?:"")
        Log.w("keyStoredSign",intent.getStringExtra(SIGNATURE)?:"")
        if (key == null) {
            // If we haven't got a key yet, we just take the key sent by the other side
            // If not, NEVER receive any key because it can be fake.
            // We only trust the first key we receive
            if (intent.hasExtra(AUTH_KEY)) {
                dataStore.updateData { AuthData(intent.getStringExtra(AUTH_KEY)) }
                return true
            } else {
                // We haven't got a key, and we can't check if it is true or not.
                return false
            }
        } else {
            Log.w("keyStoredSigned",sign(key,  intent.getLongExtra(TIMESTAMP, 0)))
            Log.w("keyStoredSignature",intent.getStringExtra(SIGNATURE).toString())
            val timestamp = Date().time
            val intentTimestamp = intent.getLongExtra(TIMESTAMP, 0)
            return timestamp - intentTimestamp < 30 * 1000 &&
                    sign(key, intentTimestamp) == intent.getStringExtra(SIGNATURE)
        }
    }

    suspend fun reset() {
        dataStore.updateData { AuthData(null) }
    }

    private fun sign(hexKey: String, timestamp: Long): String {
        try {
            val keySpec = SecretKeySpec(hexStringToByteArray(hexKey), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)
            return bytesToHex(mac.doFinal(longToBytes(timestamp)))
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("WTF?")
        } catch (e: InvalidKeyException) {
            throw RuntimeException("WTF?")
        }
    }

    val hexArray: CharArray = "0123456789ABCDEF".toCharArray()
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }


    private fun hexStringToByteArray(s: String): ByteArray? {
        try {
            val len = s.length
            if (len > 1) {
                val data = ByteArray(len / 2)
                var i = 0
                while (i < len) {
                    data[i / 2] = ((s[i].digitToIntOrNull(16) ?: (-1 shl 4)) + s[i + 1]
                        .digitToInt(16)).toByte()
                    i += 2
                }
                return data
            } else {
                return null
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun longToBytes(x: Long): ByteArray {
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
        buffer.putLong(x)
        return buffer.array()
    }

    companion object {
        private const val AUTH_KEY = "auth_key"
        private const val TIMESTAMP = "timestamp"
        private const val SIGNATURE = "signature"
    }
}