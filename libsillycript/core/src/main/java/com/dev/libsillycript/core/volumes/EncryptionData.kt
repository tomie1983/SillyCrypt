package com.dev.libsillycript.core.volumes

import com.dev.libsillycript.core.xts.XTSNew

data class EncryptionData(val xts: XTSNew, val offset: Long, val size: Long, val sectorSize: Int)