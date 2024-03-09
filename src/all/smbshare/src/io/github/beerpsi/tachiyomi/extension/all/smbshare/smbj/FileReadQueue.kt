package io.github.beerpsi.tachiyomi.extension.all.smbshare.smbj

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.mssmb2.messages.SMB2ReadResponse
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.share.File
import java.util.LinkedList
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class FileReadQueue(
    private val file: File,
    private val bufferSize: Int,
    private val readTimeout: Long,
) {
    private val readAheadSize = bufferSize
    private val pendingRequests = LinkedList<Future<SMB2ReadResponse>>()
    private var pendingAmount: Int = 0
    private var readOffset: Long = 0

    fun reset(pos: Long) {
        readOffset = pos
        clearPendingRequests()
    }

    fun clearPendingRequests() {
        pendingRequests.forEach { it.cancel(true) }
        pendingRequests.clear()
        pendingAmount = 0
    }

    suspend fun getNextChunk(): ByteArray? {
        if (pendingRequests.isEmpty()) {
            fillQueue()
        }

        val resp = getNextResponse()

        return if (resp != null) {
            fillQueue()
            resp.data
        } else {
            clearPendingRequests()
            null
        }
    }

    private suspend fun getNextResponse(): SMB2ReadResponse? {
        if (pendingRequests.isEmpty()) {
            return null
        }

        val resp = getReadResponse(pendingRequests.removeFirst(), readTimeout)

        if (resp != null) {
            pendingAmount -= bufferSize
        }

        return resp
    }

    private fun fillQueue() {
        while (pendingAmount < readAheadSize) {
            pendingRequests.add(sendReadRequest(readOffset, bufferSize))
            pendingAmount += bufferSize
            readOffset += bufferSize
        }
    }

    private val readAsyncMethod by lazy {
        File::class.java.getDeclaredMethod("readAsync", Long::class.java, Int::class.java).apply {
            isAccessible = true
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendReadRequest(offset: Long, length: Int): Future<SMB2ReadResponse> {
        return readAsyncMethod.invoke(file, offset, length) as Future<SMB2ReadResponse>
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun getReadResponse(request: Future<SMB2ReadResponse>, timeout: Long): SMB2ReadResponse? {
        val resp = try {
            request.await(timeout, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            throw TransportException(e)
        }

        if (resp.header.statusCode == NtStatus.STATUS_END_OF_FILE.value || resp.dataLength == 0) {
            return null
        }

        if (resp.header.statusCode != NtStatus.STATUS_SUCCESS.value) {
            throw SMBApiException(resp.header, "Read failed for $this")
        }

        return resp
    }
}
