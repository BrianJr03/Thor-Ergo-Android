plugins {
    alias(libs.plugins.thorkbm.android.feature)
}

android {
    namespace = "jr.brian.thorkbm.feature.setup"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:display"))
    implementation(project(":core:injection"))
    implementation(project(":feature:panel"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
