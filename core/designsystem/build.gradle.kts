plugins {
    alias(libs.plugins.thorkbm.android.library)
    alias(libs.plugins.thorkbm.android.compose)
}

android {
    namespace = "jr.brian.thorkbm.core.designsystem"
}

dependencies {
    api(project(":core:model"))
}
