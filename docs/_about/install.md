---
layout: page
title: "Install"
description: "Integrate in your project"
order: 1
---

The library works on API 18+, which is the only requirement and should be met by many projects nowadays.

The project is publicly hosted on [JCenter](https://bintray.com/natario/android/Transcoder), where you
can download the AAR package. To fetch with Gradle, make sure you add the JCenter repository in your root projects `build.gradle` file:

```groovy
allprojects {
  repositories {
    jcenter()
  }
}
```

Then simply download the latest version:

```groovy
api 'com.otaliastudios:transcoder:{{ site.github_version }}'
```

No other configuration steps are needed.