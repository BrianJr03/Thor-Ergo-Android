plugins {
    alias(libs.plugins.thorergo.android.library)
    alias(libs.plugins.thorergo.android.hilt)
}

android {
    namespace = "com.thorergo.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
