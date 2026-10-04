# AndroidJUnitRunner calls tracing code shared with the tested APK.
# AGP excludes that dependency from the test APK; retain its entry points for
# instrumentation only. Production releases use the normal R8 rules.
-keep class androidx.tracing.** { *; }
