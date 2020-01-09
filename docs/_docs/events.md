---
layout: page
title: "Transcoding Events"
description: "Listening to transcoding events"
order: 4
disqus: 1
---


Transcoding will happen on a background thread, but we will send updates through the `TranscoderListener`
interface, which can be applied when building the request:

```java
Transcoder.into(filePath)
        .setListenerHandler(handler)
        .setListener(new TranscoderListener() {
             public void onTranscodeProgress(double progress) {}
             public void onTranscodeCompleted(int successCode) {}
             public void onTranscodeCanceled() {}
             public void onTranscodeFailed(@NonNull Throwable exception) {}
        })
        // ...
```

All of the listener callbacks are called:

- If present, on the handler specified by `setListenerHandler()`
- If it has a handler, on the thread that started the `transcode()` call
- As a last resort, on the UI thread

##### onTranscodeProgress

This simply sends a double indicating the current progress. The value is typically between 0 and 1,
but can be a negative value to indicate that we are not able to compute progress (yet?).

This is the right place to update a ProgressBar, for example.

##### onTranscodeCanceled

The transcoding operation was canceled. This can happen when the `Future` returned by `transcode()`
is cancelled by the user.

##### onTranscodeFailed

This can happen in a number of cases and is typically out of our control. Input options might be
wrong, write permissions might be missing, codec might be absent, input file might be not supported
or simply corrupted.

You can take a look at the `Throwable` being passed to know more about the exception.

##### onTranscodeCompleted

Transcoding operation did succeed. The success code can be:

|Code|Meaning|
|----|-------|
|`Transcoder.SUCCESS_TRANSCODED`|Transcoding was executed successfully. Transcoded file was written to the output path.|
|`Transcoder.SUCCESS_NOT_NEEDED`|Transcoding was not executed because it was considered **not needed** by the `Validator`.|

[Keep reading](validators) to know about `Validator`s.

