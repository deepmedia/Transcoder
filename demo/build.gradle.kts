plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.otaliastudios.transcoder.demo"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":lib"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
