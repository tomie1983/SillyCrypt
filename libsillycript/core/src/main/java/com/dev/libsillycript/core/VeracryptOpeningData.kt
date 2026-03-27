package com.dev.libsillycript.core

import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.kdfs.KDFType

data class VeracryptOpeningData(
    val password: CharArray,
    val kdf: KDFType,
    val ciphers: List<BlockCipherType>,
    val pim: Int = 0,
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VeracryptOpeningData

        if (pim != other.pim) return false
        if (!password.contentEquals(other.password)) return false
        if (ciphers != other.ciphers) return false
        if (kdf != other.kdf) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pim
        result = 31 * result + password.contentHashCode()
        result = 31 * result + ciphers.hashCode()
        result = 31 * result + kdf.hashCode()
        return result
    }


}