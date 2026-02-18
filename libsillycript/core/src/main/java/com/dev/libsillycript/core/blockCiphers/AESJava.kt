package com.dev.libsillycript.core.blockCiphers

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AESJava(override val blockSize: Int = 16): BlockCipher {

    override fun encryptBlock(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, key: ByteArray) {
        val enc = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        }
        enc.doFinal(src, srcOff, blockSize, dst, dstOff)
    }
    override fun decryptBlock(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, key: ByteArray) {
        val dec = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        }
        dec.doFinal(src, srcOff, blockSize, dst, dstOff)
    }
}