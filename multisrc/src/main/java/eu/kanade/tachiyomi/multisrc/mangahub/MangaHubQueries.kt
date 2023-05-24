package eu.kanade.tachiyomi.multisrc.mangahub

import kotlinx.serialization.Serializable

object MangaHubQueries {
    private val whitespace = Regex("""\s""")

    fun searchQuery(mangaSource: String, alt: Boolean, q: String, genre: String, mod: String, offset: Int): String {
        val args = mutableListOf<String>("x: $mangaSource")

        if (alt) {
            args.add("alt: true")
        }

        if (q.isNotEmpty()) {
            args.add("q: \"${q.replace("\"", "\\\"")}\"")
        }

        if (genre.isNotEmpty()) {
            args.add("genre: \"$genre\"")
        }

        if (mod.isNotEmpty()) {
            args.add("mod: $mod")
        } else {
            args.add("mod: POPULAR")
        }

        args.add("count: true")
        args.add("offset: $offset")

        return """
            {
                search(${args.joinToString(",")}) {
                    rows {
        			    id,
        			    rank,
        			    title,
        			    slug,
        			    status,
        			    author,
        			    genres,
        			    image,
        			    latestChapter,
        			    unauthFile,
        			    createdDate
        		    },
        		    count
                }
            }
        """.replace(whitespace, "")
    }

    fun popularQuery(mangaSource: String, offset: Int) = searchQuery(mangaSource, false, "", "", "POPULAR", offset)

    fun latestQuery(mangaSource: String, offset: Int) = searchQuery(mangaSource, false, "", "", "LATEST", offset)

    fun mangaDetailsQuery(mangaSource: String, slug: String) = """
        {
        	manga(x: $mangaSource, slug: "$slug") {
        		id,
        		rank,
        		title,
        		slug,
        		status,
        		image,
        		latestChapter,
        		author,
        		artist,
        		genres,
        		description,
        		alternativeTitle,
        		mainSlug,
        		isYaoi,
        		isPorn,
        		isSoftPorn,
        		unauthFile,
        		noCoverAd,
        		isLicensed,
        		createdDate,
        		updatedDate
        	}
        }
    """.replace(whitespace, "")

    fun chapterListQuery(mangaSource: String, slug: String) = """
        {
        	manga(x: $mangaSource, slug: "$slug") {
        		chapters {
        			id,
        			number,
        			title,
        			slug,
        			date
        		}
        	}
        }

    """.replace(whitespace, "")

    fun chapterPagesQuery(mangaSource: String, slug: String, chapter: Float) = """
        {
        	chapter(x: $mangaSource, slug: "$slug", number: ${chapter.toString().replace(".0", "")}) {
        		id,
        		title,
        		mangaID,
        		number,
        		slug,
        		date,
        		pages,
        		noAd,
        		manga {
        			id,
        			title,
        			slug,
        			mainSlug,
        			author,
        			isWebtoon,
        			isYaoi,
        			isPorn,
        			isSoftPorn,
        			unauthFile,
        			isLicensed
        		}
        	}
        }
    """.trimIndent()
}

@Serializable
data class ApiErrorMessages(
    val message: String,
)

@Serializable
data class ApiChapterPagesResponse(
    val data: ApiChapterData?,
    val errors: List<ApiErrorMessages>?,
)

@Serializable
data class ApiChapterData(
    val chapter: ApiChapter?,
)

@Serializable
data class ApiChapter(
    val pages: String,
)

@Serializable
data class ApiChapterPages(
    val p: String,
    val i: List<String>,
)
