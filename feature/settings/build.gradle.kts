plugins {
    alias(libs.plugins.thorergo.android.feature)
}

android {
    namespace = "com.thorergo.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:injection"))
}
