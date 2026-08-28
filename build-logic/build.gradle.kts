plugins {
    `kotlin-dsl`
}

group = "com.ratatoskr.mobile.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.11.1")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ratatoskr.android.application"
            implementationClass = "RatatoskrAndroidApplicationPlugin"
        }
        register("kmpLibrary") {
            id = "ratatoskr.kmp.library"
            implementationClass = "RatatoskrKmpLibraryPlugin"
        }
    }
}
