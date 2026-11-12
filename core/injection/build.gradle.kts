plugins {
    alias(libs.plugins.thorergo.android.library)
    alias(libs.plugins.thorergo.android.hilt)
}

android {
    namespace = "com.thorergo.core.injection"
    // The app sets the real label per build type; this default only serves the library's own builds.
    defaultConfig.manifestPlaceholders["appLabel"] = "Thor Ergo"
    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:display"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
}
