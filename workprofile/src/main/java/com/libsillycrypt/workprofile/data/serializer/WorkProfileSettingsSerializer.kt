package com.libsillycrypt.workprofile.data.serializer

import com.libsillycrypt.core.serialization.BaseSerializer
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings

class WorkProfileSettingsSerializer: BaseSerializer<WorkProfileSettings>(
    defaultValue = WorkProfileSettings(),
    serializer = WorkProfileSettings.serializer()
)