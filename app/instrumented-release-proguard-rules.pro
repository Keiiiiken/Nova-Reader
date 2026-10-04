# AndroidJUnitRunner calls tracing code shared with the tested APK.
# AGP excludes that dependency from the test APK; retain its entry points for
# instrumentation only. Production releases use the normal R8 rules.
-keep class androidx.tracing.** { *; }

# Tests seed books and inspect persistence through these public APIs. AGP
# excludes app classes from the test APK, so R8 must retain those test entry
# points. Reader UI and WebView implementation remain optimized.
-keep class io.luma.reader.model.** { public *; }
-keep class io.luma.reader.data.** { public *; }
-keep class io.luma.reader.epub.** { public *; }
-keep class io.luma.reader.drive.** { public *; }

-keep class io.luma.reader.reader.BookSearchResult { public *; }
