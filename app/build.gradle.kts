import java.util.Properties

plugins {
    alias(libs.plugins.thorergo.android.application)
    alias(libs.plugins.thorergo.android.compose)
    alias(libs.plugins.thorergo.android.hilt)
}

/**
 * Release signing comes from the git-ignored keystore.properties (see keystore.properties.example)
 * or, on CI, from THOR_ERGO_* environment variables. Without either, release builds are unsigned.
 */
data class ReleaseKey(val storeFile: File, val password: String, val alias: String)

val releaseKey: ReleaseKey? = run {
    val props = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    }
    fun value(key: String, env: String): String? = props.getProperty(key) ?: System.getenv(env)
    val store = value("storeFile", "THOR_ERGO_KEYSTORE") ?: return@run null
    val password = value("storePassword", "THOR_ERGO_KEYSTORE_PASSWORD")
        ?: value("storePasswordFile", "THOR_ERGO_KEYSTORE_PASSWORD_FILE")?.let { file(it).readText().trim() }
        ?: return@run null
    ReleaseKey(file(store), password, value("keyAlias", "THOR_ERGO_KEY_ALIAS") ?: "thor-ergo")
}

android {
    namespace = "com.thorergo"

    defaultConfig {
        applicationId = "com.thorergo"
        versionCode = 2
        versionName = rootProject.file("VERSION").readText().trim()
    }

    // Ship only the app's seven languages; libraries otherwise bring dozens of unused translations.
    androidResources {
        localeFilters += listOf("en", "pt", "es", "fr", "de", "it", "zh-rCN")
    }

    // The interface language is chosen in the app, so every translation must ship in the APK.
    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey.storeFile
                storePassword = releaseKey.password
                keyAlias = releaseKey.alias
                keyPassword = releaseKey.password
            }
        }
    }

    // Debug and release are separate apps (different ids, names and signatures), so both can be installed.
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Thor Ergo Debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            manifestPlaceholders["appLabel"] = "Thor Ergo"
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:injection"))
    implementation(project(":feature:panel"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:setup"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
