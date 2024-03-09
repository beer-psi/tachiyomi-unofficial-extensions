package io.github.beerpsi.tachiyomi.extension.all.smbshare

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class SMBShareFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val first = SMBShare("")
        val extraSources = first.preferences.extraSources

        return buildList {
            add(first)
            for (i in 0 until extraSources) {
                add(SMBShare("${i + 2}"))
            }
        }
    }
}
