# Keep rules for offline map/routing libraries will be added when those engines
# are integrated. Release minification is enabled from the first project build.

# Mapsforge renders themes and reads map files through classes that R8 may strip or
# rename in release builds. Keep the library and silence warnings for optional deps.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.**
