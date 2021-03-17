---
layout: page
title: "Clipping and trimming"
description: "How to clip each segment individually on both ends"
order: 2
disqus: 1
---

Starting from `v0.8.0`, Transcoder offers the option to clip or trim video segments, on one or
both ends. This is done by using special `DataSource` objects that wrap you original source,
so that, in case of [concatenation](concatenation) of multiple media files, the trimming values
can be set individually for each segment.

> If Transcoder determines that the video should be decoded and re-encoded (status is `TrackStatus.COMPRESSING`)
the clipping position is respected precisely. However, if your [strategy](track-strategies) does not
include video decoding / re-encoding, the clipping position will be moved to the closest video sync frame.
This means that the clipped output duration might be different than expected,
depending on the frequency of sync frames in your original file.

### TrimDataSource

The `TrimDataSource` class lets you trim segments by specifying the amount of time to be trimmed
at both ends. For example, the code below will trim the file by 1 second at the beginning, and 
2 seconds at the end:

```java
DataSource source = new UriDataSource(context, uri);
DataSource trim = new TrimDataSource(source, 1000 * 1000, 2 * 1000 * 1000);
Transcoder.into(filePath)
        .addDataSource(trim)
        .transcode()
```

It is recommended to always check `source.getDurationUs()` to compute the correct values.
 
### ClipDataSource

The `ClipDataSource` class lets you clip segments by specifying a time window. For example, 
the code below clip the file from second 1 until second 5:

```java
DataSource source = new UriDataSource(context, uri);
DataSource clip = new ClipDataSource(source, 1000 * 1000, 5 * 1000 * 1000);
Transcoder.into(filePath)
        .addDataSource(clip)
        .transcode()
```

It is recommended to always check `source.getDurationUs()` to compute the correct values.
 
### Related APIs

|Method|Description|
|------|-----------|
|`new TrimDataSource(source, long)`|Creates a new data source trimmed on start.|
|`new TrimDataSource(source, long, long)`|Creates a new data source trimmed on both ends.|
|`new ClipDataSource(source, long)`|Creates a new data source clipped on start.|
|`new ClipDataSource(source, long, long)`|Creates a new data source clipped on both ends.|

