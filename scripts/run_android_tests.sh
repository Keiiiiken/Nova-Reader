#!/usr/bin/env bash
set -euo pipefail
trap 'adb pull /sdcard/Android/data/io.luma.reader/files/screenshots screenshots || true' EXIT
./gradlew --no-daemon --max-workers=2 connectedDebugAndroidTest
./gradlew --no-daemon --max-workers=2 -PtestMinified connectedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.luma.reader.ReaderInteractionTest
