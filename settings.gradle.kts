pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ThorErgo"

include(":app")
include(":core:model")
include(":core:keyboard")
include(":core:trackpad")
include(":core:pinyin")
include(":core:data")
include(":core:display")
include(":core:injection")
include(":core:designsystem")
include(":core:testing")
include(":feature:panel")
include(":feature:settings")
include(":feature:setup")
