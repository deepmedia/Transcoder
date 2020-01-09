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

> When video tracks are involved, Transcoder will always move the clipping position to the 
closest video sync frame. This means that sync frames define the granularity of clipping!
If the rate of sync frames in your media is very low, there might be a significant difference
with respect to what you would expect.

For example, if your media has sync frames at seconds 2 and 4 and you try to clip the first 2.5
seconds, Transcoder will actually only clip the first 2.

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

