# Akagi R8 rules.
# Libraries ship their own consumer rules (Hilt, Compose, Room). Add keep rules here only for
# reflection-based code we pull in.

# youtubedl-android maps yt-dlp's JSON with Jackson (reflection on its mapper models and on Jackson itself).
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# It unpacks Python/ffmpeg with commons-compress, whose ExtraFieldUtils instantiates zip extra fields by
# reflection: without this, release builds crash on first launch ("class … is not a concrete class").
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.compress.**

# Enum names are persisted (DataStore settings), so R8 must not rename the constants.
-keepclassmembers enum io.github.z0orb.akagi.** { <fields>; }
