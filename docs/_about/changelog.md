---
layout: page
title: "Changelog"
order: 3
---

New versions are released through GitHub, so the reference page is the [GitHub Releases](https://github.com/natario1/Transcoder/releases) page.

> Starting from 0.7.0, you can now [support development](https://github.com/sponsors/natario1) through the GitHub Sponsors program. 
Companies can share a tiny part of their revenue and get private support hours in return. Thanks!

### v0.9.1

- Improvement: `DefaultDataSink` new constructor with support for FileDescriptor. ([#87][87])

<https://github.com/natario1/Transcoder/compare/v0.9.0...v0.9.1>

### v0.9.0

- New: `BlankAudioDataSource` can be used to add muted audio to a video-only track, thanks to [@mudar][mudar] ([#64][64]) 
- Enhancement: you can now concatenate multiple files even if some of them have no audio, thanks to [@mudar][mudar] ([#64][64]) 
- Enhancement: you can now concatenate multiple files without audio track, thanks to [@cbernier2][cbernier2] ([#61][61])

<https://github.com/natario1/Transcoder/compare/v0.8.0...v0.9.0>

### v0.8.0

- New: `TrimDataSource` to trim segments. Use it to wrap your original source. Thanks to [@mudar][mudar] ([#50][50])
- New: `ClipDataSource`, just likes `TrimDataSource` but selects trim values with respect to video start ([#54][54])

> Transcoder will trim video segments only at the closest video sync frame. If your video has few sync
frames, the trim timestamp might be different than what was selected.

<https://github.com/natario1/Transcoder/compare/v0.7.4...v0.8.0>

##### v0.7.4

- Fix: fixed Xamarin incompatibility, thanks to [@aweck][aweck] ([#41][41])
- Fix: fixed small bugs with specific API versions / media files ([#47][47])
- Fix: fixed issues with specific media files, ensure consistent onProgress callback ([#48][48])

<https://github.com/natario1/Transcoder/compare/v0.7.3...v0.7.4>

##### v0.7.3

- Fix: fixed bug with files that do not have an audio track, thanks to [@pawegio][pawegio] ([#31][31])
- Fix: fixed possible issues with FilePathDataSource ([#32][32])

<https://github.com/natario1/Transcoder/compare/v0.7.2...v0.7.3>

##### v0.7.2

- Improvement: better input format detection. Fixes bugs with certain files ([#29][29])
- Improvement: added `DefaultAudioStrategy.Builder.bitRate()` option ([#29][29])

<https://github.com/natario1/Transcoder/compare/v0.7.1...v0.7.2>

##### v0.7.1

- Improvement: update the underlying OpenGL library ([#20][20])

<https://github.com/natario1/Transcoder/compare/v0.7.0...v0.7.1>

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

<https://github.com/natario1/Transcoder/compare/v0.6.0...v0.7.0>

### v0.6.0

- New: ability to change video/audio speed and change each frame timestamp ([#10][10])
- New: ability to set the video output rotation ([#8][8])
- Improvement: new frame dropping algorithm, thanks to [@Saqrag][Saqrag] ([#9][9])
- Improvement: avoid format validation on tracks coming from PassThroughTrackTranscoder, thanks to [@Saqrag][Saqrag] ([#11][11])

<https://github.com/natario1/Transcoder/compare/v0.5.0...v0.6.0>

### v0.5.0

- New: video cropping to any dimension. Encoder will crop the exceeding size. ([#6][6])
- New: `AspectRatioResizer` to crop to a given aspect ratio. ([#6][6])
- Breaking change: `MediaTranscoder` renamed to `Transcoder`. ([#6][6])
- Breaking change: `MediaTranscoderOptions` renamed to `TranscoderOptions`. ([#6][6])
- Breaking change: `MediaTranscoder.Listener` renamed to `TranscoderListener`. ([#6][6])
- Improvement: use [EglCore](https://github.com/natario1/EglCore) to replace GL logic. ([#5][5])
- Improvement: bug fixes and a new demo app to test transcoding options easily ([#4][4])

[Saqrag]: https://github.com/Saqrag
[pawegio]: https://github.com/pawegio
[aweck]: https://github.com/aweck
[mudar]: https://github.com/mudar
[cbernier2]: https://github.com/cbernier2

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
[20]: https://github.com/natario1/Transcoder/pull/20
[29]: https://github.com/natario1/Transcoder/pull/29
[31]: https://github.com/natario1/Transcoder/pull/31
[32]: https://github.com/natario1/Transcoder/pull/32
[41]: https://github.com/natario1/Transcoder/pull/41
[47]: https://github.com/natario1/Transcoder/pull/47
[48]: https://github.com/natario1/Transcoder/pull/48
[50]: https://github.com/natario1/Transcoder/pull/50
[54]: https://github.com/natario1/Transcoder/pull/54
[61]: https://github.com/natario1/Transcoder/pull/61
[64]: https://github.com/natario1/Transcoder/pull/64
