package com.dev.libsillycript.core.kdfs

interface KDFFactory {
    fun create(kdfType: KDFType): KDF
}