plugins {
    alias(libs.plugins.thorkbm.android.feature)
}

android {
    namespace = "jr.brian.thorkbm.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:injection"))
    // The update sheet and its state are part of the settings API the panel and app host.
    api(project(":core:update"))
}
