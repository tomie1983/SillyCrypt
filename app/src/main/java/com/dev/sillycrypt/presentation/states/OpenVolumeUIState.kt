package com.dev.sillycrypt.presentation.states

import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.libsillycript.core.HIDDEN_HEADER_DEFAULT_INDEX
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed class OpenVolumeUIState{
    data class Initial(
        val data: ImmutableList<VeracryptVolumeData> = persistentListOf<VeracryptVolumeData>(),
    ): OpenVolumeUIState()
    data class OpenVolumeFormState(
        val name: String,
        val password: String = "",
        val kdf: KDFType = KDFType.PBKDF2,
        val cipher: BlockCipherType = BlockCipherType.AES,
        val fsType: FsType = FsType.ExFAT,
        val pim: String = "0",
        val index: String = "0",

        val isHiddenVolume: Boolean = false,
        val protectHiddenVolume: Boolean = false,

        val hiddenPassword: String = "",
        val hiddenKdf: KDFType = KDFType.PBKDF2,
        val hiddenCipher: BlockCipherType = BlockCipherType.AES,
        val hiddenPim: String = "0",
        val hiddenIndex: String = HIDDEN_HEADER_DEFAULT_INDEX.toString(),
        val confirmDialog: ConfirmDialog? = null,
        val loading: Boolean = false
    ): OpenVolumeUIState() {
        fun toVeracryptMode(): VeracryptMode {
            val mainData = VeracryptOpeningData(
                password = password.toCharArray(),
                kdf = kdf,
                ciphers = listOf(cipher),
                pim = pim.toIntOrNull() ?: 0
            )

            val mainIndex = index.toIntOrNull()
                ?: if (isHiddenVolume) HIDDEN_HEADER_DEFAULT_INDEX else 0

            return when {
                isHiddenVolume -> {
                    VeracryptMode.OpenHidden(
                        mainData = mainData,
                        index = mainIndex
                    )
                }

                protectHiddenVolume -> {
                    require(hiddenPassword.isNotBlank()) { "Введите пароль скрытого тома" }

                    val hiddenData = VeracryptOpeningData(
                        password = hiddenPassword.toCharArray(),
                        kdf = hiddenKdf,
                        ciphers = listOf(hiddenCipher),
                        pim = hiddenPim.toIntOrNull() ?: 0
                    )

                    VeracryptMode.OpenProtected(
                        mainData = mainData,
                        hiddenData = hiddenData,
                        index = mainIndex,
                        hiddenIndex = hiddenIndex.toIntOrNull() ?: HIDDEN_HEADER_DEFAULT_INDEX
                    )
                }

                else -> {
                    VeracryptMode.OpenNormal(
                        mainData = mainData,
                        index = mainIndex
                    )
                }
            }
        }
    }
}