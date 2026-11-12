import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/** Pure Kotlin module: no Android dependencies, tests run on the plain JVM. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = ThorErgoSdk.JAVA
            targetCompatibility = ThorErgoSdk.JAVA
        }
        configureKotlin()
        addUnitTestDependencies()
    }
}
