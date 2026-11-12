plugins {
    alias(libs.plugins.thorergo.android.library)
    alias(libs.plugins.thorergo.android.hilt)
}

android {
    namespace = "com.thorergo.core.injection"
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
