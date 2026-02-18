package com.dev.libsillycript.core.blockCiphers

class BlockCipherFactoryImpl: BlockCipherFactory {
    override fun getCipher(type: BlockCipherType): BlockCipher {
        return when (type) {
            BlockCipherType.AES -> AESJava()
        }
    }
}