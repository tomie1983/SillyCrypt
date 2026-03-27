package com.sillycrypt.exfat_android.provider

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import com.dev.exfat.file.ExFATSeekableFile
import kotlinx.coroutines.runBlocking

class ExFatProxyFileDescriptorCallback(
    private val handle: ExFATSeekableFile
) : ProxyFileDescriptorCallback() {

    override fun onGetSize(): Long {
        return runBlocking {
            try {
                handle.getSize()
            } catch (t: Throwable) {
                throw errno("onGetSize", OsConstants.EIO, t)
            }
        }
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        return runBlocking {
            try {
                handle.readAt(
                    position = offset,
                    dst = data,
                    dstOffset = 0,
                    length = minOf(size, data.size)
                )
            } catch (e: IllegalArgumentException) {
                throw errno("onRead", OsConstants.EINVAL, e)
            } catch (e: UnsupportedOperationException) {
                throw errno("onRead", OsConstants.EBADF, e)
            } catch (t: Throwable) {
                throw errno("onRead", OsConstants.EIO, t)
            }
        }
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        return runBlocking {
            try {
                handle.writeAt(
                    position = offset,
                    src = data,
                    srcOffset = 0,
                    length = minOf(size, data.size)
                )
            } catch (e: IllegalArgumentException) {
                throw errno("onWrite", OsConstants.EINVAL, e)
            } catch (e: UnsupportedOperationException) {
                // Пока write-core может быть не готов.
                throw errno("onWrite", if (handle.options.write) OsConstants.EIO else OsConstants.EBADF, e)
            } catch (t: Throwable) {
                throw errno("onWrite", OsConstants.EIO, t)
            }
        }
    }

    override fun onFsync() {
        runBlocking {
            try {
                handle.fsync()
            } catch (t: Throwable) {
                throw errno("onFsync", OsConstants.EIO, t)
            }
        }
    }

    override fun onRelease() {
        runBlocking {
            try {
                handle.close()
            } catch (_: Throwable) {
                // onRelease should not throw
            }
        }
    }

    private fun errno(function: String, code: Int, cause: Throwable): ErrnoException {
        return ErrnoException(function, code).apply { initCause(cause) }
    }
}