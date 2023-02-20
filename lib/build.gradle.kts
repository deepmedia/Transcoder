import io.deepmedia.tools.publisher.common.GithubScm
import io.deepmedia.tools.publisher.common.License
import io.deepmedia.tools.publisher.common.Release
import io.deepmedia.tools.publisher.sonatype.Sonatype

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("io.deepmedia.tools.publisher")
}

android {
    setCompileSdkVersion(property("compileSdkVersion") as Int)
    defaultConfig {
        minSdk = property("minSdkVersion") as Int
        targetSdk = property("targetSdkVersion") as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes["release"].isMinifyEnabled = false
}


dependencies {
    api("com.otaliastudios.opengl:egloo:0.6.1")
    api("androidx.annotation:annotation:1.2.0")

    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("org.mockito:mockito-android:2.28.2")
}

publisher {
    project.description = "Accelerated video transcoding using Android MediaCodec API without native code (no LGPL/patent issues)."
    project.artifact = "transcoder"
    project.group = "com.otaliastudios"
    project.url = "https://github.com/natario1/Transcoder"
    project.scm = GithubScm("natario1", "Transcoder")
    project.addLicense(License.APACHE_2_0)
    project.addDeveloper("natario1", "mat.iavarone@gmail.com")
    release.sources = Release.SOURCES_AUTO
    release.docs = Release.DOCS_AUTO
    release.version = "0.10.5"

    directory()

    sonatype {
        auth.user = "SONATYPE_USER"
        auth.password = "SONATYPE_PASSWORD"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }

    sonatype("snapshot") {
        repository = Sonatype.OSSRH_SNAPSHOT_1
        release.version = "latest-SNAPSHOT"
        auth.user = "SONATYPE_USER"
        auth.password = "SONATYPE_PASSWORD"
        signing.key = "SIGNING_KEY"
        signing.password = "SIGNING_PASSWORD"
    }
}
