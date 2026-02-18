package com.dev.libsillycript.android.blockCiphers

import com.dev.libsillycript.core.blockCiphers.BlockCipher

class AesNative: BlockCipher {

    init {
        System.loadLibrary("cryptaes")
    }

    external override fun encryptBlock(
        src: ByteArray,
        srcOff: Int,
        dst: ByteArray,
        dstOff: Int,
        key: ByteArray
    )

    external override fun decryptBlock(
        src: ByteArray,
        srcOff: Int,
        dst: ByteArray,
        dstOff: Int,
        key: ByteArray
    )
}