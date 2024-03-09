package io.github.beerpsi.tachiyomi.extension.all.smbshare.smbj

import android.annotation.SuppressLint
import com.hierynomus.msfscc.fileinformation.FileEndOfFileInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.smbj.io.ByteBufferByteChunkProvider
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SeekableByteChannel

// Desugaring is enabled.
@SuppressLint("NewApi")
class FileChannel(
    private val file: File,
    bufferSize: Int,
    readTimeout: Long,
) : SeekableByteChannel {

    private var isClosed: Boolean = false
    private var position: Long = 0
    private var buffer: ByteBuffer? = null
    private val readQueue = FileReadQueue(file, bufferSize, readTimeout)

    override fun close() {
        isClosed = true
        buffer = null
    }

    override fun isOpen() = !isClosed

    override fun read(dst: ByteBuffer?): Int {
        requireOpen()

        var total = 0
        val remaining = dst?.remaining() ?: return 0

        while (total < remaining) {
            if ((buffer == null || buffer!!.remaining() <= 0) && runBlocking { loadBuffer() } == -1) {
                return -1
            }

            val length = minOf(buffer!!.remaining(), dst.remaining())

            dst.put(buffer!!.array(), buffer!!.position(), length)
            buffer!!.position(buffer!!.position() + length)
            position += length
            total += length
        }

        return total
    }

    override fun write(src: ByteBuffer?): Int {
        requireOpen()

        return file.write(ByteBufferByteChunkProvider(src, position)).let {
            updatePosition(position + it)
            it.toInt()
        }
    }

    override fun position(): Long {
        requireOpen()
        return position
    }

    override fun position(newPosition: Long): SeekableByteChannel {
        requireOpen()
        if (newPosition != position) {
            updatePosition(newPosition)
        }
        return this
    }

    override fun size(): Long {
        requireOpen()
        return file.getFileInformation(FileStandardInformation::class.java).endOfFile
    }

    override fun truncate(size: Long): SeekableByteChannel {
        requireOpen()
        file.setFileInformation(FileEndOfFileInformation(size))
        return this
    }

    private suspend fun loadBuffer(): Int {
        val chunk = readQueue.getNextChunk() ?: return -1
        val dataLength = chunk.size

        buffer = ByteBuffer.wrap(chunk)

        return dataLength
    }

    private fun requireOpen() {
        if (isClosed) {
            throw ClosedChannelException()
        }
    }

    private fun updatePosition(newPosition: Long) {
        position = newPosition
        buffer = null
        readQueue.reset(position)
    }
}
