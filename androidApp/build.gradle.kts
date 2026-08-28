plugins {
    id("ratatoskr.android.application")
}

android {
    namespace = "com.ratatoskr.mobile"

    defaultConfig {
        applicationId = "com.ratatoskr.mobile"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
