#!/usr/bin/env bash
ADB_TAGS="Transcoder:I Engine:I"
ADB_TAGS="$ADB_TAGS DefaultVideoStrategy:I DefaultAudioStrategy:I"
ADB_TAGS="$ADB_TAGS VideoDecoderOutput:I VideoFrameDropper:I"
ADB_TAGS="$ADB_TAGS AudioEngine:I"
adb logcat -c
adb logcat $ADB_TAGS *:E -v color &
./gradlew lib:connectedCheck