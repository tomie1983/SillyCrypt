package com.dev.libsillycript.core.kdfs

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PBKDF2: KDF {
    override fun derive(password: CharArray, salt: ByteArray, pim: Int): ByteArray {
        val kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512", "BC")
        val iterations = if (pim == 0) {
            ITERATIONS_SHA512
        } else {
            INIT_ITERATIONS_SHA512 + pim * 1000
        }
        val sk = kf.generateSecret(PBEKeySpec(password, salt, iterations, PBKDF2_KEY_SIZE))
        //clear password
        password.fill(Char(0))
        return sk.encoded
    }

    companion object {
        private const val PBKDF2_KEY_SIZE = 512
        private const val ITERATIONS_SHA512 = 500000
        private const val INIT_ITERATIONS_SHA512 = 15000
    }
}