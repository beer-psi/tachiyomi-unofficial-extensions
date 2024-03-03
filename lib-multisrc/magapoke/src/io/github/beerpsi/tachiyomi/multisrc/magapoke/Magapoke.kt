package io.github.beerpsi.tachiyomi.multisrc.magapoke

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@SuppressLint("ApplySharedPref")
open class Magapoke(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val client = network.client.newBuilder()
        .addInterceptor(::mgpkHashIntercept)
        .addInterceptor(::errorIntercept)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "okhttp/4.9.1")

    override val supportsLatest = true

    open val version = "5.4.1"
    private val platform = 2

    /*
        Private cache to store episode_id_list from manga titles. Saves us a few calls.
     */
    private var episodeIdListCache: MutableMap<Int, List<Int>> = mutableMapOf()

    private fun HttpUrl.Builder.addCommonQueryParameters() = apply {
        addQueryParameter("platform", platform.toString())
        addQueryParameter("version", version)
        addQueryParameter("user_id", getUserId().toString())
    }

    private fun TitleListResponse.toMangaList(): List<SManga> = title_list.map {
        episodeIdListCache[it.title_id] = it.episode_id_list

        SManga.create().apply {
            url = "/landing?t=${it.title_id}"
            title = it.title_name
            author = it.author_text
            description = it.introduction_text
            thumbnail_url = it.thumbnail_image_url
        }
    }

    private fun titleListRequest(ids: Iterable<Int>): Request {
        val url = baseUrl.toHttpUrl().newBuilder().addCommonQueryParameters().apply {
            addPathSegments("title/list")

            addQueryParameter("title_id_list", ids.joinToString(","))
        }.build()

        return GET(url, headers)
    }

    private fun titleListParse(ids: Iterable<Int>): List<SManga> {
        return client.newCall(titleListRequest(ids)).execute().parseAs<TitleListResponse>().toMangaList()
    }

    open val popularMangaRankingId = 30
    open val popularMangaLimit = 10

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().addCommonQueryParameters().apply {
            addPathSegments("ranking/all")

            addQueryParameter("ranking_id", popularMangaRankingId.toString())
            addQueryParameter("offset", ((page - 1) * popularMangaLimit).toString())
            addQueryParameter("limit", popularMangaLimit.toString())
            addQueryParameter("ranking_ab", "0")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<RankingResponse>()
        val manga = titleListParse(data.ranking_title_list.map { it.id })
        return MangasPage(manga, manga.size == popularMangaLimit)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addCommonQueryParameters()
            .addPathSegments("title/weekly")
            .build().toString()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<WeeklyTitleResponse>()
        val weeklyList = data.weekly_list.find { it.weekday_index == data.today_weekday_index }!!
        val manga = titleListParse((weeklyList.new_title_id_list + weeklyList.bonus_point_title_id + weeklyList.title_id_list + weeklyList.popular_title_id_list).toSet())
        return MangasPage(manga, false)
    }

    /*
        The K MANGA/Mangapoke app asks for 100 results, but we can't handle that so
     */
    open val searchMangaLimit = 24

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().addCommonQueryParameters().apply {
            addPathSegments("search/title")

            addQueryParameter("keyword", query)
            addQueryParameter("offset", ((page - 1) * searchMangaLimit).toString())
            addQueryParameter("limit", searchMangaLimit.toString())
            // TODO: genre_id, tag_id, magazine_category
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val manga = response.parseAs<TitleListResponse>().toMangaList()
        return MangasPage(manga, manga.size == searchMangaLimit)
    }

    // Already returned on popular manga & co, saves a call
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun mangaDetailsRequest(manga: SManga) = throw UnsupportedOperationException("Unused")

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException("Unused")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val titleId = "${baseUrl}${manga.url}".toHttpUrl().queryParameter("t")?.toInt()
            ?: throw Exception("Missing title ID in manga URL")
        val episodeIds = if (episodeIdListCache[titleId] != null) {
            episodeIdListCache[titleId]!!
        } else {
            val data = client.newCall(titleListRequest(listOf(titleId))).execute().parseAs<TitleListResponse>()
            data.title_list.find { it.title_id == titleId }!!.episode_id_list
        }

        return client.newCall(chapterListRequest(episodeIds))
            .asObservableSuccess()
            .map { chapterListParse(it) }
    }

    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException("Unused")

    private fun chapterListRequest(episodeIds: Iterable<Int>): Request {
        val body = FormBody.Builder().apply {
            add("platform", platform.toString())
            add("version", version)
            add("user_id", getUserId().toString())
            add("episode_id_list", episodeIds.joinToString(","))
            add("force_master", "0")
        }.build()

        return POST("$baseUrl/episode/list", headers, body)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<EpisodeListResponse>()
        // TODO: Paid chapters wen
        return data.episode_list.filter { it.point == 0 }.sortedByDescending { it.index }.map {
            SChapter.create().apply {
                url = "/landing?t=${it.title_id}&e=${it.episode_id}#${it.magazine_id ?: 0}"
                name = it.episode_name
                date_upload = if (it.start_time.isNullOrEmpty()) {
                    0L
                } else {
                    kotlin.runCatching {
                        dateFormatter.parse(it.start_time)?.time
                    }.getOrNull() ?: 0L
                }
                chapter_number = it.index.toFloat()
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "${baseUrl}${chapter.url}".toHttpUrl()
        val chapterId = chapterUrl.queryParameter("e")
            ?: throw Exception("Missing chapter ID in chapter URL")
        val magazineId = chapterUrl.fragment
            ?: throw Exception("Missing magazine ID in chapter URL")

        val url = baseUrl.toHttpUrl().newBuilder().addCommonQueryParameters().apply {
            addPathSegments("episode/viewer")

            addQueryParameter("episode_id", chapterId)
            addQueryParameter("magazine_id", magazineId)
            addQueryParameter("force_master", "0")
            addQueryParameter("is_download", "0")
        }.build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<EpisodeViewerResponse>().page_list.sortedBy { it.index }.map {
            Page(it.index, imageUrl = it.image_url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused")

    private fun registerNewAccount(): RegisterResponse {
        val originalUUID = (0..1)
            .joinToString("") { UUID.randomUUID().toString().replace("-", "").lowercase() }

        val body = FormBody.Builder().apply {
            add("platform", "2")
            add("version", version)
            add("device", "Pixel 6a")
            add("locale", Locale.getDefault().toLanguageTag())
            add("original_uuid", originalUUID)
            add("os_version", "13")
            add("regid", "aaaaa")
            add("tablet", "0")
            add("timezone", "Asia/Tokyo")
            add("is_overwrite", "0")
        }.build()

        val response =
            client.newCall(POST("$baseUrl/user/register", headers, body)).execute().parseAs<RegisterResponse>()

        if (response.status != "success") {
            throw Exception("Failed to register: [Error ${response.response_code}] ${response.error_message}")
        }

        preferences.edit().apply {
            putInt("userId", response.user_id)
            putString("hashKey", response.hash_key)
        }.commit()

        return response
    }

    private fun getUserId(): Int {
        val value = preferences.getInt("userId", -1)
        if (value == -1) {
            return registerNewAccount().user_id
        }
        return value
    }
    private fun getHashKey(): String {
        val value = preferences.getString("hashKey", "")
        if (value.isNullOrEmpty()) {
            return registerNewAccount().hash_key
        }
        return value
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val json: Json by injectLazy()

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tokyo")
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(this.body.string())
    }

    private fun errorIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val contentType = response.headers["Content-Type"]

        if (response.isSuccessful || contentType != "application/json") {
            return response
        }

        val data = response.parseAs<MangapokeResponse>()
        throw IOException("[Error ${data.response_code}] ${data.error_message}")
    }

    private fun mgpkHashIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val params = when (request.method) {
            "GET" ->
                request.url.queryParameterNames
                    .associateWith { request.url.queryParameterValues(it).joinToString(",") }
            "POST" -> {
                val body = request.body as FormBody? ?: return chain.proceed(request)
                (0 until body.size).associate { body.name(it) to body.value(it) }
            }
            else -> return chain.proceed(request)
        }.toMutableMap()

        if (!params.keys.contains("user_id")) {
            return chain.proceed(request)
        }

        val hashKey = getHashKey()
        if (hashKey.isEmpty()) {
            throw IOException("user_id found in request but no hash_key saved.")
        }
        params["hash_key"] = hashKey

        val joinedHash = params.toSortedMap().values
            .joinToString("") { MessageDigest.getInstance("MD5").digest(it.toByteArray()).toHex() }
        val mgpkHash = MessageDigest.getInstance("SHA256").digest(joinedHash.toByteArray()).toHex()

        return chain.proceed(
            request.newBuilder()
                .header("x-mgpk-hash", mgpkHash)
                .build(),
        )
    }
}
