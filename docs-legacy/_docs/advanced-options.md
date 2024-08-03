---
layout: redirect
redirect_to: https://opensource.deepmedia.io/transcoder/advanced-options
title: "Advanced Options"
description: "Advanced transcoding options"
order: 7
disqus: 1
---

### Video rotation

You can set the output video rotation with the `setRotation(int)` method. This will apply a clockwise
rotation to the input video frames. Accepted values are `0`, `90`, `180`, `270`:

```java
Transcoder.into(filePath)
        .setVideoRotation(rotation) // 0, 90, 180, 270
        // ...
```

### Time interpolation

We offer APIs to change the timestamp of each video and audio frame. You can pass a `TimeInterpolator`
to the transcoder builder to be able to receive the frame timestamp as input, and return a new one
as output.

```java
Transcoder.into(filePath)
        .setTimeInterpolator(timeInterpolator)
        // ...
```

As an example, this is the implementation of the default interpolator, called `DefaultTimeInterpolator`,
that will just return the input time unchanged:

```java
@Override
public long interpolate(@NonNull TrackType type, long time) {
    // Receive input time in microseconds and return a possibly different one.
    return time;
}
```

It should be obvious that returning invalid times can make the process crash at any point, or at least
the transcoding operation fail.

### Video speed

We also offer a special time interpolator called `SpeedTimeInterpolator` that accepts a `float` parameter
and will modify the video speed.

- A speed factor equal to 1 will leave speed unchanged
- A speed factor < 1 will slow the video down
- A speed factor > 1 will accelerate the video

This interpolator can be set using `setTimeInterpolator(TimeInterpolator)`, or, as a shorthand, 
using `setSpeed(float)`:

```java
Transcoder.into(filePath)
        .setSpeed(0.5F) // 0.5x
        .setSpeed(1F) // Unchanged
        .setSpeed(2F) // Twice as fast
        // ...
```

### Audio stretching

When a time interpolator alters the frames and samples timestamps, you can either remove audio or
stretch the audio samples to the new length. This is done through the `AudioStretcher` interface:

```java
Transcoder.into(filePath)
        .setAudioStretcher(audioStretcher)
        // ...
```

The default audio stretcher, `DefaultAudioStretcher`, will:

- When we need to shrink a group of samples, cut the last ones
- When we need to stretch a group of samples, insert noise samples in between

Please take a look at the implementation and read class documentation.

### Audio resampling

When a sample rate different than the input is specified (by the `TrackStrategy`, or, when using the
default audio strategy, by `DefaultAudioStategy.Builder.sampleRate()`), this library will automatically
perform sample rate conversion for you. 

This operation is performed by a class called `AudioResampler`. We offer the option to pass your
own resamplers through the transcoder builder:

```java
Transcoder.into(filePath)
        .setAudioResampler(audioResampler)
        // ...
```

The default audio resampler, `DefaultAudioResampler`, will perform both upsampling and downsampling
with very basic algorithms (drop samples when downsampling, repeat samples when upsampling).
Upsampling is generally discouraged - implementing a real upsampling algorithm is probably out of
the scope of this library.

Please take a look at the implementation and read class documentation.

