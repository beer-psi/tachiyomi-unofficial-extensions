package io.github.beerpsi.tachiyomi.extension.all.smbshare

import android.app.Application
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import io.github.beerpsi.tachiyomi.extension.all.smbshare.models.COMIC_INFO_FILE
import io.github.beerpsi.tachiyomi.extension.all.smbshare.models.ComicInfo
import io.github.beerpsi.tachiyomi.extension.all.smbshare.models.MangaDetails
import io.github.beerpsi.tachiyomi.extension.all.smbshare.models.copyFromComicInfo
import io.github.beerpsi.tachiyomi.extension.all.smbshare.smbj.FileChannel
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import nl.adaptivity.xmlutil.AndroidXmlReader
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import org.apache.commons.compress.archivers.zip.ZipFile
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.api.getOrNull
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days

// The only reason for using a HttpSource instead of a CatalogueSource is that I need an entry point
// for hijacking loading images; the HTTP client serves well in this respect.
class SMBShare(private val suffix: String = "") : HttpSource(), ConfigurableSource, UnmeteredSource {

    override val name by lazy {
        val displayNameSuffix = preferences.displayName
            .ifEmpty { suffix }
            .let { if (it.isNotEmpty()) " ($it)" else "" }

        "SMB Share$displayNameSuffix"
    }

    override val lang = "all"

    override val supportsLatest = true

    override val baseUrl = "https://en.wikipedia.org/wiki/Server_Message_Block"

