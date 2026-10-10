#!/usr/bin/env bash
set -euo pipefail
collect_captures() {
    mkdir -p screenshots
    adb pull /sdcard/Android/data/io.nova.reader/files/screenshots screenshots/ || true
}
trap collect_captures EXIT
# The disposable Google APIs image can launch its first-run setup process
# during tests. Its ANR dialog steals focus from the app (including Espresso's
# back action). Disable only that setup package; keep Google Play services and
# all Nova Reader assertions enabled.
setup_package=com.google.android.googlesetup
if adb shell pm list packages "$setup_package" | tr -d '\r' | grep -Fxq "package:$setup_package"; then
    adb shell am force-stop "$setup_package"
    disabled=$(adb shell pm disable-user --user 0 "$setup_package" | tr -d '\r')
    printf '%s\n' "$disabled"
    [[ "$disabled" == *'new state: disabled-user'* ]]
fi
./gradlew --no-daemon --max-workers=2 connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
collect_captures
./gradlew --no-daemon --max-workers=2 -PtestMinified connectedReleaseAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=io.luma.reader.ReaderInteractionTest

collect_captures
# Check the actual distribution bytecode without instrumentation-only keep rules.
./gradlew --no-daemon --max-workers=2 assembleRelease
python3 scripts/check_release_ui.py
