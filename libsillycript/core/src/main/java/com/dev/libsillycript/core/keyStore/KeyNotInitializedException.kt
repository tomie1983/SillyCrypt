package com.dev.libsillycript.core.keyStore

import java.lang.RuntimeException

class KeyNotInitializedException: RuntimeException("Encryption key was not initialized")