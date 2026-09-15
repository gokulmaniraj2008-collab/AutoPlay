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
        versionCode = 7
        versionName = "0.7.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\"")
        }
        getByName("release") {
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\"")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.17.0")
}
