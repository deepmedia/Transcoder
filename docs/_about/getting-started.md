---
layout: page
title: "Getting Started"
description: "Simple guide to transcode your first video"
order: 2
disqus: 1
---

### Before you start

If your app targets versions older than API 18, you can override the minSdkVersion by
adding this line to your manifest file:

```xml
<uses-sdk tools:overrideLibrary="com.otaliastudios.transcoder" />
```

In this case you should check at runtime that API level is at least 18, before
calling any method here.

### Transcoding your first video

Transcoding happens through the `Transcoder` class by passing it an output file path,
and one of more input data sources. It's pretty simple:

```java
Transcoder.into(filePath)
        .addDataSource(context, uri) // or...
        .addDataSource(filePath) // or...
        .addDataSource(fileDescriptor) // or...
        .addDataSource(dataSource)
        .setListener(new TranscoderListener() {
             public void onTranscodeProgress(double progress) {}
             public void onTranscodeCompleted(int successCode) {}
             public void onTranscodeCanceled() {}
             public void onTranscodeFailed(@NonNull Throwable exception) {}
        }).transcode()
```

However, we offer many APIs and additional features on top that you can read about in the
in-depth documentation.

