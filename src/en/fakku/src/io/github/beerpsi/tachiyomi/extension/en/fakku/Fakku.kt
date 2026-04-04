package io.github.beerpsi.tachiyomi.extension.en.fakku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

class Fakku : ParsedHttpSource() {

    override val name = "FAKKU"

    override val lang = "en"

    override val baseUrl = "https://www.fakku.net"

    private val apiUrl = "https://reader.fakku.net"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("DNT", "1")

    override val client = network.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .rateLimit(permits = 3)
        .build()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(SortFilter(2)),
    )

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(SortFilter(0)),
    )

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH).trim()
        val url = "/hentai/$id"

        fetchMangaDetails(SManga.create().apply { this.url = url })
            .map {
                it.url = url
                MangasPage(listOf(it), false)
            }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment(query)
            }

            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }

            val incl = mutableListOf<String>()
            val excl = mutableListOf<String>()
            val artists = mutableListOf<String>()

            filters.forEach {
                when (it) {
                    is SortFilter -> when (it.values[it.state]) {
                        "New" -> addQueryParameter("sort", "new")
                        "Trending" -> addQueryParameter("sort", "trending")
                        "Popular" -> addQueryParameter("sort", "popular")
                    }

                    is TagFilter -> it.state.forEach { tag ->
                        when (tag.state) {
                            Filter.TriState.STATE_INCLUDE -> incl.add(tag.id)
                            Filter.TriState.STATE_EXCLUDE -> excl.add(tag.id)
                            else -> {}
                        }
                    }

                    is ArtistFilter -> it.state.forEach { artist ->
                        if (artist.state) {
                            artists.add(artist.id)
                        }
                    }

                    else -> {}
                }
            }

            addQueryParameter("artists", artists.joinToString(","))
            addQueryParameter("tags", incl.joinToString(","))
            addQueryParameter("not_tags", excl.joinToString(","))
        }.build()

        return GET(url)
    }

    override fun searchMangaSelector() = "div[id^=content-]"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("div.text-left > a[href^=/hentai]")!!

        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst("img.object-cover")?.attr("src")
    }

    override fun searchMangaNextPageSelector() = "div.pagination-row a svg.icon-angle-right"

    @Suppress("NestedBlockDepth")
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        thumbnail_url = document.selectFirst("figure[itemtype='https://schema.org/ImageObject'] picture source")?.attr("srcset")
        genre = document.select("div.table.w-full.text-sm > div > a.inline-block").joinToString { it.ownText() }

        val extras = mutableMapOf<String, String>()

        document.select(
            "div.table.w-full.text-sm",
        ).forEach {
            val key = it.selectFirst("div.inline-block.w-24.text-left.align-top")?.text()
            val value = it.selectFirst("div.table-cell.w-full.align-top.text-left.space-y-2")?.text()

            when (key) {
                "Artist" -> {
                    author = value
                    artist = value
                }

                null -> if (value != null) {
                    description = value
                }

                else -> if (value != null) {
                    extras[key] = value
                }
            }
        }

        if (description == null) {
            description = ""
        } else {
            description += "\n\n"
        }

        extras.entries.forEach {
            description += "${it.key}: ${it.value}\n"
        }

        description = description!!.trim()
    }

    override fun chapterListParse(response: Response) = listOf(
        SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(
                response.request.url.newBuilder()
                    .addPathSegments("read")
                    .build()
                    .toString(),
            )
        },
    )

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document) = throw UnsupportedOperationException()

    @Suppress("CyclomaticComplexMethod")
    override fun pageListParse(response: Response): List<Page> {
        val entryId = response.request.url.pathSegments.dropLast(1).last()

        // The document fetch is still needed to ensure the correct cookies are set.
        val resp = client.newCall(
            GET(
                "$apiUrl/hentai/$entryId/read",
                headersBuilder()
                    .add("Accept", "*/*")
                    .add("Sec-Fetch-Dest", "empty")
                    .add("Sec-Fetch-Mode", "cors")
                    .add("Sec-Fetch-Site", "same-site")
                    .build(),
            ),
        )
            .execute()
            .parseAs<ReaderResponse>()

        if (resp.keyHash != null && resp.keyData != null) {
            val zid = client.cookieJar.loadForRequest(response.request.url).find { it.name == "fakku_zid" }?.value

            if (zid == null) {
                throw Exception("Images are scrambled, but fakku_zid cookie not found.")
            }

            val decryptionKey = calculateDecryptionKey(zid, resp.keyHash)
            val keyData = Base64.decode(resp.keyData, Base64.DEFAULT)
                .decryptXorCipher(decryptionKey)
                .toString(Charsets.UTF_8)
                .parseAs<Map<String, List<Int>>>()

            return resp.pages.values.map { page ->
                val fragment = keyData[page.page.toString()]
                    ?.joinToString(",")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "#page,$it" }
                    .orEmpty()

                Page(page.page, "", "${page.image}$fragment")
            }
        } else {
            return resp.pages.values.map {
                Page(it.page, "", it.image)
            }
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        val response = chain.proceed(request)

        if (fragment == null || !fragment.startsWith("page,")) {
            return response
        }

        val arr = fragment.split(",").drop(1).map(String::toInt).toMutableList()
        val seed = arr.removeAt(arr.lastIndex)
        val reordered = arr.shuffle(seed)

        val pieceOrderSeed = reordered[2]
        val width = reordered[0] xor pieceOrderSeed
        val height = reordered[1] xor pieceOrderSeed
        val isHorizontal = width > height
        val smallerEdge = if (isHorizontal) height else width
        val offset = 128 * ceil(smallerEdge.toDouble() / IMAGE_PIECE_SIZE).toInt() - smallerEdge
        val widthPieces = ceil(width.toDouble() / IMAGE_PIECE_SIZE).toInt()
        val heightPieces = ceil(height.toDouble() / IMAGE_PIECE_SIZE).toInt()

        val input = response.body.use { BitmapFactory.decodeStream(it.byteStream()) }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val pieceOrder = (0 until widthPieces * heightPieces)
            .toMutableList()
            .randomize(pieceOrderSeed)

        for (i in pieceOrder.indices) {
            val sxPiece = pieceOrder[i] % widthPieces
            val syPiece = (pieceOrder[i] - sxPiece) / widthPieces
            val dxPiece = i % widthPieces
            val dyPiece = (i - dxPiece) / widthPieces
            val lastPiece = if (isHorizontal) {
                dyPiece == heightPieces - 1
            } else {
                dxPiece == widthPieces - 1
            }
            var dx = dxPiece * IMAGE_PIECE_SIZE
            var dy = dyPiece * IMAGE_PIECE_SIZE

            if (lastPiece) {
                dx -= if (isHorizontal) 0 else offset
                dy -= if (isHorizontal) offset else 0
            }

            val sx = sxPiece * IMAGE_PIECE_SIZE
            val sy = syPiece * IMAGE_PIECE_SIZE

            val srcRect = Rect(sx, sy, sx + IMAGE_PIECE_SIZE, sy + IMAGE_PIECE_SIZE)
            val dstRect = Rect(dx, dy, dx + IMAGE_PIECE_SIZE, dy + IMAGE_PIECE_SIZE)
            canvas.drawBitmap(input, srcRect, dstRect, null)
        }

        val outputArray = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, outputArray)

        return response.newBuilder()
            .body(outputArray.toByteArray().toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    override fun getFilterList(): FilterList {
        fetchFilterOptions()

        val filters = mutableListOf<Filter<*>>(SortFilter())

        if (fetchFilterStatus != FetchFilterStatus.FETCHED) {
            val message = if (fetchFilterStatus == FetchFilterStatus.NOT_FETCHED && fetchFiltersAttempts >= 3) {
                "Failed to fetch filtering options"
            } else {
                "Press 'Reset' to show filtering options"
            }

            filters.add(0, Filter.Header(message))
            filters.add(1, Filter.Separator())
        } else {
            filters.add(TagFilter(tags.map { Tag(it.name, it.id) }))
            filters.add(ArtistFilter(artists.map { Artist(it.name, it.id) }))
        }

        return FilterList(filters)
    }

    private data class FilterOption(val name: String, val id: String)
    private class Tag(name: String, val id: String) : Filter.TriState(name)
    private class Artist(name: String, val id: String) : Filter.CheckBox(name)
    private class TagFilter(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags)
    private class ArtistFilter(artists: List<Artist>) : Filter.Group<Artist>("Artists", artists)
    private class SortFilter(state: Int = 2) :
        Filter.Select<String>(
            "Sort by",
            arrayOf("New", "Trending", "Popular"),
            state,
        )

    private enum class FetchFilterStatus {
        NOT_FETCHED,
        FETCHING,
        FETCHED,
    }

    private var tags = emptyList<FilterOption>()
    private var artists = emptyList<FilterOption>()

    private var fetchFilterStatus = FetchFilterStatus.NOT_FETCHED
    private var fetchFiltersAttempts = 0

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("TooGenericExceptionCaught")
    private fun fetchFilterOptions() {
        if (fetchFilterStatus != FetchFilterStatus.NOT_FETCHED ||
            fetchFiltersAttempts >= MAX_FETCH_FILTER_ATTEMPTS
        ) {
            return
        }

        fetchFilterStatus = FetchFilterStatus.FETCHING
        fetchFiltersAttempts++

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val document = client.newCall(GET("$baseUrl/search")).await().asJsoup()

                tags = document.select("select#tags > option").map {
                    FilterOption(it.text(), it.attr("value"))
                }
                artists = document.select("select#artists > option").map {
                    FilterOption(it.text(), it.attr("value"))
                }
                fetchFilterStatus = FetchFilterStatus.FETCHED
            } catch (e: Exception) {
                fetchFilterStatus = FetchFilterStatus.NOT_FETCHED
                Log.e("Fakku", "Failed to fetch filtering options", e)
            }
        }
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}

private const val MAX_FETCH_FILTER_ATTEMPTS = 3
private const val IMAGE_PIECE_SIZE = 128
private const val COMPRESS_QUALITY = 100
