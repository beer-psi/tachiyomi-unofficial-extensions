package io.github.beerpsi.tachiyomi.multisrc.viz

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale

open class Viz(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource(), ConfigurableSource {

    override val client = network.client.newBuilder().addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        val mime = res.headers["Content-Type"]
        if (res.isSuccessful) {
            if (mime != "binary/octet-stream") {
                return@addInterceptor res
            }

            // Fix image content type
            val type = IMG_CONTENT_TYPE.toMediaType()
            val body = res.body.bytes().toResponseBody(type)
            return@addInterceptor res.newBuilder().body(body)
                .header("Content-Type", IMG_CONTENT_TYPE).build()
        }
        res.close()
        throw IOException("HTTP error ${res.code}")
    }.build()

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", System.getProperty("http.agent")!!)
        .add("Referer", androidAppPackageName)
        .add("x-devil-fruit", "$vizAndroidAppVersionCode flame-flame fruits")

    open val vizAppId = 1
    open val androidAppPackageName = "com.vizmanga.android"
    open val subscriptionInfoPrefix = "vm_"

    private val apiUrl = "https://api.viz.com"
    private val vizDeviceId = 4
    private val vizVersionId = 9
    private val vizAndroidAppVersionCode = 123

    private fun commonFormBodyBuilder() = FormBody.Builder().apply {
        add("instance_id", instanceId)
        add("device_token", instanceId)
        add("device_id", vizDeviceId.toString())
        add("version", vizVersionId.toString())
        add("viz_app_id", vizAppId.toString())
        add("android_app_version_code", vizAndroidAppVersionCode.toString())

        val jwtToken = getJwtToken()
        val userId = getUserId()
        if (jwtToken.isNotEmpty() && userId != -1) {
            add("trust_user_jwt", jwtToken)
            add("user_id", userId.toString())
        }
    }

    private val instanceId by lazy {
        val pref = preferences.getString(DEVICE_TOKEN_PREF, "")
        if (pref.isNullOrEmpty()) {
            val byteArray = ByteArray(8)
            SecureRandom().nextBytes(byteArray)
            byteArray.toHex()
        } else {
            pref
        }
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private lateinit var directory: List<MangaSeriesDto>
    private var premiumEnabled = false

    private fun doLogin(username: String, password: String): LoginResponseDto {
        preferences.edit().putString(DEVICE_TOKEN_PREF, instanceId).apply()

        val request = POST(
            "$apiUrl/manga/try_manga_login",
            headers,
            commonFormBodyBuilder().apply {
                add("login", username)
                add("pass", password)
            }.build(),
        )

        val response = client.newCall(request).execute().parseAs<LoginResponseDto>()
        if (response.ok != 1 || response.trust_user_jwt == null || response.user_id == null) {
            throw Exception("Login failed. There was an issue with your username/password.")
        }

        preferences.edit().apply {
            putString(TRUST_JWT_TOKEN_PREF, response.trust_user_jwt)
            putInt(USER_ID_PREF, response.user_id)
        }.apply()

        return response
    }

    init {
        val username = getUsername()
        val password = getPassword()
        var jwtToken = getJwtToken()
        var userId = getUserId()

        Single.fromCallable {
            if (username.isEmpty() || password.isEmpty()) {
                return@fromCallable
            }

            if (jwtToken.isEmpty() || userId == -1) {
                val response = doLogin(username, password)
                jwtToken = response.trust_user_jwt!!
                userId = response.user_id!!
            }

            val entitledUrl = "$apiUrl/manga/entitled".toHttpUrl().newBuilder().apply {
                addQueryParameter("trust_user_jwt", jwtToken)
                addQueryParameter("user_id", userId.toString())
                addQueryParameter("instance_id", instanceId)
                addQueryParameter("device_token", instanceId)
                addQueryParameter("device_id", vizDeviceId.toString())
                addQueryParameter("version", vizVersionId.toString())
                addQueryParameter("viz_app_id", vizAppId.toString())
                addQueryParameter("android_app_version_code", vizAndroidAppVersionCode.toString())
            }.build().toString()

            val entitledResponse = client.newCall(GET(entitledUrl, headers)).execute().parseAs<EntitledResponseDto>()

            val validFromField = SubscriptionInfoDto::class.java.getField("${subscriptionInfoPrefix}valid_from")
            val validToField = SubscriptionInfoDto::class.java.getField("${subscriptionInfoPrefix}valid_to")

            val validFromRaw = validFromField.get(entitledResponse.subscription_info) as String?
            val validToRaw = validToField.get(entitledResponse.subscription_info) as String?

            if (validFromRaw == null || validToRaw == null) {
                premiumEnabled = false
                return@fromCallable
            }

            val validFrom = getEpoch(validFromRaw, -1)
            val validTo = getEpoch(validToRaw, -1)

            val currentTime = System.currentTimeMillis()
            premiumEnabled = currentTime in (validFrom + 1) until validTo
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    Log.e("Viz", "Could not log into Viz: $it")
                },
            )
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/manga/store_cached/$vizAppId/$vizDeviceId/$vizVersionId", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<StoreResponseDto>()
        directory = directoryFromResponse(data)
        return parseDirectory(1)
    }

    private fun directoryFromResponse(response: StoreResponseDto): List<MangaSeriesDto> {
        return response.data.filterIsInstance<MangaSeriesWrapperDto>().mapNotNull {
            if (it.manga_series.show_chapter && (it.manga_series.num_chapters_free > 0 || premiumEnabled)) {
                it.manga_series
            } else {
                null
            }
        }
    }

    private fun parseDirectory(page: Int): MangasPage {
        val manga = mutableListOf<SManga>()
        val endRange = ((page * 24) - 1).let { if (it <= directory.lastIndex) it else directory.lastIndex }

        for (i in (((page - 1) * 24)..endRange)) {
            var desc = directory[i].synopsis
            if (directory[i].tagline != null) {
                desc = directory[i].tagline + "\n\n" + desc
            }
            manga.add(
                SManga.create().apply {
                    url = "/chapters/${directory[i].vanityurl}#${directory[i].id}"
                    title = directory[i].title
                    author = directory[i].latest_author
                    description = desc
                    status = SManga.UNKNOWN
                    thumbnail_url = directory[i].link_img_url
                },
            )
        }
        return MangasPage(manga, endRange < directory.lastIndex)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { latestUpdatesParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(1)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<StoreResponseDto>()
        directory = directoryFromResponse(data).sortedByDescending { it.chapter_latest_pub_date }
        return parseDirectory(1)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response, query)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not supported")

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val data = response.parseAs<StoreResponseDto>()
        directory = if (query.startsWith("id:")) {
            val id = query.removePrefix("id:")
            directoryFromResponse(data).filter { it.vanityurl == id }
        } else {
            directoryFromResponse(data).filter { it.title_sort.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true) }
        }
        return parseDirectory(1)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val coverArtPreference = getCoverArtPreference()
        if (coverArtPreference == DEFAULT) {
            return Observable.just(manga)
        } else {
            return client.newCall(chapterListRequest(manga))
                .asObservableSuccess()
                .map {
                    val data = it.parseAs<StoreResponseDto>().data
                    val volumes = data.filterIsInstance<MangaWrapperDto>().mapNotNull { manga ->
                        if (manga.manga.volume != null && !manga.manga.thumburl.isNullOrEmpty()) {
                            manga.manga
                        } else {
                            null
                        }
                    }.sortedBy { manga -> manga.volume }

                    if (volumes.isEmpty()) {
                        return@map manga
                    }

                    manga.thumbnail_url = if (coverArtPreference == FIRST_VOLUME) {
                        volumes[0].thumburl
                    } else {
                        volumes.last().thumburl
                    }
                    manga
                }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not supported")

    override fun chapterListRequest(manga: SManga): Request {
        val id = "$baseUrl${manga.url}".toHttpUrl().fragment
        return GET("$apiUrl/manga/store/series/$id/$vizAppId/$vizDeviceId/$vizVersionId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<StoreResponseDto>().data
        val currentEpoch = System.currentTimeMillis() / 1000

        return data.filterIsInstance<MangaWrapperDto>().mapNotNull {
            val freeChapter = (it.manga.epoch_exp_date == null || it.manga.epoch_exp_date > currentEpoch) && (it.manga.epoch_pub_date == null || it.manga.epoch_pub_date < currentEpoch)
            if (it.manga.chapter != null && (freeChapter || premiumEnabled)) {
                val chapterNumber = it.manga.chapter.replace(".0", "")
                SChapter.create().apply {
                    url = "/${it.manga.series_vanityurl}-chapter-$chapterNumber/chapter/${it.manga.id}?action=read#${it.manga.numpages}"
                    name = "Chapter $chapterNumber"
                    date_upload = if (it.manga.publication_date != null) {
                        getEpoch(it.manga.publication_date)
                    } else {
                        0L
                    }
                    chapter_number = it.manga.chapter.toFloat()
                }
            } else {
                null
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val id = url.pathSegments.last()
        val pageCount = url.fragment!!.toInt()
        val request = POST(
            "$apiUrl/manga/auth",
            headers,
            commonFormBodyBuilder().apply {
                add("manga_id", id)
            }.build(),
        )
        return client.newCall(request).asObservableSuccess().map { pageListParse(it, id, pageCount) }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Unused")

    private fun pageListParse(response: Response, mangaId: String, pageCount: Int): List<Page> {
        val data = response.parseAs<AuthResponseDto>()

        if (data.archive_info.ok == 0) {
            throw Exception("Cannot read premium chapters")
        }

        return (0..pageCount).map {
            Page(it, "https://api.viz.com/manga/get_manga_url#$mangaId")
        }
    }

    override fun imageUrlRequest(page: Page): Request {
        val mangaId = page.url.toHttpUrl().fragment!!
        return POST(
            "$apiUrl/manga/get_manga_url",
            headers,
            commonFormBodyBuilder().apply {
                add("manga_id", mangaId)
                add("page", page.index.toString())
            }.build(),
        )
    }

    override fun imageUrlParse(response: Response): String {
        return response.parseAs<DataResponseDto>().data
    }

    private fun getUsername(): String = preferences.getString(USERNAME_PREF, "")!!
    private fun getPassword(): String = preferences.getString(PASSWORD_PREF, "")!!
    private fun getJwtToken(): String = preferences.getString(TRUST_JWT_TOKEN_PREF, "")!!
    private fun getUserId(): Int = preferences.getInt(USER_ID_PREF, -1)
    private fun getCoverArtPreference(): String = preferences.getString(COVER_ART_PREF, DEFAULT)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val usernamePref = EditTextPreference(screen.context).apply {
            key = USERNAME_PREF
            title = "Username"
            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString()

                preferences.edit().apply {
                    if (value.isBlank()) {
                        remove(DEVICE_TOKEN_PREF)
                        remove(USER_ID_PREF)
                        remove(TRUST_JWT_TOKEN_PREF)
                    }
                    putString(USERNAME_PREF, value)
                }.commit()
            }
        }
        val passwordPref = EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Password"
            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString()

                preferences.edit().apply {
                    if (value.isBlank()) {
                        remove(DEVICE_TOKEN_PREF)
                        remove(USER_ID_PREF)
                        remove(TRUST_JWT_TOKEN_PREF)
                    }
                    putString(PASSWORD_PREF, value)
                }.commit()
            }
        }
        val coverSelectionPref = ListPreference(screen.context).apply {
            key = COVER_ART_PREF
            title = "Cover art"
            entries = arrayOf("Default", "First volume", "Latest volume")
            entryValues = arrayOf(DEFAULT, FIRST_VOLUME, LATEST_VOLUME)
            setDefaultValue(DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                val res = preferences.edit().putString(COVER_ART_PREF, newValue.toString()).commit()
                res
            }
        }

        screen.addPreference(usernamePref)
        screen.addPreference(passwordPref)
        screen.addPreference(coverSelectionPref)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private val iso8601TimezoneRegex = Regex("""([+\-])(\d{2}):(\d{2})""")

    private fun getEpoch(date: String, defaultValue: Long = 0L): Long = kotlin.runCatching {
        dateFormatter.parse(
            date.replace(iso8601TimezoneRegex) {
                it.groupValues.subList(1, 4).joinToString("")
            },
        )?.time
    }.getOrNull() ?: defaultValue

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    companion object {
        const val USERNAME_PREF = "USERNAME_PREF"
        const val PASSWORD_PREF = "PASSWORD_PREF"

        const val COVER_ART_PREF = "COVER_ART_PREF"
        const val DEFAULT = "DEFAULT"
        const val FIRST_VOLUME = "FIRST_VOLUME"
        const val LATEST_VOLUME = "LATEST_VOLUME"

        const val TRUST_JWT_TOKEN_PREF = "TRUST_JWT_TOKEN_PREF"
        const val USER_ID_PREF = "USER_ID_PREF"
        const val DEVICE_TOKEN_PREF = "DEVICE_TOKEN_PREF"

        const val IMG_CONTENT_TYPE = "image/jpeg"
    }
}
