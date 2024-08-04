plugins {
    id("com.android.library")
    kotlin("android")
    id("io.deepmedia.tools.deployer") version "0.14.0-local-alpha1"
    id("org.jetbrains.dokka") version "1.9.20"
}

android {
    namespace = "com.otaliastudios.transcoder"
    compileSdk = 34
    defaultConfig {
        minSdk = 18
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        targetSdk = 23
    }
    publishing {
        singleVariant("release")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("com.otaliastudios.opengl:egloo:0.6.1")
    api("androidx.annotation:annotation:1.8.1")

    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("org.mockito:mockito-android:2.28.2")

    dokkaPlugin("org.jetbrains.dokka:android-documentation-plugin:1.9.20")
}

val javadocs = tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

deployer {
    verbose = true

    content {
        component {
            fromSoftwareComponent("release")
            kotlinSources()
            docs(javadocs)
        }
    }

    projectInfo {
        groupId = "com.otaliastudios"
        artifactId = "transcoder"
        release.version = "0.10.5"
        release.tag = "v0.10.5"
        description = "Accelerated video transcoding using Android MediaCodec API without native code (no LGPL/patent issues)."
        url = "https://github.com/deepmedia/Transcoder"
        scm.fromGithub("deepmedia", "Transcoder")
        license(apache2)
        developer("natario1", "mattia@deepmedia.io", "DeepMedia", "https://deepmedia.io")
    }

    signing {
        key = secret("SIGNING_KEY")
        password = secret("SIGNING_PASSWORD")
    }

    // use "deployLocal" to deploy to local maven repository
    localSpec {
        directory.set(rootProject.layout.buildDirectory.get().dir("inspect"))
    }

    // use "deployNexus" to deploy to OSSRH / maven central
    nexusSpec {
        auth.user = secret("SONATYPE_USER")
        auth.password = secret("SONATYPE_PASSWORD")
        syncToMavenCentral = true
    }

    // use "deployNexusSnapshot" to deploy to sonatype snapshots repo
    nexusSpec("snapshot") {
        auth.user = secret("SONATYPE_USER")
        auth.password = secret("SONATYPE_PASSWORD")
        repositoryUrl = ossrhSnapshots1
        release.version = "latest-SNAPSHOT"
    }

    // use "deployGithub" to deploy to github packages
    githubSpec {
        repository = "Transcoder"
        owner = "deepmedia"
        auth {
            user = secret("GHUB_USER")
            token = secret("GHUB_PERSONAL_ACCESS_TOKEN")
        }
    }
}
