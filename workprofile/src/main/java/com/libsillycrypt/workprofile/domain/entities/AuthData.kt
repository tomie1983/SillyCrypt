package com.libsillycrypt.workprofile.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class AuthData(
    val authKey: String? = null
)
