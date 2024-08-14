#!/usr/bin/env bash
adb logcat -c
adb logcat *:V > "$1" &
LOGCAT_PID=$!
trap "kill $LOGCAT_PID" EXIT
./gradlew lib:connectedCheck --stacktrace