import io.deepmedia.tools.deployer.model.Secret

plugins {
    id("com.android.library")
    id("io.deepmedia.tools.deployer")
}

android {
    namespace = "com.otaliastudios.transcoder"
    compileSdk = 34
    defaultConfig.minSdk = 21
    publishing { singleVariant("release") }
}

dependencies {
    api(project(":lib"))
}

deployer {
    content {
        component {
            fromSoftwareComponent("release")
            emptyDocs()
            emptySources()
        }
    }

    projectInfo {
        groupId = "com.otaliastudios"
        artifactId = "transcoder"
        release.version = "0.10.5" // change :lib and README
        description = "Accelerated video compression and transcoding on Android using MediaCodec APIs (no FFMPEG/LGPL licensing issues). Supports cropping to any dimension, concatenation, audio processing and much more."
        url = "https://opensource.deepmedia.io/transcoder"
        scm.fromGithub("deepmedia", "Transcoder")
        license(apache2)
        developer("Mattia Iavarone", "mattia@deepmedia.io", "DeepMedia", "https://deepmedia.io")
    }

    signing {
        key = secret("SIGNING_KEY")
        password = secret("SIGNING_PASSWORD")
    }

    // use "deployLocal" to deploy to local maven repository
    localSpec {
        directory.set(rootProject.layout.buildDirectory.get().dir("inspect"))
        signing {
            key = absent()
            password = absent()
        }
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
