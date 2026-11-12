plugins {
    alias(libs.plugins.thorkbm.android.library)
}

android {
    namespace = "jr.brian.thorkbm.core.testing"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    api(project(":core:injection"))
    api(project(":core:display"))
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}
