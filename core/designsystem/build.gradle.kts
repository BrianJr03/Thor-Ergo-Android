plugins {
    alias(libs.plugins.thorergo.android.library)
    alias(libs.plugins.thorergo.android.compose)
}

android {
    namespace = "com.thorergo.core.designsystem"
}

dependencies {
    api(project(":core:model"))
}
