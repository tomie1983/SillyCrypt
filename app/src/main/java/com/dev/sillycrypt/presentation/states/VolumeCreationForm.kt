package com.dev.sillycrypt.presentation.states

import com.dev.libsillycript.core.VeracryptData
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType

data class VolumeCreationForm(
    val size: String = "",
    val password: String = "",
    val kdf: KDFType = KDFType.PBKDF2,
    val cipher: BlockCipherType = BlockCipherType.AES,
    val fsType: FsType = FsType.ExFAT,
    val pim: String = "0",
    val index: String = "0",
    val isHiddenVolume: Boolean = false,
    val isIndexFixed: Boolean = true
    ) {
    fun toVeracryptData(): VeracryptData {
        val mainData = VeracryptOpeningData(
            password = password.toCharArray(),
            kdf = kdf,
            ciphers = listOf(cipher),
            pim = pim.toIntOrNull() ?: 0
        )

        val mainIndex = index.toInt()

        val sizeLong = size.toLong()

        return VeracryptData(sizeLong, mainData, fsType, mainIndex)
    }
}