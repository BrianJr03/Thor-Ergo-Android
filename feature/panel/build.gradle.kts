plugins {
    alias(libs.plugins.thorkbm.android.feature)
    alias(libs.plugins.roborazzi)
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

android {
    namespace = "jr.brian.thorkbm.feature.panel"
}

dependencies {
    implementation(project(":core:keyboard"))
    implementation(project(":core:trackpad"))
    implementation(project(":core:pinyin"))
    implementation(project(":core:data"))
    implementation(project(":core:display"))
    implementation(project(":core:injection"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
}
