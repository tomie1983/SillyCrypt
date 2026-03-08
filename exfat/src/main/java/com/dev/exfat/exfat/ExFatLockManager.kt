package com.dev.exfat.exfat

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized lock manager for filesystem-wide synchronization.
 *
 * Lock hierarchy:
 * 1) node read/write lock (inside NodeState)
 * 2) directory/node lock of parent if metadata entry-set must be updated
 * 3) allocationMutex for FAT / Bitmap / BootRegionChangingMetadata
 *
 * Current stage:
 * - read-only operations actively use node read locks
 * - write path is not implemented yet, but allocationMutex is introduced now
 *   so future mutating operations have a stable synchronization primitive
 */
internal class ExFatLockManager {
    /**
     * Global lock for allocator state:
     * - FAT
     * - Allocation Bitmap
     * - Boot region changing metadata
     * - nextAllocHintCluster
     */
    val allocationMutex: Mutex = Mutex()

    /**
     * Protects registry/cache structures inside ExFATFS.
     */
    val registryMutex: Mutex = Mutex()

    /**
     * Optional per-node auxiliary mutexes for future operations that need an extra
     * coroutine-friendly primitive keyed by NodeId.
     *
     * Right now NodeState itself already contains the main RW lock, so this map is
     * mainly future-proofing and not required by current read paths.
     */
    private val nodeMutexes = ConcurrentHashMap<NodeId, Mutex>()

    fun nodeMutex(nodeId: NodeId): Mutex = nodeMutexes.computeIfAbsent(nodeId) { Mutex() }
}