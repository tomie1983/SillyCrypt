package com.dev.exfat.exfat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal suspend inline fun <T> Mutex.locked(crossinline action: suspend () -> T): T {
    return withLock { action() }
}