package com.dev.libsillycript.core.blockCiphers

interface BlockCipher {
    val blockSize: Int get() = 16
    fun encryptBlock(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, key: ByteArray)
    fun decryptBlock(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, key: ByteArray)
}