package com.dev.libsillycript.core.blockCiphers

interface BlockCipherFactory {
    fun getCipher(type: BlockCipherType): BlockCipher

    fun getCiphers(types: List<BlockCipherType>): List<BlockCipher> = types.map {
        getCipher(it)
    }
}