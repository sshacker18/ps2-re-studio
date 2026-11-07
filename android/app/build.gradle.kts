plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.sshacker18.ps2restudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sshacker18.ps2restudio.v2"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "0.4.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
