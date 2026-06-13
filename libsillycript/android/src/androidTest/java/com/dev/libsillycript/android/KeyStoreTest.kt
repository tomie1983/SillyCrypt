package com.dev.libsillycript.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import com.dev.libsillycript.android.androidCrypto.EncryptionManager
import com.dev.libsillycript.android.androidCrypto.EncryptionManagerImpl
import com.dev.libsillycript.core.keyStore.KeyNotInitializedException
import com.dev.libsillycript.android.keystore.AndroidKeyStore


@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidKeyStoreTest {

private class CountingEncryptionManager(
    private val delegate: EncryptionManager = EncryptionManagerImpl()
) : EncryptionManager {

    var encryptCalls = 0
        private set

    var decryptCalls = 0
        private set

    val encryptAliases = mutableListOf<String>()
    val decryptAliases = mutableListOf<String>()

    override fun encrypt(alias: String, bytes: ByteArray): ByteArray {
        encryptCalls++
        encryptAliases += alias
        return delegate.encrypt(alias, bytes)
    }

    override fun decrypt(alias: String, data: ByteArray): ByteArray {
        decryptCalls++
        decryptAliases += alias
        return delegate.decrypt(alias, data)
    }
}

@Before
fun setUp() {
    deleteAndroidKeyStoreEntry()
}

@After
fun tearDown() {
    deleteAndroidKeyStoreEntry()
}

private fun deleteAndroidKeyStoreEntry() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    if (keyStore.containsAlias(TEST_ALIAS)) {
        keyStore.deleteEntry(TEST_ALIAS)
    }
}

@Test
fun getKey_beforeSetKey() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    try {
        keyStore.getKey()
        throw AssertionError("Expected KeyNotInitializedException")
    } catch (_: KeyNotInitializedException) {
    }

    assertEquals(0, encryptionManager.encryptCalls)
    assertEquals(0, encryptionManager.decryptCalls)
}

@Test
fun setKey_encryptsKey_andZerosInput() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    val input = byteArrayOf(1, 2, 3, 4)
    val expected = input.copyOf()

    keyStore.setKey(input)

    assertEquals(1, encryptionManager.encryptCalls)
    assertEquals(listOf(TEST_ALIAS), encryptionManager.encryptAliases)

    assertTrue(
        "setKey() must zero input array",
        input.all { it == 0.toByte() }
    )

    val actual = keyStore.getKey()

    assertArrayEquals(expected, actual)
    assertEquals(1, encryptionManager.decryptCalls)
    assertEquals(listOf(TEST_ALIAS), encryptionManager.decryptAliases)
}

@Test
fun getKey_returnsCopy() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf(10, 20, 30, 40))

    val first = keyStore.getKey()
    first[0] = 99

    val second = keyStore.getKey()

    assertNotSame(first, second)
    assertArrayEquals(byteArrayOf(10, 20, 30, 40), second)

    assertEquals(1, encryptionManager.decryptCalls)
}

@Test
fun getKey_usesCacheUntilTimeout() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf(1, 2, 3))

    val first = keyStore.getKey()
    assertArrayEquals(byteArrayOf(1, 2, 3), first)
    assertEquals(1, encryptionManager.decryptCalls)

    val second = keyStore.getKey()
    assertArrayEquals(byteArrayOf(1, 2, 3), second)

    assertEquals(1, encryptionManager.decryptCalls)
}

@Test
fun getKey_afterTimeout_decryptsAgain() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(500L)
    )

    keyStore.setKey(byteArrayOf(11, 22, 33))

    val first = keyStore.getKey()
    assertArrayEquals(byteArrayOf(11, 22, 33), first)
    assertEquals(1, encryptionManager.decryptCalls)

    advanceTimeBy(500)
    runCurrent()

    val second = keyStore.getKey()
    assertArrayEquals(byteArrayOf(11, 22, 33), second)

    assertEquals(2, encryptionManager.decryptCalls)
}

@Test
fun getKey_refreshesIdleTimer() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf(7, 8, 9))

    keyStore.getKey()
    assertEquals(1, encryptionManager.decryptCalls)

    advanceTimeBy(900)
    runCurrent()

    keyStore.getKey()
    assertEquals(1, encryptionManager.decryptCalls)

    advanceTimeBy(200)
    runCurrent()

    keyStore.getKey()

    assertEquals(1, encryptionManager.decryptCalls)

    advanceTimeBy(1_000)
    runCurrent()

    keyStore.getKey()

    assertEquals(2, encryptionManager.decryptCalls)
}

@Test
fun clearKey_removesBothKeys() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf(1, 2, 3, 4))

    val beforeClear = keyStore.getKey()
    assertArrayEquals(byteArrayOf(1, 2, 3, 4), beforeClear)

    keyStore.clearKey()

    try {
        keyStore.getKey()
        throw AssertionError("Expected KeyNotInitializedException")
    } catch (_: KeyNotInitializedException) {
    }
}

@Test
fun setKey_replacesPreviousKey() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf(1, 1, 1))
    assertArrayEquals(byteArrayOf(1, 1, 1), keyStore.getKey())
    assertEquals(1, encryptionManager.decryptCalls)

    keyStore.setKey(byteArrayOf(2, 2, 2))

    val actual = keyStore.getKey()

    assertArrayEquals(byteArrayOf(2, 2, 2), actual)

    assertEquals(2, encryptionManager.decryptCalls)
}

@Test
fun setKey_withEmptyKey_throwsException() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf())

    try {
        keyStore.getKey()
        throw AssertionError("Expected IllegalArgumentException")
    } catch (e: IllegalArgumentException) {
        assertEquals("Computed ByteArray must not be empty", e.message)
    }
}

@Test
fun clearKey_cancelsPendingTimeoutJob() = runTest {
    val encryptionManager = CountingEncryptionManager()
    val keyStore = AndroidKeyStore(
        encryptionManager = encryptionManager,
        coroutineScope = this,
        timeoutMillis = MutableStateFlow(1_000L)
    )

    keyStore.setKey(byteArrayOf(5, 6, 7))
    keyStore.getKey()

    assertEquals(1, encryptionManager.decryptCalls)

    keyStore.clearKey()

    advanceTimeBy(1_000)
    runCurrent()

    try {
        keyStore.getKey()
        throw AssertionError("Expected KeyNotInitializedException")
    } catch (_: KeyNotInitializedException) {
    }

    assertEquals(1, encryptionManager.decryptCalls)
}

companion object {
    private const val TEST_ALIAS = "SillyCryptKeyStore"
}
}