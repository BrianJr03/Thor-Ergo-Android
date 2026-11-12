plugins {
    alias(libs.plugins.thorkbm.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:pinyin"))
    implementation(libs.kotlinx.serialization.json)
}
