plugins {
    alias(libs.plugins.thorkbm.jvm.library)
}

dependencies {
    testImplementation(project(":core:keyboard"))
}
