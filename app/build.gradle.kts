plugins {
    alias(libs.plugins.thorergo.android.application)
    alias(libs.plugins.thorergo.android.compose)
    alias(libs.plugins.thorergo.android.hilt)
}

android {
    namespace = "com.thorergo"

    defaultConfig {
        applicationId = "com.thorergo"
        versionCode = 2
        versionName = rootProject.file("VERSION").readText().trim()
    }

    // The interface language is chosen in the app, so every translation must ship in the APK.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:injection"))
    implementation(project(":feature:panel"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:setup"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
