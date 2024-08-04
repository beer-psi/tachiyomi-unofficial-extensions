package io.github.beerpsi.tachiyomi.extension.vi.cuutruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class CuuTruyenImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val fragment = response.request.url.fragment

        if (fragment == null || !fragment.contains(DRM_DATA_KEY)) {
            return response
        }

        val drmData = fragment.substringAfter("$DRM_DATA_KEY=")
        val image = unscrambleImage(response.body.byteStream(), drmData)
        val body = image.toResponseBody("image/jpeg".toMediaTypeOrNull())
        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun unscrambleImage(image: InputStream, drmData: String): ByteArray {
        val bitmap = BitmapFactory.decodeStream(image)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val data = Base64.decode(drmData, Base64.DEFAULT)
            .decodeXorCipher(DECRYPTION_KEY)
            .toString(Charsets.UTF_8)

        if (!data.startsWith("#v4|")) {
            throw IOException("Invalid DRM data (does not start with expected magic bytes): $data")
        }

        var sy = 0;
        for (t in data.split('|').drop(1)) {
            val (dy, height) = t.split('-').map(String::toInt)
            val srcRect = Rect(0, sy, bitmap.width, sy + height)
            val dstRect = Rect(0, dy, bitmap.width, dy + height)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            sy += height
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, output)
        return output.toByteArray()
    }

    private fun ByteArray.decodeXorCipher(key: String): ByteArray {
        val k = key.toByteArray(Charsets.UTF_8)

        return this.mapIndexed { i, b ->
            (b.toInt() xor k[i % k.size].toInt()).toByte()
        }
            .toByteArray()
    }

    companion object {
        const val DRM_DATA_KEY = "drm_data"
    }
}

private const val COMPRESS_QUALITY = 100
private const val DECRYPTION_KEY = "3141592653589793"
