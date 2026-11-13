plugins {
    alias(libs.plugins.thorkbm.android.library)
    alias(libs.plugins.thorkbm.android.hilt)
}

android {
    namespace = "jr.brian.thorkbm.core.update"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