    @Suppress("MagicNumber")
    override val id by lazy {
        val key = "smb share${if (suffix.isNotBlank()) " ($suffix)" else ""}/all/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    override val client = network.client.newBuilder()
        .addInterceptor(SMBImageInterceptor(this))
        .build()

    internal val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val json: Json by injectLazy()
    private val xml: XML by lazy {
        Injekt.getOrNull<XML>() ?: XML {
            defaultPolicy {
                ignoreUnknownChildren()
            }
            autoPolymorphic = true
            xmlDeclMode = XmlDeclMode.Charset
            indent = 2
            xmlVersion = XmlVersion.XML10
        }
    }

    internal val smbConfig = SmbConfig.builder()
        .withTimeout(SMB_TIMEOUT, TimeUnit.SECONDS)
        .withSoTimeout(SMB_SOCKET_TIMEOUT, TimeUnit.SECONDS)
        .build()
    private val smbClient = SMBClient(smbConfig)
    private var smbSession: Session? = null
    internal var diskShare: DiskShare? = null

    private val thumbnailCache = mutableMapOf<String, String>()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        fetchSearchManga(page, "", LATEST_FILTERS)

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = Observable.fromCallable {
        connectToShare()

        val lastModifiedLimit = if (filters === LATEST_FILTERS) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }
        val mangaDirs = diskShare!!.list(preferences.rootDirectory)
            .filter { it.isDirectory && !it.fileName.startsWith(".") }
            .distinctBy { it.fileName }
            .filter {
                when {
                    lastModifiedLimit == 0L -> query.isBlank() || it.fileName.contains(query, true)
                    else -> it.lastWriteTime.toEpochMillis() >= lastModifiedLimit
                }
            }
            .toMutableList()

        filters.forEach { filter ->
            when (filter) {
                is OrderBy.Title -> mangaDirs.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
                is OrderBy.Latest -> mangaDirs.sortBy { it.lastWriteTime.toEpochMillis() }
                else -> {}
            }

            if (filter is OrderBy && !filter.state!!.ascending) {
                mangaDirs.reverse()
            }
        }

        val manga = runBlocking {
            mangaDirs.map { mangaDir ->
                async {
                    SManga.create().apply {
                        url = "${preferences.rootDirectory}/${mangaDir.fileName}"
                        title = mangaDir.fileName

                        // Realistically we don't have all the I/O in the world to open an image
                        // to find what the thing is, so we just find the first file named "cover"
                        // and *might* be an image.
                        diskShare!!.list(url, "cover.*")
                            .findCover()
                            ?.let {
                                thumbnail_url = SMBImageInterceptorHelper.createUrl(url, it.fileName).toString()
                            }
                    }
                }
            }.awaitAll()
        }

        MangasPage(manga, false)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl

    @Suppress("CyclomaticComplexMethod")
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.fromCallable {
        connectToShare()

        manga.thumbnail_url = thumbnailCache[manga.url] ?: manga.thumbnail_url

        try {
            val mangaDirFiles = diskShare!!.list(manga.url).orEmpty()

            mangaDirFiles
                .findCover()
                ?.let {
                    manga.thumbnail_url = SMBImageInterceptorHelper.createUrl(manga.url, it.fileName).toString()
                }

            val comicInfoFile = mangaDirFiles
                .firstOrNull { it.fileName == COMIC_INFO_FILE }
            val legacyJsonDetailsFile = mangaDirFiles
                .firstOrNull { it.fileName.endsWith(".json") }

            when {
                comicInfoFile != null -> {
                    val comicInfo = diskShare!!.useFileInputStream("${manga.url}/${comicInfoFile.fileName}") { stream ->
                        AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use {
                            xml.decodeFromReader<ComicInfo>(it)
                        }
                    }

                    manga.copyFromComicInfo(comicInfo)
                }

                legacyJsonDetailsFile != null -> {
                    diskShare!!.useFileInputStream("${manga.url}/${legacyJsonDetailsFile.fileName}") { stream ->
                        json.decodeFromStream<MangaDetails>(stream).run {
                            title?.let { manga.title = it }
                            author?.let { manga.author = it }
                            artist?.let { manga.artist = it }
                            description?.let { manga.description = it }
                            genre?.let { manga.genre = it.joinToString() }
                            status?.let { manga.status = it }
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e("SMBShare", "Metadata for ${manga.title} is not well-formed.", e)
        } catch (e: SerializationException) {
            Log.e("SMBShare", "Could not decode metadata for ${manga.title}.", e)
        } catch (e: IOException) {
            Log.e("SMBShare", "Could not fetch metadata for ${manga.title}.", e)
        }

        manga
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        connectToShare()

        val chapters = diskShare!!.list(manga.url).orEmpty()
            .filter {
                (it.isDirectory && !it.fileName.startsWith(".")) ||
                    it.fileName.endsWith(".zip") ||
                    it.fileName.endsWith(".cbz")
            }
            .sortedWith { f1, f2 ->
                f2.fileName.compareToCaseInsensitiveNaturalOrder(f1.fileName)
            }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.fileName}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.fileName
                    } else {
                        chapterFile.fileName.substringBeforeLast(".")
                    }
                    date_upload = chapterFile.lastWriteTime.toEpochMillis()
                }
            }

        if (manga.thumbnail_url.isNullOrBlank()) {
            chapters.lastOrNull()?.let { chapter ->
                fetchPageListInner(chapter).firstOrNull()?.let {
                    thumbnailCache.put(manga.url, it.imageUrl!!)
                }
            }
        }

        chapters
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        connectToShare()
        fetchPageListInner(chapter)
    }

    private fun fetchPageListInner(chapter: SChapter): List<Page> {
        val fileInformation: FileBasicInformation = diskShare!!.open(
            chapter.url,
            setOf(AccessMask.FILE_READ_ATTRIBUTES),
            null,
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).use {
            it.getFileInformation(FileBasicInformation::class.java)
        }

        return if (fileInformation.isDirectory) {
            diskShare!!.list(chapter.url)
                .filter { !it.isDirectory && mightBeImage(it.fileName) }
                .sortedWith { f1, f2 ->
                    f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName)
                }
                .mapIndexed { i, file ->
                    Page(i, imageUrl = SMBImageInterceptorHelper.createUrl(chapter.url, file.fileName).toString())
                }
        } else {
            val file = diskShare!!.openFile(
                chapter.url,
                setOf(AccessMask.FILE_READ_DATA),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            val channel = FileChannel(
                file,
                smbConfig.readBufferSize,
                smbConfig.readTimeout,
            )
            val zip = ZipFile(
                channel,
                chapter.url,
                "UTF-8",
                true,
                true,
            )

            zip.entries.asSequence()
                .filter { !it.isDirectory && mightBeImage(it.name) }
                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                .mapIndexed { i, entry ->
                    val imageUrl = SMBImageInterceptorHelper.createUrl(chapter.url).newBuilder()
                        .fragment(entry.name)
                        .toString()

                    Page(i, imageUrl = imageUrl)
                }
                .toList()
        }
    }

    override fun getFilterList() = POPULAR_FILTERS

    @Suppress("LongMethod")
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (suffix.isEmpty()) {
            ListPreference(screen.context).apply {
                key = PREF_EXTRA_SOURCES_COUNT
                title = "Number of extra sources"
                summary = "Number of additional sources to create. There will always be at least one SMB share."
                entries = PREF_EXTRA_SOURCES_ENTRIES
                entryValues = PREF_EXTRA_SOURCES_ENTRIES

                setDefaultValue("0")
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    true
                }
            }.also(screen::addPreference)
        }

