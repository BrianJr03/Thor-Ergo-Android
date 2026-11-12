plugins {
    alias(libs.plugins.thorergo.android.feature)
}

android {
    namespace = "com.thorergo.feature.setup"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:display"))
    implementation(project(":core:injection"))
    implementation(project(":feature:panel"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
