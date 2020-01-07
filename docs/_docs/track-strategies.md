---
layout: page
title: "Track Strategies"
description: "Per-track transcoding options"
order: 5
disqus: 1
---


Track strategies return options for each track (audio or video) for the engine to understand **how**
and **if** this track should be transcoded, and whether the whole process should be aborted.

```java
Transcoder.into(filePath)
        .setVideoTrackStrategy(videoStrategy)
        .setAudioTrackStrategy(audioStrategy)
        // ...
```

The point of `TrackStrategy` is to inspect the input `android.media.MediaFormat` and return
the output `android.media.MediaFormat`, filled with required options.

This library offers track specific strategies that help with audio and video options (see
[Audio Strategies](#audio-strategies) and [Video Strategies](#video-strategies)).
In addition, we have a few built-in strategies that can work for both audio and video:

##### PassThroughTrackStrategy

A TrackStrategy that asks the encoder to keep this track as is, by returning the same input
format. Note that this is risky, as the input track format might not be supported my the MP4 container.

This will set the `TrackStatus` to `TrackStatus.PASS_THROUGH`.

##### RemoveTrackStrategy

A TrackStrategy that asks the encoder to remove this track from the output container, by returning null.
For instance, this can be used as an audio strategy to remove audio from video/audio streams.

This will set the `TrackStatus` to `TrackStatus.REMOVING`.

### Audio Strategies

The default internal strategy for audio is a `DefaultAudioStrategy`, which converts the
audio stream to AAC format with the specified number of channels and [sample rate](advanced-options).

```java
DefaultAudioStrategy strategy = DefaultAudioStrategy.builder()
        .channels(DefaultAudioStrategy.CHANNELS_AS_INPUT)
        .channels(1)
        .channels(2)
        .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
        .sampleRate(44100)
        .sampleRate(30000)
        .bitRate(DefaultAudioStrategy.BITRATE_UNKNOWN)
        .bitRate(bitRate)
        .build();

Transcoder.into(filePath)
        .setAudioTrackStrategy(strategy)
        // ...
```

Take a look at the source code to understand how to manage the `android.media.MediaFormat` object.

### Video Strategies

The default internal strategy for video is a `DefaultVideoStrategy`, which converts the
video stream to AVC format and is very configurable. The class helps in defining an output size.
If the output size does not match the aspect ratio of the input stream size, `Transcoder` will
crop part of the input so it matches the final ratio.

##### Video Size

We provide helpers for common tasks:

```java
DefaultVideoStrategy strategy;

// Sets an exact size. If aspect ratio does not match, cropping will take place.
strategy = DefaultVideoStrategy.exact(1080, 720).build();

// Keeps the aspect ratio, but scales down the input size with the given fraction.
strategy = DefaultVideoStrategy.fraction(0.5F).build();

// Ensures that each video size is at most the given value - scales down otherwise.
strategy = DefaultVideoStrategy.atMost(1000).build();

// Ensures that minor and major dimension are at most the given values - scales down otherwise.
strategy = DefaultVideoStrategy.atMost(500, 1000).build();
```

In fact, all of these will simply call `new DefaultVideoStrategy.Builder(resizer)` with a special
resizer. We offer handy resizers:

|Name|Description|
|----|-----------|
|`ExactResizer`|Returns the exact dimensions passed to the constructor.|
|`AspectRatioResizer`|Crops the input size to match the given aspect ratio.|
|`FractionResizer`|Reduces the input size by the given fraction (0..1).|
|`AtMostResizer`|If needed, reduces the input size so that the "at most" constraints are matched. Aspect ratio is kept.|
|`PassThroughResizer`|Returns the input size unchanged.|

You can also group resizers through `MultiResizer`, which applies resizers in chain:

```java
// First scales down, then ensures size is at most 1000. Order matters!
Resizer resizer = new MultiResizer();
resizer.addResizer(new FractionResizer(0.5F));
resizer.addResizer(new AtMostResizer(1000));

// First makes it 16:9, then ensures size is at most 1000. Order matters!
Resizer resizer = new MultiResizer();
resizer.addResizer(new AspectRatioResizer(16F / 9F));
resizer.addResizer(new AtMostResizer(1000));
```

This option is already available through the DefaultVideoStrategy builder, so you can do:

```java
DefaultVideoStrategy strategy = new DefaultVideoStrategy.Builder()
        .addResizer(new AspectRatioResizer(16F / 9F))
        .addResizer(new FractionResizer(0.5F))
        .addResizer(new AtMostResizer(1000))
        .build();
```

##### Other options

You can configure the `DefaultVideoStrategy` with other options unrelated to the video size:

```java
DefaultVideoStrategy strategy = new DefaultVideoStrategy.Builder()
        .bitRate(bitRate)
        .bitRate(DefaultVideoStrategy.BITRATE_UNKNOWN) // tries to estimate
        .frameRate(frameRate) // will be capped to the input frameRate
        .keyFrameInterval(interval) // interval between key-frames in seconds
        .build();
```

### Compatibility

As stated pretty much everywhere, **not all codecs/devices/manufacturers support all sizes/options**.
This is a complex issue which is especially important for video strategies, as a wrong size can lead
to a transcoding error or corrupted file.

Android platform specifies requirements for manufacturers through the [CTS (Compatibility test suite)](https://source.android.com/compatibility/cts).
Only a few codecs and sizes are **strictly** required to work.

We collect common presets in the `DefaultVideoStrategies` class:

```java
Transcoder.into(filePath)
        .setVideoTrackStrategy(DefaultVideoStrategies.for720x1280()) // 16:9
        .setVideoTrackStrategy(DefaultVideoStrategies.for360x480()) // 4:3
        // ...
```