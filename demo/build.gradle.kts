plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "2.0.0"
}

android {
    namespace = "com.otaliastudios.transcoder.demo"
    compileSdk = 34
    defaultConfig {
        minSdk = 18
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":lib"))
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
}
