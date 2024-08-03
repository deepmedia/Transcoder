---
layout: redirect
redirect_to: https://opensource.deepmedia.io/transcoder/data-sources
title: "Data Sources"
description: "Sources of media data"
order: 1
disqus: 1
---

Starting a transcoding operation will require a source for our data, which is not necessarily
a `File`. The `DataSource` objects will automatically take care about releasing streams / resources,
which is convenient but it means that they can not be used twice.

```java
Transcoder.into(filePath)
        .addDataSource(source1)
        .transcode()
```

##### UriDataSource

The Android friendly source can be created with `new UriDataSource(context, uri)` or simply
using `addDataSource(context, uri)` in the transcoding builder.

##### FileDescriptorDataSource

A data source backed by a file descriptor. Use `new FileDescriptorDataSource(descriptor)` or
simply `addDataSource(descriptor)` in the transcoding builder. Note that it is the caller
responsibility to close the file descriptor.

##### FilePathDataSource

A data source backed by a file absolute path. Use `new FilePathDataSource(path)` or
simply `addDataSource(path)` in the transcoding builder.

##### AssetFileDescriptorDataSource

A data source backed by Android's AssetFileDescriptor. Use `new AssetFileDescriptorDataSource(descriptor)`
or simply `addDataSource(descriptor)` in the transcoding builder. Note that it is the caller
responsibility to close the file descriptor.

### Track specific sources

Although a media source can have both audio and video, you can select a specific track
for transcoding and exclude the other(s). For example, to select the video track only:
 
```java
Transcoder.into(filePath)
        .addDataSource(TrackType.VIDEO, source)
        .transcode()
```
 
### Related APIs

|Method|Description|
|------|-----------|
|`addDataSource(Context, Uri)`|Adds a new source for the given Uri.|
|`addDataSource(FileDescriptor)`|Adds a new source for the given FileDescriptor.|
|`addDataSource(String)`|Adds a new source for the given file path.|
|`addDataSource(DataSource)`|Adds a new source.|
|`addDataSource(TrackType, DataSource)`|Adds a new source restricted to the given TrackType.|

