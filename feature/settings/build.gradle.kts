plugins {
    alias(libs.plugins.thorkbm.android.feature)
}

android {
    namespace = "jr.brian.thorkbm.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:injection"))
}