        screen.addEditTextPreference(
            key = PREF_DOMAIN,
            title = "Domain",
            summary = preferences.domain.ifEmpty { "SMB share address" },
            default = "",
            onChange = { disconnectFromShare() },
        )
        screen.addEditTextPreference(
            key = PREF_PORT,
            title = "Port",
            summary = preferences.port.toString(),
            default = "445",
            validate = {
                val value = it.toIntOrNull() ?: return@addEditTextPreference false

                value in (1..65535)
            },
            validationMessage = "Not a valid port number, must be between 1 and 65535",
            onChange = { disconnectFromShare() },
        )
        screen.addEditTextPreference(
            key = PREF_SHARE_NAME,
            title = "Share name",
            summary = preferences.shareName.ifEmpty { "SMB share name" },
            default = "",
            onChange = { disconnectFromShare() },
        )
        screen.addEditTextPreference(
            key = PREF_USERNAME,
            title = "Username",
            summary = preferences.username.ifEmpty { "User account name" },
            default = "",
            onChange = { disconnectFromShare() },
        )
        screen.addEditTextPreference(
            key = PREF_PASSWORD,
            title = "Password",
            summary = if (preferences.password.isEmpty()) {
                "User account password"
            } else {
                "*".repeat(preferences.password.length)
            },
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            default = "",
            onChange = { disconnectFromShare() },
        )
        screen.addEditTextPreference(
            key = PREF_ROOT_DIRECTORY,
            title = "Manga folder",
            summary = preferences.rootDirectory
                .ifEmpty {
                    "The folder name where series are located. They must follow the same structure as the local source."
                },
            default = "",
        )
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun connectToShare() {
        if (smbSession?.connection?.isConnected == true && diskShare?.isConnected == true) {
            return
        }

        val authContext = AuthenticationContext(
            preferences.username,
            preferences.password.toCharArray(),
            null,
        )

        smbSession = smbClient.connect(preferences.domain, preferences.port).authenticate(authContext)

        val share = smbSession!!.connectShare(preferences.shareName) as? DiskShare?

        if (share == null || !share.isConnected) {
            throw Exception("Could not connect to SMB share, or share was not a disk share")
        }

        diskShare = share
    }

    private fun disconnectFromShare() {
        diskShare?.close()
        smbSession?.close()
        diskShare = null
        smbSession = null
    }
}

private const val SMB_TIMEOUT = 60L
private const val SMB_SOCKET_TIMEOUT = 120L
private const val MAX_EXTRA_SOURCES = 10
private val PREF_EXTRA_SOURCES_ENTRIES = (0..MAX_EXTRA_SOURCES).map { it.toString() }.toTypedArray()
private val LATEST_THRESHOLD = 7.days.inWholeMicroseconds
private val POPULAR_FILTERS = FilterList(OrderBy.Title())
private val LATEST_FILTERS = FilterList(OrderBy.Latest())

sealed class OrderBy(selection: Selection) : Filter.Sort(
    "Order by",
    arrayOf("Title", "Date"),
    selection,
) {
    class Title : OrderBy(Selection(0, true))
    class Latest : OrderBy(Selection(1, false))
}

internal fun <R> DiskShare.useFileInputStream(fileName: String, block: (InputStream) -> R): R = openFile(
    fileName,
    setOf(AccessMask.FILE_READ_DATA),
    null,
    setOf(SMB2ShareAccess.FILE_SHARE_READ),
    SMB2CreateDisposition.FILE_OPEN,
    null,
)
    .use { it.inputStream.use(block) }

private val FileIdBothDirectoryInformation.isDirectory: Boolean
    get() = (fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L

private val FileBasicInformation.isDirectory: Boolean
    get() = (fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L

private fun String.compareToCaseInsensitiveNaturalOrder(other: String): Int {
    val comparator = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()

    return comparator.compare(this, other)
}

private fun List<FileIdBothDirectoryInformation>.findCover() = this.firstOrNull {
    it.fileName.startsWith("cover.") && mightBeImage(it.fileName)
}

private fun mightBeImage(fileName: String): Boolean {
    return URLConnection.guessContentTypeFromName(fileName)?.startsWith("image/") == true
}
