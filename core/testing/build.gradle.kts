plugins {
    alias(libs.plugins.thorergo.android.library)
}

android {
    namespace = "com.thorergo.core.testing"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    api(project(":core:injection"))
    api(project(":core:display"))
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}
