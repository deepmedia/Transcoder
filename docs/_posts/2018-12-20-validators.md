---
layout: page
title: "Validators"
subtitle: "Validate or abort the transcoding process"
description: "Validate or abort the transcoding process"
category: docs
date: 2018-12-20 20:02:08
order: 4
disqus: 1
---

Validators tell the engine whether the transcoding process should start or not based on the status
of the audio and video track.

```java
Transcoder.into(filePath)
        .setValidator(validator)
        // ...
```

This can be used, for example, to:

- avoid transcoding when video resolution is already OK with our needs
- avoid operating on files without an audio/video stream
- avoid operating on files with an audio/video stream

Validators should implement the `validate(TrackStatus, TrackStatus)` and inspect the status for video
and audio tracks. When `false` is returned, transcoding will complete with the `SUCCESS_NOT_NEEDED` status code.
The TrackStatus enum contains the following values:

|Value|Meaning|
|-----|-------|
|`TrackStatus.ABSENT`|This track was absent in the source file.|
|`TrackStatus.PASS_THROUGH`|This track is about to be copied as-is in the target file.|
|`TrackStatus.COMPRESSING`|This track is about to be processed and compressed in the target file.|
|`TrackStatus.REMOVING`|This track will be removed in the target file.|

The `TrackStatus` value depends on the [track strategy](track-strategies) that was used.
We provide a few validators that can be injected for typical usage.

#### DefaultValidator

This is the default validator and it returns true when any of the track is `COMPRESSING` or `REMOVING`.
In the other cases, transcoding is typically not needed so we abort the operation.

#### WriteAlwaysValidator

This validator always returns true and as such will always write to target file, no matter the track status,
presence of tracks and so on. For instance, the output container file might have no tracks.

#### WriteVideoValidator

A Validator that gives priority to the video track. Transcoding will not happen if the video track does not need it,
even if the audio track might need it. If reducing file size is your only concern, this can avoid compressing
files that would not benefit so much from compressing the audio track only.

