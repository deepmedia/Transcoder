### v0.7.0

- New: video concatenation to stitch together multiple media ([#14][14])
- New: select a specific track type (`VIDEO` or `AUDIO`) for sources ([#14][14])
- New: audio resampling through `DefaultAudioStrategy` ([#16][16])
- New: custom resampling through `TranscoderOptions.setAudioResampler()` ([#16][16])
- Breaking change: `TranscoderOptions.setDataSource()` renamed to `addDataSource()` ([#14][14])
- Breaking change: `TranscoderOptions.setRotation()` renamed to `setVideoRotation()` ([#14][14])
- Breaking change: `DefaultVideoStrategy.iFrameInterval()` renamed to `keyFrameInterval()` ([#14][14])
- Breaking change: `DefaultAudioStrategy` now uses a builder - removed old constructor ([#16][16])
- Improvement: rotate videos through OpenGL instead of using metadata ([#14][14])
- Improvement: when concatenating multiple sources, automatically clip the longer track (audio or video) ([#17][17])
- Improvement: various bug fixed ([#18][18])

https://github.com/natario1/Transcoder/compare/v0.6.0...v0.7.0

### v0.6.0

- New: ability to change video/audio speed and change each frame timestamp ([#10][10])
- New: ability to set the video output rotation ([#8][8])
- Improvement: new frame dropping algorithm, thanks to [@Saqrag][Saqrag] ([#9][9])
- Improvement: avoid format validation on tracks coming from PassThroughTrackTranscoder, thanks to [@Saqrag][Saqrag] ([#11][11])

https://github.com/natario1/Transcoder/compare/v0.5.0...v0.6.0

### v0.5.0

- New: video cropping to any dimension. Encoder will crop the exceeding size. ([#6][6])
- New: `AspectRatioResizer` to crop to a given aspect ratio. ([#6][6])
- Breaking change: `MediaTranscoder` renamed to `Transcoder`. ([#6][6])
- Breaking change: `MediaTranscoderOptions` renamed to `TranscoderOptions`. ([#6][6])
- Breaking change: `MediaTranscoder.Listener` renamed to `TranscoderListener`. ([#6][6])
- Improvement: use [EglCore](https://github.com/natario1/EglCore) to replace GL logic. ([#5][5])
- Improvement: bug fixes and a new demo app to test transcoding options easily ([#4][4])

[Saqrag]: https://github.com/Saqrag

[4]: https://github.com/natario1/Transcoder/pull/4
[5]: https://github.com/natario1/Transcoder/pull/5
[6]: https://github.com/natario1/Transcoder/pull/6
[8]: https://github.com/natario1/Transcoder/pull/8
[9]: https://github.com/natario1/Transcoder/pull/9
[10]: https://github.com/natario1/Transcoder/pull/10
[14]: https://github.com/natario1/Transcoder/pull/14
[16]: https://github.com/natario1/Transcoder/pull/16
[17]: https://github.com/natario1/Transcoder/pull/17
[18]: https://github.com/natario1/Transcoder/pull/18
