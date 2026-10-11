# Project-specific R8 rules. Room, Compose and kotlinx.serialization ship their own consumer rules.

# ML Kit text recognition looks up parts of itself at runtime, which R8's full mode strips:
# reading a photo then fails inside com.google.android.gms.internal.mlkit_vision_common with a
# NullPointerException. Found by testing the release build; keep ML Kit as shipped.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
