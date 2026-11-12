import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** A UI feature: Android library + Compose + Hilt + the shared core modules every feature needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("thorkbm.android.library")
        pluginManager.apply("thorkbm.android.compose")
        pluginManager.apply("thorkbm.android.hilt")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
