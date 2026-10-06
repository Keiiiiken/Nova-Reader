#!/usr/bin/env bash
set -euo pipefail
collect_captures() {
    mkdir -p screenshots
    adb pull /sdcard/Android/data/io.nova.reader/files/screenshots screenshots/ || true
}
trap collect_captures EXIT
./gradlew --no-daemon --max-workers=2 connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
collect_captures
./gradlew --no-daemon --max-workers=2 -PtestMinified connectedReleaseAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=io.luma.reader.ReaderInteractionTest

collect_captures
# Check the actual distribution bytecode without instrumentation-only keep rules.
./gradlew --no-daemon --max-workers=2 assembleRelease
python3 scripts/check_release_ui.py
