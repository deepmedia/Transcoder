### v0.5.0

- New: video cropping to any dimension. Encoder will crop the exceeding size. ([#6][6])
- New: `AspectRatioResizer` to crop to a given aspect ratio. ([#6][6])
- Breaking change: `MediaTranscoder` renamed to `Transcoder`. ([#6][6])
- Breaking change: `MediaTranscoderOptions` renamed to `TranscoderOptions`. ([#6][6])
- Breaking change: `MediaTranscoder.Listener` renamed to `TranscoderListener`. ([#6][6])
- Improvement: use [EglCore](https://github.com/natario1/EglCore) to replace GL logic. ([#5][5])
- Improvement: bug fixes and a new demo app to test transcoding options easily ([#4][4])

[4]: https://github.com/natario1/Transcoder/pull/4
[5]: https://github.com/natario1/Transcoder/pull/5
[6]: https://github.com/natario1/Transcoder/pull/6
