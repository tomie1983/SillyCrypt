package com.dev.libsillycript.core

sealed class VeracryptMode(open val mainData: VeracryptOpeningData, open val index: Int) {
    data class OpenNormal(override val mainData: VeracryptOpeningData, override val index: Int = 0): VeracryptMode(mainData, index)

    data class OpenHidden(override val mainData: VeracryptOpeningData, override val index: Int = HIDDEN_HEADER_DEFAULT_INDEX) : VeracryptMode(mainData, index)

    data class OpenProtected(
        override val mainData: VeracryptOpeningData,
        val hiddenData: VeracryptOpeningData,
        override val index: Int = 0,
        val hiddenIndex: Int = HIDDEN_HEADER_DEFAULT_INDEX
    ) : VeracryptMode(mainData, index)
}

const val HIDDEN_HEADER_DEFAULT_INDEX = 128