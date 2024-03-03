package io.github.beerpsi.tachiyomi.extension.en.kmanga

import io.github.beerpsi.tachiyomi.multisrc.magapoke.Magapoke

class KManga : Magapoke("K Manga", "https://api.kmanga.kodansha.com", "en") {
    override val version = "5.3.0"

    override val popularMangaRankingId = 2
}
