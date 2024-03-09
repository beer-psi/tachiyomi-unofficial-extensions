package io.github.beerpsi.tachiyomi.extension.all.smbshare

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import io.github.beerpsi.tachiyomi.extension.all.smbshare.smbj.FileChannel
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLConnection

class SMBImageInterceptor(private val extension: SMBShare) : Interceptor {
    @Suppress("LongMethod", "ReturnCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != SMBImageInterceptorHelper.HOST) {
            return chain.proceed(request)
        }

        val respBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
        val diskShare = extension.diskShare
            ?: return respBuilder
                .code(HTTP_INTERNAL_SERVER_ERROR)
                .message("Internal Server Error")
                .body("SMB disk share not available".toResponseBody())
                .build()
        val pathToGet = request.url.pathSegments.joinToString("/")

        if (!diskShare.fileExists(pathToGet)) {
            return respBuilder
                .code(HTTP_NOT_FOUND)
                .message("Not Found")
                .build()
        }

        val outputStream = ByteArrayOutputStream()
        if (request.url.fragment != null) {
            val file = extension.diskShare!!.openFile(
                pathToGet,
                setOf(AccessMask.FILE_READ_DATA),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            val channel = FileChannel(
                file,
                extension.smbConfig.readBufferSize,
                extension.smbConfig.readTimeout,
            )
            val zip = ZipFile(
                channel,
                pathToGet,
                "UTF-8",
                true,
                true,
            )
            val entry = zip.getEntry(request.url.fragment)

            zip.getInputStream(entry).use { it.copyTo(outputStream) }
            file.close()
            channel.close()
            try {
                zip.close()
            } catch (_: IOException) {}
        } else {
            diskShare.useFileInputStream(pathToGet) { it.copyTo(outputStream) }
        }
        val output = outputStream.toByteArray()
        val fileName = request.url.fragment ?: request.url.pathSegments.last()
        val mediaType = URLConnection.guessContentTypeFromName(fileName)
            ?.toMediaType()

        return respBuilder
            .code(HTTP_OK)
            .message("OK")
            .body(output.toResponseBody(mediaType))
            .build()
    }
}

object SMBImageInterceptorHelper {
    const val HOST = "smb-image-interceptor"

    fun createUrl(vararg paths: String): HttpUrl {
        return HttpUrl.Builder().apply {
            scheme("http")
            host(HOST)
            paths.forEach { addPathSegments(it) }
        }.build()
    }

    fun createUrl(path: String): HttpUrl {
        return HttpUrl.Builder().apply {
            scheme("http")
            host(HOST)
            addPathSegments(path)
        }.build()
    }
}

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404
private const val HTTP_INTERNAL_SERVER_ERROR = 500
