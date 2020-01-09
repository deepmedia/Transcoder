---
layout: main
title: "Transcoder"
---

# Transcoder

Transcoder is a well documented Android library providing hardware-accelerated video transcoding, 
using MediaCodec APIs instead of native code (no FFMPEG patent issues). 

<p align="center">
  <img src="static/banner.png" vspace="10" width="100%">
</p>

- Fast transcoding to AAC/AVC
- Hardware accelerated
- Multithreaded
- Convenient, fluent API
- Concatenate multiple video and audio tracks [[docs]](docs/concatenation)
- Clip or trim video segments [[docs]](docs/clipping)
- Choose output size, with automatic cropping [[docs]](docs/track-strategies#video-size)
- Choose output rotation [[docs]](docs/advanced-options#video-rotation) 
- Choose output speed [[docs]](docs/advanced-options#video-speed)
- Choose output frame rate [[docs]](docs/track-strategies#other-options)
- Choose output audio channels [[docs]](docs/track-strategies#audio-strategies)
- Choose output audio sample rate [[docs]](docs/track-strategies#audio-strategies)
- Override frames timestamp, e.g. to slow down the middle part of the video [[docs]](docs/advanced-options#time-interpolation) 
- Error handling [[docs]](docs/events)
- Configurable validators to e.g. avoid transcoding if the source is already compressed enough [[docs]](docs/validators)
- Configurable video and audio strategies [[docs]](docs/track-strategies)

### Get started

Get started with [install info](about/install), [quick setup](about/getting-started), or
start reading the in-depth [documentation](docs/data-sources).

### Notes

This project started as a fork of [ypresto/android-transcoder](https://github.com/ypresto/android-transcoder).
With respect to the source project, which misses most of the functionality listed above, 
we have also fixed a huge number of bugs and are much less conservative when choosing options 
that might not be supported. The source project will always throw - for example, accepting only 16:9,
AVC Baseline Profile videos - we prefer to try and let the codec fail if it wants to.

### Support

If you like the project, use it with profit, and want to thank back, please consider [donating or
becoming a supporter](extra/donate).

