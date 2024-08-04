package io.github.beerpsi.tachiyomi.extension.vi.cuutruyen

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto.ChapterDto
import io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto.MangaDto
import io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto.ResponseDto
import io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto.SearchByTagDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

private const val HTTP_INTERNAL_SERVER_ERROR = 500

class CuuTruyen : HttpSource(), ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val name = "Cứu Truyện"

    override val lang = "vi"

    private val domain = preferences.getString(DOMAIN_PREF_KEY, DEFAULT_DOMAIN)
    override val baseUrl = "https://$domain"
    private val apiUrl = "https://$domain/api/v2"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .addInterceptor(CuuTruyenImageInterceptor())
        .addInterceptor(::thumbnailIntercept)
        .rateLimit(permits = 3)
        .build()

    private val titleCache = object : LinkedHashMap<Int, String?>(
        (TITLE_CACHE_CAPACITY / TITLE_CACHE_LOAD_FACTOR).toInt(),
        TITLE_CACHE_LOAD_FACTOR,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String?>?): Boolean {
            return size > TITLE_CACHE_CAPACITY
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("mangas/top")
            addQueryParameter("duration", "all")
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "24")
        }.build().toString()
        return GET(url, headers = headers, cache = CacheControl.FORCE_NETWORK)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.code == HTTP_INTERNAL_SERVER_ERROR) {
            return MangasPage(emptyList(), false)
        }

        val responseDto = response.parseAs<ResponseDto<List<MangaDto>>>()
        val coverKey = preferences.coverQuality
        val manga = responseDto.data.map { it.toSManga(coverKey) }
        val hasNextPage = responseDto.metadata!!.currentPage < responseDto.metadata.totalPages

        responseDto.data.forEach {
            titleCache[it.id] = when (coverKey) {
                "cover_mobile_url" -> it.coverMobileUrl
                else -> it.coverUrl
            }
        }

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("mangas/recently_updated")
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "24")
        }.build().toString()
        return GET(url, headers = headers, cache = CacheControl.FORCE_NETWORK)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH).trim()
                if (id.toIntOrNull() == null) {
                    throw Exception("ID tìm kiếm không hợp lệ (phải là một số).")
                }
                val url = "/mangas/$id"
                fetchMangaDetails(
                    SManga.create().apply {
                        this.url = url
                    },
                )
                    .map {
                        it.url = url
                        MangasPage(listOf(it), false)
                    }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.ifEmpty { getFilterList() }
            .filterIsInstance<TagFilter>()
            .firstOrNull()
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegments("mangas/search")
                addQueryParameter("q", query)
                addQueryParameter("page", page.toString())
                addQueryParameter("per_page", "24")
            } else if (tagFilter != null && tagFilter.state != 0) {
                addPathSegment("tags")
                addPathSegment(tagFilter.tags[tagFilter.state].id)
                addQueryParameter("page", page.toString())
                addQueryParameter("per_page", "30")
            } else {
                return popularMangaRequest(page)
            }
        }.build()

        return GET(url, headers = headers, cache = CacheControl.FORCE_NETWORK)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.endsWith("mangas/search")) {
            return popularMangaParse(response)
        }

        val data = response.parseAs<ResponseDto<SearchByTagDto>>()
        val coverKey = preferences.coverQuality
        val manga = data.data.mangas.map { it.toSManga(coverKey) }
        val hasNextPage = data.metadata!!.currentPage < data.metadata.totalPages

        data.data.mangas.forEach {
            titleCache[it.id] = when (coverKey) {
                "cover_mobile_url" -> it.coverMobileUrl
                else -> it.coverUrl
            }
        }

        return MangasPage(manga, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga {
        val responseDto = response.parseAs<ResponseDto<MangaDto>>()
        return responseDto.data.toSManga(preferences.coverQuality)
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$apiUrl${manga.url}/chapters", headers = headers, cache = CacheControl.FORCE_NETWORK)

    override fun chapterListParse(response: Response): List<SChapter> {
        val segments = response.request.url.pathSegments
        val lastIndex = segments.lastIndex
        val mangaUrl = "/${segments[lastIndex - 2]}/${segments[lastIndex - 1]}"
        return response.parseAs<ResponseDto<List<ChapterDto>>>().data.map { it.toSChapter(mangaUrl) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            val chapterId = chapter.url.split("/").last()
            addPathSegment("chapters")
            addPathSegment(chapterId)
        }.build().toString()
        return GET(url, headers = headers, cache = CacheControl.FORCE_NETWORK)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterDto = response.parseAs<ResponseDto<ChapterDto>>()
        return chapterDto.data.pages!!.map { it.toPage() }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        TagFilter(tagList),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "coverQuality"
            title = "Chất lượng ảnh bìa"
            entries = arrayOf("Chất lượng cao", "Di động")
            entryValues = arrayOf("cover_url", "cover_mobile_url")
            setDefaultValue("cover_url")

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String

                preferences.edit()
                    .putString("coverQuality", entry)
                    .commit()
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = DOMAIN_PREF_KEY
            title = DOMAIN_TITLE
            entries = DOMAINS
            entryValues = DOMAINS
            summary = domain

            setDefaultValue(DEFAULT_DOMAIN)

            setOnPreferenceChangeListener { _, _ ->
                Toast
                    .makeText(
                        screen.context,
                        "Khởi động lại Tachiyomi để áp dụng thay đổi.",
                        Toast.LENGTH_LONG
                    )
                    .show()
                true
            }
        }.let(screen::addPreference)
    }

    private fun thumbnailIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val isMangaCoverRequest = request.url.encodedPath.contains("/manga/") &&
            request.url.encodedPath.contains("/cover/")

        if (response.isSuccessful || !isMangaCoverRequest) {
            return response
        }

        val titleId = request.url.encodedPath
            .substringAfter("/manga/")
            .substringBefore("/cover/")
            .toInt()
        val newCover = titleCache[titleId] ?: return response

        response.close()
        return chain.proceed(request.newBuilder().url(newCover).build())
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(body.string())
    }

    private val SharedPreferences.coverQuality
        get() = getString("coverQuality", "")

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}

