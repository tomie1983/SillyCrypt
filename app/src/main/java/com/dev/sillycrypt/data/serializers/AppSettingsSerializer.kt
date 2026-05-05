package com.dev.sillycrypt.data.serializers

import com.dev.sillycrypt.domain.entities.AppSettings
import com.libsillycrypt.core.serialization.BaseSerializer

class AppSettingsSerializer :
    BaseSerializer<AppSettings>(
        defaultValue = AppSettings(),
        serializer = AppSettings.serializer()
    )