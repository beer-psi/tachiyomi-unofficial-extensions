package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.HomeView
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.Manga
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.MangaDetailView
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.MangaListView
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.MangaViewerStatus
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.MangaViewerView
import io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models.SearchView
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

private val API_URL = "https://lu0.jp".toHttpUrl()

class MangaGaugau : HttpSource() {

    override val name = "マンガがうがう"

    override val baseUrl = "https://gaugau.futabanet.jp"

    override val lang = "ja"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(::authIntercept)
        .addInterceptor(::thumbnailIntercept)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "okhttp/4.11.0")

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private lateinit var titleCache: Map<Int, Manga>

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = API_URL.newBuilder()
            .addPathSegments("api/v1/home")
            .addCommonQueryParameters()
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<HomeView>()

        titleCache = data.rankings.flatMap { it.titles }.associateBy { it.titleId }
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val directory = titleCache.values
        val manga = directory.drop((page - 1) * 24).take(24).map { it.toSManga(API_URL) }
        val hasNextPage = (page + 1) * 24 < titleCache.size

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val weekday = weekdayDateFormat.format(Calendar.getInstance().time.time).lowercase()
        val url = API_URL.newBuilder()
            .addPathSegments("api/v1/manga/update_day")
            .addQueryParameter("code", weekday)
            .addCommonQueryParameters()
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListView>()
        val manga = data.titles.map { it.toSManga(API_URL) }

        return MangasPage(manga, false)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, query) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = API_URL.newBuilder()
            .addPathSegments("api/v1/search")
            .addCommonQueryParameters()
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val data = response.parseAs<SearchView>()

        titleCache = data.titles
            .filter { it.titleName.contains(query, true) }
            .associateBy { it.titleId }

        return parseDirectory(1)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = API_URL.newBuilder()
            .addPathSegments("api/v1/manga/detail")
            .addQueryParameter("title_id", manga.url)
            .addCommonQueryParameters()
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<MangaDetailView>().toSManga(API_URL)

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) =
        response.parseAs<MangaDetailView>().chapters.map { it.toSChapter() }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = API_URL.newBuilder()
            .addPathSegments("api/v1/manga/viewer")
            .addQueryParameter("chapter_id", chapter.url)
            .addQueryParameter("free_point", "0")
            .addQueryParameter("event_point", "0")
            .addQueryParameter("paid_point", "0")
            .addQueryParameter("gau_potion_count", "0")
            .addCommonQueryParameters()
            .toString()

        return POST(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<MangaViewerView>()

        return when (data.status) {
            MangaViewerStatus.SUCCESS ->
                data.pages
                    .mapNotNull { it.image }
                    .mapIndexed { i, page -> Page(i, imageUrl = "$API_URL${page.imageUrl.removePrefix("/")}") }
            MangaViewerStatus.CONTENT_NOT_FOUND -> throw Exception("Could not find chapter")
            MangaViewerStatus.POINT_MISMATCH -> throw Exception("Purchase this chapter in the official app.")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun thumbnailIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful || request.url.pathSegments.first() != "l") {
            return response
        }

        val titleId = request.url.pathSegments.last().toInt()
        val title = titleCache[titleId] ?: return response

        response.close()

        val thumbnailRequest = GET("$API_URL${title.singleListThumbnailUrl}", request.headers)
        return chain.proceed(thumbnailRequest)
    }

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isApiRequest = request.url.host == API_URL.host &&
            request.url.encodedPath.startsWith("/api/v1")
        val isAuthenticatedRequest = request.url.queryParameter("secret") != null
        val isRegisterRequest = request.url.pathSegments.last() == "register"

        if (!isApiRequest || isAuthenticatedRequest || isRegisterRequest) {
            return chain.proceed(request)
        }

        val secret = generateDeviceToken()
        val deviceToken = generateDeviceToken()
        val securityKey = calculateSecurityKey(secret, deviceToken)
        val registerUrl = API_URL.newBuilder().apply {
            addPathSegments("api/v1/register")
            addQueryParameter("device_token", deviceToken)
            addQueryParameter("security_key", securityKey)
            addQueryParameter("secret", secret)
            addCommonQueryParameters()
        }.build()
        val registerRequest = Request.Builder()
            .method("PUT", ByteArray(0).toRequestBody())
            .url(registerUrl)
            .headers(headers)
            .build()

        client.newCall(registerRequest).execute()
        preferences.edit().putString(PREF_SECRET, secret).apply()

        val url = request.url.newBuilder()
            .addQueryParameter("secret", secret)
            .build()
        val newRequest = request.newBuilder().url(url).build()

        return chain.proceed(newRequest)
    }

    private fun HttpUrl.Builder.addCommonQueryParameters() = apply {
        addQueryParameter("os", "android")
        addQueryParameter("os_ver", Build.VERSION.SDK_INT.toString())
        addQueryParameter("app_ver", APP_VER)

        preferences.getString(PREF_SECRET, null)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                addQueryParameter("secret", it)
            }
    }
}

private val weekdayDateFormat = SimpleDateFormat("EE", Locale.ENGLISH)

private const val PREF_SECRET = "secret"
private const val APP_VER = "50"
private const val DEVICE_TOKEN_BYTES = 32

private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

private inline fun <reified T> Response.parseAs(): T =
    ProtoBuf.decodeFromByteArray(body.bytes())

fun generateDeviceToken() = Random.nextBytes(DEVICE_TOKEN_BYTES).toHex()

fun calculateSecurityKey(secret: String, deviceToken: String): String {
    val md5 = MessageDigest.getInstance("SHA256")

    return md5.digest("$secret$deviceToken$SECURITY_KEY_SALT".encodeToByteArray()).toHex()
}
