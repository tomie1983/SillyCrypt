package com.dev.libsillycript.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class SharedSectorCache(
    maxCachedSectors: Int = 2048
) {
    private val cacheMutex = Mutex()

    /**
     * sectorIndex -> ciphertext sector bytes
     */
    private val sectorCache =
        object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>): Boolean {
                return size > maxCachedSectors
            }
        }

    /**
     * Separate mutex per sector.
     * Used to prohibit concurrent access to the same cached sector.
     */
    private val sectorMutexes = ConcurrentHashMap<Long, Mutex>()

    private fun mutexForSector(sectorIndex: Long): Mutex {
        return sectorMutexes.computeIfAbsent(sectorIndex) { Mutex() }
    }

    /**
     * Executes [block] under per-sector mutex.
     * Use this when you want to coordinate access to a particular sector.
     */
    suspend fun <T> withSectorLock(sectorIndex: Long, block: suspend () -> T): T {
        return mutexForSector(sectorIndex).withLock {
            block()
        }
    }

    /**
     * Returns a COPY of cached ciphertext sector, or null if absent.
     */
    suspend fun getSector(sectorIndex: Long): ByteArray? {
        return cacheMutex.withLock {
            sectorCache[sectorIndex]?.copyOf()
        }
    }

    /**
     * Caches ciphertext sector bytes.
     * Stores a copy to avoid external mutation of cached data.
     */
    suspend fun putSector(sectorIndex: Long, data: ByteArray) {
        cacheMutex.withLock {
            sectorCache[sectorIndex] = data.copyOf()
        }
    }

    /**
     * Removes cached sector.
     */
    suspend fun invalidateSector(sectorIndex: Long) {
        cacheMutex.withLock {
            sectorCache.remove(sectorIndex)
        }
    }

    /**
     * Removes all cached sectors.
     */
    suspend fun clear() {
        cacheMutex.withLock {
            sectorCache.clear()
        }
    }

    /**
     * Optional helper: checks presence only.
     */
    suspend fun containsSector(sectorIndex: Long): Boolean {
        return cacheMutex.withLock {
            sectorCache.containsKey(sectorIndex)
        }
    }
}