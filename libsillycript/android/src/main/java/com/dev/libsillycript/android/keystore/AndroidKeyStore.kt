package com.dev.libsillycript.android.keystore

import com.dev.libsillycript.android.androidCrypto.EncryptionManager
import com.dev.libsillycript.core.keyStore.KeyNotInitializedException
import com.dev.libsillycript.core.keyStore.KeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class AndroidKeyStore(
    private val encryptionManager: EncryptionManager,
    private val coroutineScope: CoroutineScope,
    private val timeoutMillis: Flow<Long>,
    ): KeyStore {
    private var key: ByteArray? = null
    private val mu = Mutex()
    private  var clearJob: Job? = null
    private val version = AtomicLong(0L)
    private var decryptedKey: ByteArray? = null

    private fun decryptKey(): ByteArray {
        return key?.let {
            encryptionManager.decrypt(ALIAS,it)
        } ?: throw KeyNotInitializedException()
    }

    override suspend fun getKey(): ByteArray {
        decryptedKey?.let { current ->
            touch() // refresh idle timer
            return current.copyOf()
        }

        // Compute once
        return mu.withLock {
            decryptedKey?.let { cached ->
                touch()
                return@withLock cached.copyOf()
            }
            val computed = decryptKey()
            require(computed.isNotEmpty()) { "Computed ByteArray must not be empty" }
            decryptedKey = computed
            touch()
            computed.copyOf()
        }
    }

    override suspend fun setKey(newKey: ByteArray) {
        val enc = encryptionManager.encrypt(ALIAS, newKey)
        newKey.fill(0) // обнуляем вход

        mu.withLock {
            // (опционально) обнулим старый зашифрованный буфер
            key?.fill(0)
            key = enc

            // кэш расшифровки теперь невалиден
            clearUnlocked()

            // сбросим возможный старый таймер, чтобы он не чистил нам новый кэш «вдогонку»
            version.incrementAndGet()
            clearJob?.cancel()
            clearJob = null
        }
    }

    override suspend fun clearKey() {
        mu.withLock {
            key?.fill(0)
            key = null
            clearUnlocked()
            version.incrementAndGet()
            clearJob?.cancel()
            clearJob = null
        }
    }

    private fun touch() {
        val myV = version.incrementAndGet()
        clearJob?.cancel()
        clearJob = coroutineScope.launch {
            delay(timeoutMillis.first())
            if (version.get() == myV) {
                mu.withLock {
                    // check again under lock to avoid clearing after a concurrent access
                    if (version.get() == myV) {
                        clearUnlocked()
                    }
                }
            }
        }
    }

    private fun clearUnlocked() {
        decryptedKey?.fill(0)
        decryptedKey = null
    }

    companion object {
        private const val ALIAS = "SillyCryptKeyStore"
    }
}