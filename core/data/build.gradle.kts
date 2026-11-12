plugins {
    alias(libs.plugins.thorkbm.android.library)
    alias(libs.plugins.thorkbm.android.hilt)
}

android {
    namespace = "jr.brian.thorkbm.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
