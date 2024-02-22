plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
    id("io.gitlab.arturbosch.detekt")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.multisrc.${project.name}"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
        }
    }

    buildFeatures {
        resValues = false
        shaders = false
    }

    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
    ignoreFailures = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

repositories {
    mavenCentral()
}

// TODO: use versionCatalogs.named("libs") in Gradle 8.5
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    compileOnly(libs.findBundle("common").get())
}

tasks {
    preBuild {
        dependsOn(detekt)
    }
}
