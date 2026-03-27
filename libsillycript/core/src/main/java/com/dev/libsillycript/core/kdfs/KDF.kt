package com.dev.libsillycript.core.kdfs

interface KDF {
    fun derive(password: CharArray, salt: ByteArray, pim: Int): ByteArray
}