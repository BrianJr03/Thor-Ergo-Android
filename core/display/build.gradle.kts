plugins {
    alias(libs.plugins.thorkbm.android.library)
    alias(libs.plugins.thorkbm.android.hilt)
}

android {
    namespace = "jr.brian.thorkbm.core.display"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
