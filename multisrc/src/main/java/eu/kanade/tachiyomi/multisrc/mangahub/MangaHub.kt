package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.util.Calendar

abstract class MangaHub(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    private var baseApiUrl = "https://api.mghubcdn.com"
    private var baseCdnUrl = "https://img.mghubcdn.com/file/imghub"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::uaIntercept)
        .addInterceptor(::apiAuthInterceptor)
        .addInterceptor(::thumbsInterceptor)
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json")
        .add("Origin", "$baseUrl/")
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("DNT", "1")
        .add("Sec-Fetch-Dest", "empty")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Site", "cross-site")

    open val json: Json by injectLazy()

    private var userAgent: String? = null
    private var checkedUa = false

    private val mangaSource = when (name) {
        "MangaHub" -> "m01"
        "MangaReader.site" -> "mr01"
        "MangaPanda.onl" -> "mr02"
        else -> throw Exception("Unknown source")
    }

    private fun uaIntercept(chain: Interceptor.Chain): Response {
        if (userAgent == null && !checkedUa) {
            val uaResponse = chain.proceed(GET(UA_DB_URL))

            if (uaResponse.isSuccessful) {
                // only using desktop chromium-based browsers, apparently they refuse to load(403) if not chrome(ium)
                val uaList = json.decodeFromString<Map<String, List<String>>>(uaResponse.body.string())
                val chromeUserAgentString = uaList["desktop"]!!.filter { it.contains("chrome", ignoreCase = true) }
                userAgent = chromeUserAgentString.random()
                checkedUa = true
            }

            uaResponse.close()
        }

        if (userAgent != null) {
            val newRequest = chain.request().newBuilder()
                .header("User-Agent", userAgent!!)
                .build()

            return chain.proceed(newRequest)
        }

        return chain.proceed(chain.request())
    }

    private fun thumbsInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val request = if (originalRequest.url.host == CDN_THUMBS_URL.toHttpUrl().host) {
            originalRequest.newBuilder()
                .header("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Sec-Fetch-Dest", "image")
                .header("Sec-Fetch-Mode", "no-cors")
                .removeHeader("Origin")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }

    private fun apiAuthInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val cookie = client.cookieJar
            .loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }

        val request =
            if (originalRequest.url.toString() == "$baseApiUrl/graphql" && cookie != null) {
                originalRequest.newBuilder()
                    .header("x-mhub-access", cookie.value)
                    .build()
            } else {
                originalRequest
            }

        return chain.proceed(request)
    }

    private fun refreshApiKey(chapter: SChapter?) {
        val now = Calendar.getInstance().time.time

        val slug = if (chapter != null) {
            "$baseUrl${chapter.url}"
                .toHttpUrlOrNull()
                ?.pathSegments
                ?.get(1)
        } else {
            null
        }

        val url = if (slug != null) {
            "$baseUrl/manga/$slug".toHttpUrl()
        } else {
            baseUrl.toHttpUrl()
        }

        // Clear key cookie
        val cookie = Cookie.parse(url, "mhub_access=; Max-Age=0; Path=/")!!
        client.cookieJar.saveFromResponse(url, listOf(cookie))

        // Set required cookie (for cache busting?)
        val recently = buildJsonObject {
            putJsonObject((now - (0..3600).random()).toString()) {
                put("mangaID", (1..42_000).random())
                put("number", (1..20).random())
            }
        }.toString()

        client.cookieJar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder()
                    .domain(url.host)
                    .name("recently")
                    .value(URLEncoder.encode(recently, "utf-8"))
                    .expiresAt(now + 2 * 60 * 60 * 24 * 31) // +2 months
                    .build(),
            ),
        )

        val request = GET("$url?reloadKey=1", headers)
        client.newCall(request).execute()
    }

    private fun graphQLRequest(query: String): Request {
        val body = buildJsonObject {
            put("query", query)
        }.toString().toRequestBody("application/json".toMediaType())

        return POST(
            "$baseApiUrl/graphql",
            headers,
            body,
        )
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { popularMangaParse(it, page) }
            .doOnError { refreshApiKey(null) }
            .retry(1)
    }

    // popular
    override fun popularMangaRequest(page: Int): Request =
        graphQLRequest(MangaHubQueries.popularQuery(mangaSource, (page - 1) * 30))

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Unused")

    private fun popularMangaParse(response: Response, page: Int): MangasPage {
        val data = response.parseAs<MangaListResponse>()

        if (!data.errors.isNullOrEmpty()) {
            throw Exception(data.errors.first().message)
        }

        val mangaList = data.data.search!!.rows.map { it.toSManga() }
        val hasMore = page * 30 < data.data.search.count
        return MangasPage(mangaList, hasMore)
    }

    // latest
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { latestUpdatesParse(it, page) }
            .doOnError { refreshApiKey(null) }
            .retry(1)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        graphQLRequest(MangaHubQueries.latestQuery(mangaSource, (page - 1) * 30))

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Unused")

    private fun latestUpdatesParse(response: Response, page: Int): MangasPage = popularMangaParse(response, page)

    // search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { searchMangaParse(it, page) }
            .doOnError { refreshApiKey(null) }
            .retry(1)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var mod = ""
        var genre = ""

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    mod = filter.values[filter.state].key
                }
                is GenreList -> {
                    genre = filter.values[filter.state].key
                }
                else -> {}
            }
        }

        return graphQLRequest(MangaHubQueries.searchQuery(mangaSource, false, query, genre, mod, (page - 1) * 30))
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Unused")

    private fun searchMangaParse(response: Response, page: Int): MangasPage = popularMangaParse(response, page)

    // manga details
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return super.fetchMangaDetails(manga)
            .doOnError { refreshApiKey(null) }
            .retry(1)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/")
        return graphQLRequest(MangaHubQueries.mangaDetailsQuery(mangaSource, slug))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaFullResponse>()

        if (!data.errors.isNullOrEmpty()) {
            throw Exception(data.errors.first().message)
        }

        return data.data.manga!!.toSManga()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url.substringBefore("#")}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .doOnError { refreshApiKey(null) }
            .retry(1)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/manga/")
        val body = buildJsonObject {
            put("query", MangaHubQueries.chapterListQuery(mangaSource, slug))
        }
        return POST(
            "$baseApiUrl/graphql#$slug",
            headers,
            body.toString().toRequestBody("application/json".toMediaType()),
        )
    }

    // chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.fragment!!
        val data = response.parseAs<ChapterListResponse>()

        if (!data.errors.isNullOrEmpty()) {
            throw Exception(data.errors.first().message)
        }

        return data.data.manga!!.chapters.map { it.toSChapter(slug) }.reversed()
    }

    // pages
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.split("/")

        return graphQLRequest(MangaHubQueries.chapterPagesQuery(mangaSource, chapterUrl[2], chapterUrl[3].substringAfter("#").toFloat()))
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        super.fetchPageList(chapter)
            .doOnError { refreshApiKey(chapter) }
            .retry(1)

    override fun pageListParse(response: Response): List<Page> {
        val chapterObject = json.decodeFromString<ApiChapterPagesResponse>(response.body.string())

        if (chapterObject.data?.chapter == null) {
            if (chapterObject.errors != null) {
                val errors = chapterObject.errors.joinToString("\n") { it.message }
                throw Exception(errors)
            }
            throw Exception("Unknown error while processing pages")
        }

        val pages = json.decodeFromString<ApiChapterPages>(chapterObject.data.chapter.pages)

        return pages.i.mapIndexed { i, page ->
            Page(i, "", "$baseCdnUrl/${pages.p}$page")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Unused")

    // Image
    override fun imageUrlRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .removeAll("Origin")
            .build()

        return GET(page.url, newHeaders)
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    // filters
    private class Genre(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)
    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Genres", genres, 0)

    override fun getFilterList() = FilterList(
        OrderBy(orderBy),
        GenreList(genres),
    )

    private val orderBy = arrayOf(
        Order("Popular", "POPULAR"),
        Order("Updates", "LATEST"),
        Order("A-Z", "ALPHABET"),
        Order("New", "NEW"),
        Order("Completed", "COMPLETED"),
    )

    private val genres = arrayOf(
        Genre("All Genres", "all"),
        Genre("[no chapters]", "no-chapters"),
        Genre("4-Koma", "4-koma"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Award Winning", "award-winning"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Food", "food"),
        Genre("Game", "game"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School life", "school-life"),
        Genre("Sci fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shoujoai", "shoujoai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Shounenai", "shounenai"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Space", "space"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Superhero", "superhero"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Wuxia", "wuxia"),
        Genre("Yuri", "yuri"),
    )

    companion object {
        private const val UA_DB_URL = "https://tachiyomiorg.github.io/user-agents/user-agents.json"

        const val CDN_THUMBS_URL = "https://thumb.mghubcdn.com"
    }
}
