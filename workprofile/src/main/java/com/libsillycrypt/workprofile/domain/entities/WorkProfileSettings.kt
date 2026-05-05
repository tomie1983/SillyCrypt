package com.libsillycrypt.workprofile.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class WorkProfileSettings(
    val name: String = "work profile",
    val provisioned: Boolean = false,
    val isCameraDisabled: Boolean = false,
    val isScreenCaptureDisabled: Boolean = false,
    val allowClipBoardSharing: Boolean = true,
    val appsList: List<String> = listOf()
)