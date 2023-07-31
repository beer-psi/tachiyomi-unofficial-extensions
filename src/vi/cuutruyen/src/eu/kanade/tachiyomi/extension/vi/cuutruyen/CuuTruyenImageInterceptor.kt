package eu.kanade.tachiyomi.extension.vi.cuutruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import app.cash.quickjs.QuickJs
import app.cash.quickjs.QuickJsException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class CuuTruyenImageInterceptor : Interceptor {
    private val cuudrmBytecode: ByteArray by lazy {
        /*
            FastestSmallestTextEncoderDecoder
            https://github.com/anonyco/FastestSmallestTextEncoderDecoder

            SPDX-License-Identifier: CC0-1.0
            Copyright (c) 2020 anonyco
         */
        val fastestSmallestTextEncoderDecoderJs = javaClass.getResource("/assets/EncoderDecoderTogether.min.js")
            ?.readText() ?: throw IOException("EncoderDecoderTogether.min.js not found.")

        /*
            rollup -f iife -p terser -n cuudrm cuudrm.js
         */
        val cuudrmJs = javaClass.getResource("/assets/cuudrm.js")
            ?.readText() ?: throw IOException("cuudrm.js not found.")

        QuickJs.create().use {
            it.compile(fastestSmallestTextEncoderDecoderJs + cuudrmJs, "?")
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.fragment?.contains(KEY) != true) {
            return response
        }
        val drmData = response.request.url.fragment!!
            .substringAfter("$KEY=")
            .replace("\n", "")

        val image = unscrambleImage(response.body.byteStream(), drmData)
        val body = image.toResponseBody("image/jpeg".toMediaTypeOrNull())
        return response.newBuilder()
            .body(body)
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun unscrambleImage(image: InputStream, drmData: String): ByteArray {
        val bitmap = BitmapFactory.decodeStream(image)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        try {
            val transformations = QuickJs.create().use { ctx ->
                ctx.execute(cuudrmBytecode)

                val decryptScript = "cuudrm.render_image(null, null, '$drmData');"
                val transformations = ctx.evaluate(decryptScript)
                (transformations as Array<Any>).map { (it as Array<Any>).map { it as Int } }
            }
            transformations.forEach {
                // Scrambling only happens horizontally (along the height).
                //
                // The coordinates array are arguments of JS' CanvasRenderingContext2D.drawImage():
                // sx, sy, sWidth, sHeight, dx, dy, dWidth, dHeight
                //
                // coordinates[2] and coordinates[6] are not used because they are set to a specific
                // width (1116) to keep cuudrm_bg happy without giving it an actual image to work with.
                val sx = it[0]
                val sy = it[1]
                val sHeight = it[3]
                val dx = it[4]
                val dy = it[5]
                val dHeight = it[7]

                val srcRect = Rect(sx, sy, sx + bitmap.width, sy + sHeight)
                val dstRect = Rect(dx, dy, dx + bitmap.width, dy + dHeight)
                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            }
        } catch (e: QuickJsException) {
            Log.e("CuuTruyenImageIntercept", e.stackTraceToString())
            throw IOException(e)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)
        return output.toByteArray()
    }

    companion object {
        const val KEY = "drm_data"
    }
}
