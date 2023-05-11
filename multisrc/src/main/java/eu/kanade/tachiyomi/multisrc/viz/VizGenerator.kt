package eu.kanade.tachiyomi.multisrc.viz

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class VizGenerator : ThemeSourceGenerator {

    override val themePkg = "viz"

    override val themeClass = "Viz"

    override val baseVersionCode: Int = 6

    override val sources = listOf(
        SingleLang("VIZ Manga (App)", "https://www.viz.com/vizmanga", "en", className = "VizMangaApp"),
        SingleLang("Shonen Jump (App)", "https://www.viz.com/shonenjump", "en", className = "ShonenJumpApp"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            VizGenerator().createAll()
        }
    }
}
