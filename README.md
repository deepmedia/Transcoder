[![Build Status](https://github.com/deepmedia/Transcoder/actions/workflows/build.yml/badge.svg?event=push)](https://github.com/deepmedia/Transcoder/actions)
[![Release](https://img.shields.io/github/release/deepmedia/Transcoder.svg)](https://github.com/deepmedia/Transcoder/releases)
[![Issues](https://img.shields.io/github/issues-raw/deepmedia/Transcoder.svg)](https://github.com/deepmedia/Transcoder/issues)

![Project logo](assets/logo-256.png)

# Transcoder

Transcodes and compresses video files into the MP4 format, with audio support, using hardware-accelerated
Android codecs available on the device. Works on API 21+.

- Fast transcoding to AAC/AVC
- Hardware accelerated
- Convenient, fluent API
- Thumbnails support
- Concatenate multiple video and audio tracks [[docs]](https://opensource.deepmedia.io/transcoder/concatenation)
- Clip or trim video segments [[docs]](https://opensource.deepmedia.io/transcoder/clipping)
- Choose output size, with automatic cropping [[docs]](https://opensource.deepmedia.io/transcoder/track-strategies#video-size)
- Choose output rotation [[docs]](https://opensource.deepmedia.io/transcoder/advanced-options#video-rotation)
- Choose output speed [[docs]](https://opensource.deepmedia.io/transcoder/advanced-options#video-speed)
- Choose output frame rate [[docs]](https://opensource.deepmedia.io/transcoder/track-strategies#other-options)
- Choose output audio channels [[docs]](https://opensource.deepmedia.io/transcoder/track-strategies#audio-strategies)
- Choose output audio sample rate [[docs]](https://opensource.deepmedia.io/transcoder/track-strategies#audio-strategies)
- Override frames timestamp, e.g. to slow down the middle part of the video [[docs]](https://opensource.deepmedia.io/transcoder/advanced-options#time-interpolation)
- Error handling [[docs]](https://opensource.deepmedia.io/transcoder/events)
- Configurable validators to e.g. avoid transcoding if the source is already compressed enough [[docs]](https://opensource.deepmedia.io/transcoder/validators)
- Configurable video and audio strategies [[docs]](https://opensource.deepmedia.io/transcoder/track-strategies)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.deepmedia.community:transcoder-android:0.11.2")
}
```

*This project started as a fork of [ypresto/android-transcoder](https://github.com/ypresto/android-transcoder).
With respect to the source project, which misses most of the functionality listed above,
we have also fixed a huge number of bugs and are much less conservative when choosing options
that might not be supported. The source project will always throw - for example, accepting only 16:9,
AVC Baseline Profile videos - we prefer to try and let the codec fail if it wants to*.

*Transcoder is trusted and supported by [ShareChat](https://sharechat.com/), a social media app with
over 100 million downloads.*

Please check out [the official website](https://opensource.deepmedia.io/transcoder) for setup instructions and documentation.
You may also check the demo app (under `/demo`) for a complete example.

```kotlin
Transcoder.into(filePath)
    .addDataSource(context, uri) // or...
    .addDataSource(filePath) // or...
    .addDataSource(fileDescriptor) // or...
    .addDataSource(dataSource)
    .setListener(object : TranscoderListener {
         override fun onTranscodeProgress(progress: Double) = Unit
         override fun onTranscodeCompleted(successCode: Int) = Unit
         override fun onTranscodeCanceled() = Unit
         override fun onTranscodeFailed(exception: Throwable) = Unit
    }).transcode()
```
