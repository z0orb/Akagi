# Akagi R8 rules.
# Libraries ship their own consumer rules (Hilt, Compose, Room). Add keep rules here only for
# reflection-based code we pull in.

# youtubedl-android: getInfo() maps yt-dlp's JSON onto its mapper models with Jackson reflection. Akagi parses
# the JSON itself, but the models stay whole for any library path that maps them. Its updater reads GitHub's
# release JSON as a tree, which needs no reflection. Narrowed from keeping all of both libraries (PF-09).
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
# Jackson 2.11 loads its Java 7 helpers by class name.
-keep class com.fasterxml.jackson.databind.ext.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# It unpacks Python/ffmpeg with commons-compress, whose ExtraFieldUtils instantiates zip extra fields by
# reflection: without this, release builds crash on first launch ("class … is not a concrete class").
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.compress.**

# Enum names are persisted (DataStore settings), so R8 must not rename the constants.
-keepclassmembers enum io.github.z0orb.akagi.** { <fields>; }
