package com.dev.libsillycript.android.blockCiphers

import com.dev.libsillycript.core.blockCiphers.BlockCipher
import com.dev.libsillycript.core.blockCiphers.BlockCipherFactory
import com.dev.libsillycript.core.blockCiphers.BlockCipherType

class BlockCipherNativeFactory: BlockCipherFactory {
    override fun getCipher(type: BlockCipherType): BlockCipher {
        return when(type) {
            BlockCipherType.AES -> AesNative()
        }
    }
}