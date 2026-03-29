package com.dev.sillycrypt.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(val timeout: Long = 100)