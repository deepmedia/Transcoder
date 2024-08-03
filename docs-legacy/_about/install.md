---
layout: redirect
redirect_to: https://opensource.deepmedia.io/transcoder/install
title: "Install"
description: "Integrate in your project"
order: 1
---

Transcoder is publicly hosted on the [Maven Central](https://repo1.maven.org/maven2/com/otaliastudios/)
repository, where you can download the AAR package. To fetch with Gradle, make sure you add the
Maven Central repository in your root projects `build.gradle` file:

```kotlin
allprojects {
  repositories {
    mavenCentral()
  }
}
```

Then simply download the latest version:

```kotlin
api("com.otaliastudios:transcoder:{{ site.github_version }}")
```

> The library works on API 18+, which is the only requirement and should be met by many projects nowadays.

### Snapshots

We deploy snapshots on each push to the main branch. If you want to use the latest, unreleased features,
you can do so (at your own risk) by adding the snapshot repository:

```kotlin
allprojects {
  repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
  }
}
```

and changing the library version from `{{ site.github_version }}` to `latest-SNAPSHOT`.