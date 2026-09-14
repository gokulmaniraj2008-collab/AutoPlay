plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gokul.autoplay"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.gokul.autoplay"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"

        val spotifyClientId = project.findProperty("SPOTIFY_CLIENT_ID")?.toString() ?: ""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.spotify.android:app-remote:0.8.0")
    implementation("com.google.code.gson:gson:2.13.2")
}
