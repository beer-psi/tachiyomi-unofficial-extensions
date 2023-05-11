package eu.kanade.tachiyomi.extension.en.shonenjumpapp

import eu.kanade.tachiyomi.multisrc.viz.Viz

class ShonenJumpApp : Viz("Shonen Jump (App)", "https://www.viz.com/shonenjump", "en") {

    override val androidAppPackageName = "com.viz.wsj.android"

    override val subscriptionInfoPrefix = ""

    override val vizAppId = 3
}
