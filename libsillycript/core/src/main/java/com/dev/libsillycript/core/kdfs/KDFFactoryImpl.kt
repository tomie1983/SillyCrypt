package com.dev.libsillycript.core.kdfs

class KDFFactoryImpl: KDFFactory {
    override fun create(kdfType: KDFType): KDF {
        return when(kdfType) {
            KDFType.PBKDF2 -> PBKDF2()
        }
    }
}