---
layout: page
title: "Video Concatenation"
description: "How to concatenate video segments"
category: docs
date: 2018-12-20 20:02:08
order: 2
disqus: 1
---

As you might have guessed from the previous section, you can use `addDataSource(source)` multiple times. 
All the source files will be stitched together:

```java
Transcoder.into(filePath)
        .addDataSource(source1)
        .addDataSource(source2)
        .addDataSource(source3)
        // ...
```

In the above example, the three videos will be stitched together in the order they are added
to the builder. Once `source1` ends, we'll append `source2` and so on. The library will take care
of applying consistent parameters (frame rate, bit rate, sample rate) during the conversion.

This is a powerful tool since it can be used per-track:

```java
Transcoder.into(filePath)
        .addDataSource(source1) // Audio & Video, 20 seconds
        .addDataSource(TrackType.VIDEO, source2) // Video, 5 seconds
        .addDataSource(TrackType.VIDEO, source3) // Video, 5 seconds
        .addDataSource(TrackType.AUDIO, source4) // Audio, 10 sceonds
        // ...
```

In the above example, the output file will be 30 seconds long:

```
Video: | •••••••••••••••••• source1 •••••••••••••••••• | •••• source2 •••• | •••• source3 •••• |  
Audio: | •••••••••••••••••• source1 •••••••••••••••••• | •••••••••••••• source4 •••••••••••••• | 
```

And that's all you need to do.

### Automatic clipping

When concatenating data from multiple sources and on different tracks, it's common to have
a total audio length that is different than the total video length.

In this case, `Transcoder` will automatically clip the longest track to match the shorter.
For example:

```java
Transcoder.into(filePath)
        .addDataSource(TrackType.VIDEO, video1) // Video, 30 seconds
        .addDataSource(TrackType.VIDEO, video2) // Video, 30 seconds
        .addDataSource(TrackType.AUDIO, music) // Audio, 3 minutes
        // ...
```

In the situation above, we won't use the full music track, but only the first minute of it.

