import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class RatatoskrKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        extensions.configure<KtlintExtension> {
            filter {
                exclude("**/api/generated/**")
            }
        }

        extensions.configure<LibraryExtension> {
            namespace = "com.ratatoskr.mobile.shared"
            compileSdk = 36
            defaultConfig.minSdk = 26
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(17)
            androidTarget {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            iosArm64()
            iosSimulatorArm64()
            targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).configureEach {
                binaries.framework {
                    baseName = "Shared"
                    isStatic = true
                }
            }
            compilerOptions.allWarningsAsErrors.set(true)
        }
    }
}
