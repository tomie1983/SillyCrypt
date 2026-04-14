package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessDataFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EXFatVolumesManager {
    private val map = ConcurrentHashMap<String, ExFATFS>()
    private val _activeVolumes: MutableStateFlow<List<VeracryptVolumeData>> = MutableStateFlow(listOf())
    val activeVolumes = _activeVolumes.asStateFlow()

    private fun updateActiveVolumes() {
        _activeVolumes.update {
            map.entries.map { VeracryptVolumeData(it.value.name, it.key) }
        }
    }

    fun register(volumeFactory: RandomAccessDataFactory, name: String): String {
        var id = UUID.randomUUID().toString()
        while (map.contains(id)) {
            id = UUID.randomUUID().toString()
        }
        map[id] = ExFATFS(volumeFactory, name)
        updateActiveVolumes()
        return id
    }

    fun get(id: String): ExFATFS =
        map[id] ?: throw IllegalStateException("Volume not mounted: $id")

    suspend fun unregister(id: String) {
        map.remove(id)?.close()
        updateActiveVolumes()
    }
}