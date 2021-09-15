plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    setCompileSdkVersion(property("compileSdkVersion") as Int)

    defaultConfig {
        applicationId = "com.otaliastudios.transcoder.demo"
        minSdk = property("minSdkVersion") as Int
        targetSdk = property("targetSdkVersion") as Int
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":lib"))
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
}
