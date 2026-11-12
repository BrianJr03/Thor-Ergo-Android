plugins {
    alias(libs.plugins.thorergo.android.library)
    alias(libs.plugins.thorergo.android.hilt)
}

android {
    namespace = "com.thorergo.core.display"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
