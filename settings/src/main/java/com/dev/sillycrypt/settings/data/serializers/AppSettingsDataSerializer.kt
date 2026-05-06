package com.dev.sillycrypt.settings.data.serializers

import com.dev.sillycrypt.settings.data.entities.AppSettingsData
import com.libsillycrypt.core.serialization.BaseSerializer
import javax.inject.Inject

class AppSettingsDataSerializer @Inject constructor(): BaseSerializer<AppSettingsData>(AppSettingsData(),
    AppSettingsData.serializer()
)