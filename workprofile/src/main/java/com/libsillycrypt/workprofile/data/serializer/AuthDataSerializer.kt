package com.libsillycrypt.workprofile.data.serializer

import com.libsillycrypt.core.serialization.BaseSerializer
import com.libsillycrypt.workprofile.domain.entities.AuthData
import javax.inject.Inject

class AuthDataSerializer @Inject constructor(): BaseSerializer<AuthData>(AuthData(), AuthData.serializer())