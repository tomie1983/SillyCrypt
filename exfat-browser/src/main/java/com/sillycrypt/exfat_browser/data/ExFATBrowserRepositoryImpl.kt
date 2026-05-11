package com.sillycrypt.exfat_browser.data

import android.content.Context
import android.net.Uri
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.exfat.exfat.ExFATFS
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.exfat.file.ExFATFile
import com.dev.exfat.file.SeekableOpenOptions
import com.sillycrypt.exfat_browser.domain.entities.BrowserDisplayEntry
import com.sillycrypt.exfat_browser.domain.repository.ExFATBrowserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject

class ExFATBrowserRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val _selectedVolumeFlow: MutableSharedFlow<VeracryptVolumeData>,
    private val _displayedListFlow: MutableSharedFlow<BrowserDisplayEntry>,
): ExFATBrowserRepository {

    private var exFatFS: ExFATFS? = null

    private var currentDir: ExFATFile? = null

    override val selectedVolume: Flow<VeracryptVolumeData> = _selectedVolumeFlow.asSharedFlow()

    override val displayedStateFlow: Flow<BrowserDisplayEntry> = _displayedListFlow.asSharedFlow()

    override suspend fun setSelectedVolume(volumeData: VeracryptVolumeData) {
        exFatFS = EXFatVolumesManager.get(volumeData.uuid)
        currentDir = exFatFS?.root()
        _selectedVolumeFlow.emit(volumeData)
        refreshFS()
    }

    private suspend fun refreshFS() {
        currentDir?.let { dir ->
            _displayedListFlow.emit(
                BrowserDisplayEntry(
                    dir.path,
                    dir.listFiles()
                        .sortedWith(
                            compareByDescending<ExFATFile> { it.isDirectory }
                                .thenBy { it.name.lowercase() }).toPersistentList()
                )
            )
        }
    }

    override suspend fun listFiles(file: ExFATFile) {
        check(file.isDirectory) {
            "FIle is not a directory"
        }
        currentDir = file
        refreshFS()
    }

    override suspend fun deleteFile(file: ExFATFile) {
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
        refreshFS()
    }

    override suspend fun createFile(name: String) {
        currentDir?.createFile(name)
        refreshFS()
    }

    override suspend fun createDir(name: String) {
        currentDir?.createDirectory(name)
        refreshFS()
    }

    override suspend fun copyFile(name: String, uri: Uri) {
        val dir = currentDir ?: throw IllegalStateException("Current directory undefined")

        val file = dir.createFile(name)
        val seekable = file.openSeekable(SeekableOpenOptions.fromAndroidMode("rw"))

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open input stream for uri: $uri" }

                val buffer = ByteArray(DEFAULT_COPY_BUFFER_SIZE)
                var position = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break

                    seekable.writeAt(
                        position = position,
                        src = buffer,
                        srcOffset = 0,
                        length = read
                    )

                    position += read.toLong()
                }

                seekable.truncate(position)
                seekable.fsync()
            }
        } catch (e: Throwable) {
            runCatching {
                file.delete()
            }
            throw e
        } finally {
            seekable.close()
            refreshFS()
        }
    }

    companion object {
        private const val DEFAULT_COPY_BUFFER_SIZE = 64 * 1024
    }
}