private const val TITLE_CACHE_CAPACITY = 120
private const val TITLE_CACHE_LOAD_FACTOR = 0.7F

private const val DOMAIN_PREF_KEY = "domain"
private const val DEFAULT_DOMAIN = "cuutruyen.net"
private const val DOMAIN_TITLE = "Tên miền"
private val DOMAINS = arrayOf("cuutruyen.net", "nettrom.com", "hetcuutruyen.net", "cuutruyent9sv7.xyz")

private class TagFilter(val tags: List<Tag>) : Filter.Select<String>(
    "Thể loại",
    tags.map { it.name }.toTypedArray(),
)

private class Tag(val name: String, val id: String)

// Got this list off their Discord, they don't have a tag list on the website
private val tagList by lazy {
    listOf(
        Tag("tất cả", ""),
        Tag("manga", "manga"),
        Tag("đang tiến hành", "dang-tien-hanh"),
        Tag("thể thao", "the-thao"),
        Tag("hài hước", "hai-huoc"),
        Tag("shounen", "shounen"),
        Tag("học đường", "hoc-duong"),
        Tag("chất lượng cao", "chat-luong-cao"),
        Tag("comedy", "comedy"),
        Tag("action", "action"),
        Tag("horror", "horror"),
        Tag("sci-fi", "sci-fi"),
        Tag("aliens", "aliens"),
        Tag("martial arts", "martial-arts"),
        Tag("military", "military"),
        Tag("monsters", "monsters"),
        Tag("supernatural", "supernatural"),
        Tag("web comic", "web-comic"),
        Tag("phiêu lưu", "phieu-luu"),
        Tag("hậu tận thế", "hau-tan-the"),
        Tag("hành động", "hanh-dong"),
        Tag("đã hoàn thành", "da-hoan-thanh"),
        Tag("sinh tồn", "sinh-ton"),
        Tag("du hành thời gian", "du-hanh-thoi-gian"),
        Tag("khoa học", "khoa-hoc"),
        Tag("tạm ngưng", "tam-ngung"),
        Tag("nsfw", "nsfw"),
        Tag("bạo lực", "bao-luc"),
        Tag("khoả thân", "khoa-than"),
        Tag("bí ẩn", "bi-an"),
        Tag("trinh thám", "trinh-tham"),
        Tag("kinh dị", "kinh-di"),
        Tag("máu me", "mau-me"),
        Tag("tình dục", "tinh-duc"),
        Tag("có màu", "co-mau"),
        Tag("manhwa", "manhwa"),
        Tag("webtoon", "webtoon"),
        Tag("siêu nhiên", "sieu-nhien"),
        Tag("fantasy", "fantasy"),
        Tag("võ thuật", "vo-thuat"),
        Tag("drama", "drama"),
        Tag("hệ thống", "he-thong"),
        Tag("lãng mạn", "lang-man"),
        Tag("đời thường", "doi-thuong"),
        Tag("công sở", "cong-so"),
        Tag("sát thủ", "sat-thu"),
        Tag("phép thuật", "phep-thuat"),
        Tag("tội phạm", "toi-pham"),
        Tag("seinen", "seinen"),
        Tag("isekai", "isekai"),
        Tag("chuyển sinh", "chuyen-sinh"),
        Tag("harem", "harem"),
        Tag("mecha", "mecha"),
        Tag("trung cổ", "trung-co"),
        Tag("lgbt", "lgbt"),
        Tag("yaoi", "yaoi"),
        Tag("game", "game"),
        Tag("bi kịch", "bi-kich"),
        Tag("động vật", "dong-vat"),
        Tag("tâm lý", "tam-ly"),
        Tag("manhua", "manhua"),
        Tag("nam biến nữ", "nam-bien-nu"),
        Tag("romcom", "romcom"),
        Tag("award winning", "award-winning"),
        Tag("oneshot", "oneshot"),
        Tag("khoa học viễn tưởng", "khoa-hoc-vien-tuong"),
        Tag("dark fantasy", "dark-fantasy"),
        Tag("zombie", "zombie"),
        Tag("nam x nam", "nam-x-nam"),
        Tag("giật gân", "giat-gan"),
        Tag("cảnh sát", "canh-sat"),
        Tag("ntr", "ntr"),
        Tag("cooking", "cooking"),
        Tag("ẩm thực", "am-thuc"),
        Tag("ecchi", "ecchi"),
        Tag("quái vật", "quai-vat"),
        Tag("vampires", "vampires"),
        Tag("nam giả nữ", "nam-gia-nu"),
        Tag("yakuza", "yakuza"),
        Tag("romance", "romance"),
        Tag("sport", "sport"),
        Tag("shoujo", "shoujo"),
        Tag("ninja", "ninja"),
        Tag("lịch sử", "lich-su"),
        Tag("doujinshi", "doujinshi"),
        Tag("databook", "databook"),
        Tag("adventure", "adventure"),
        Tag("y học", "y-hoc"),
        Tag("miễn bản quyền", "mien-ban-quyen"),
        Tag("josei", "josei"),
        Tag("psychological", "psychological"),
        Tag("anime", "anime"),
        Tag("yuri", "yuri"),
        Tag("yonkoma", "yonkoma"),
        Tag("quân đội", "quan-doi"),
        Tag("nữ giả nam", "nu-gia-nam"),
        Tag("chính trị", "chinh-tri"),
        Tag("tuyển tập", "tuyen-tap"),
        Tag("tu tiên", "tu-tien"),
        Tag("vô cp", "vo-cp"),
        Tag("xuyên không", "xuyen-khong"),
        Tag("việt nam", "viet-nam"),
        Tag("toán học", "toan-hoc"),
        Tag("tình yêu không được đáp lại", "tinh-yeu-khong-duoc-dap-lai"),
        Tag("tình yêu thuần khiết", "tinh-yeu-thuan-khiet"),
        Tag("thiếu niên", "thieu-nien"),
        Tag("tình yêu", "tinh-yeu"),
        Tag("chính kịch", "chinh-kich"),
        Tag("ngọt ngào", "ngot-ngao"),
        Tag("wholesome", "wholesome"),
        Tag("smut", "smut"),
        Tag("gore", "gore"),
        Tag("school life", "school-life"),
        Tag("slice of life", "slice-of-life"),
        Tag("tragedy", "tragedy"),
        Tag("mystery", "mystery"),
        Tag("atlus", "atlus"),
        Tag("sega", "sega"),
        Tag("rpg", "rpg"),
        Tag("chuyển thể", "chuyen-the"),
        Tag("historical", "historical"),
        Tag("medical", "medical"),
        Tag("ghosts", "ghosts"),
        Tag("thriller", "thriller"),
        Tag("animals", "animals"),
        Tag("survival", "survival"),
        Tag("samurai", "samurai"),
        Tag("virtual reality", "virtual-reality"),
        Tag("video games", "video-games"),
        Tag("monster girls", "monster-girls"),
        Tag("adaption", "adaption"),
        Tag("idol", "idol"),
    )
}
