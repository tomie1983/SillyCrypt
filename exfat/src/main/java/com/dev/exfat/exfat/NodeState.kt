package com.dev.exfat.exfat

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal sealed interface NodeId {
    object Root : NodeId

    data class DirectoryEntry(
        val parentDirFirstCluster: Long,
        val primaryEntryOffsetInParentBytes: Long
    ) : NodeId
}

internal data class NodeCoreMetadata(
    val isDirectory: Boolean,
    val attributes: Int,
    val firstCluster: Long,
    val dataLength: Long,
    val validDataLength: Long,
    val readableLength: Long,
    val streamFlags: Int,
    val noFatChain: Boolean
)

internal data class NodeEntrySetLocation(
    val parentDirFirstCluster: Long,
    val parentDirNoFatChain: Boolean,
    val primaryEntryOffsetInParentBytes: Long,
    val streamEntryOffsetInParentBytes: Long?,
    val fileNameEntryOffsetsInParentBytes: LongArray
)

/**
 * Live node state stored in the filesystem registry.
 *
 * Important:
 * - contains no file name and no absolute path
 * - keeps only technical metadata and physical entry-set location
 * - [writeMutex] serializes future write transactions on one file
 */
internal class NodeState(
    val nodeId: NodeId,
    initialCore: NodeCoreMetadata,
    initialEntrySetLocation: NodeEntrySetLocation?
) {
    private val rwLock = ReentrantReadWriteLock()

    private var currentCore: NodeCoreMetadata = initialCore
    private var currentEntrySetLocation: NodeEntrySetLocation? = initialEntrySetLocation

    val writeMutex: Mutex = Mutex()

    fun snapshotCore(): NodeCoreMetadata = rwLock.read { currentCore }

    fun snapshotEntrySetLocation(): NodeEntrySetLocation? = rwLock.read { currentEntrySetLocation }

    fun update(
        newCore: NodeCoreMetadata,
        newEntrySetLocation: NodeEntrySetLocation?
    ) {
        rwLock.write {
            currentCore = newCore
            currentEntrySetLocation = newEntrySetLocation
        }
    }

    fun <T> withReadLock(action: () -> T): T = rwLock.read(action)

    fun <T> withWriteLock(action: () -> T): T = rwLock.write(action)
}