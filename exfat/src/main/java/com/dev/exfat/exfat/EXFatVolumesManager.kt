package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EXFatVolumesManager {
    private val map = ConcurrentHashMap<String, ExFATFS>()
    private val _activeVolumes: MutableStateFlow<List<String>> = MutableStateFlow(listOf())
    val activeVolumes = _activeVolumes.asStateFlow()

    private fun updateActiveVolumes() {
        _activeVolumes.update { map.keys().toList() }
    }

    fun register(volumeFactory: RandomAccessDataFactory): String {
        var id = UUID.randomUUID().toString()
        while (map.contains(id)) {
            id = UUID.randomUUID().toString()
        }
        map[id] = ExFATFS(volumeFactory)
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