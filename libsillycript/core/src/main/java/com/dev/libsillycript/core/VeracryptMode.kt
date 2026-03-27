package com.dev.libsillycript.core

sealed class VeracryptMode(open val mainData: VeracryptOpeningData) {
    data class OpenNormal(override val mainData: VeracryptOpeningData): VeracryptMode(mainData)

    data class OpenHidden(override val mainData: VeracryptOpeningData) : VeracryptMode(mainData)

    data class OpenProtected(override val mainData: VeracryptOpeningData, val hiddenData: VeracryptOpeningData) :
        VeracryptMode(mainData)
}