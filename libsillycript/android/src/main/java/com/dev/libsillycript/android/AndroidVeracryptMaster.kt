package com.dev.libsillycript.android

import com.dev.libsillycript.android.blockCiphers.BlockCipherNativeFactory
import com.dev.libsillycript.core.VeraCryptMaster
import com.dev.libsillycript.android.keystore.AndroidKeyStoreFactory
import kotlinx.coroutines.flow.Flow

class AndroidVeracryptMaster(
    timeoutFlow: Flow<Long>
): VeraCryptMaster(
    keyStoreFactory = AndroidKeyStoreFactory(timeoutFlow),
    blockCipherFactory = BlockCipherNativeFactory(),
)