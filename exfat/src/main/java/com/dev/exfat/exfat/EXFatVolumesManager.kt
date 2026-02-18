package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EXFatVolumesManager {
    private val map = ConcurrentHashMap<String, ExFATVeracryptFS>()
    private val _activeVolumes: MutableStateFlow<List<String>> = MutableStateFlow(listOf())
    val activeVolumes = _activeVolumes.asStateFlow()

    fun register(volume: RandomAccessData): String {
        var id = UUID.randomUUID().toString()
        while (map.contains(id)) {
            id = UUID.randomUUID().toString()
        }
        map[id] = ExFATVeracryptFS(volume)
        _activeVolumes.update { it + id }
        return id
    }

    fun get(id: String): ExFATVeracryptFS =
        map[id] ?: throw IllegalStateException("Volume not mounted: $id")

    suspend fun unregister(id: String) {
        map.remove(id)?.close()
    }
}