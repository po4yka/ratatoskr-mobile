plugins {
    id("ratatoskr.android.application")
}

val ratatoskrLinkHost = providers.gradleProperty("ratatoskr.linkHost").orElse("links.ratatoskr.test")

android {
    namespace = "com.ratatoskr.mobile"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.ratatoskr.mobile"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["ratatoskrLinkHost"] = ratatoskrLinkHost.get()
        buildConfigField("String", "RATATOSKR_LINK_HOST", "\"${ratatoskrLinkHost.get()}\"")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
