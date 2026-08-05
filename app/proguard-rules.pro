# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep VPN service / receiver class names (referenced from manifest, reflection-safe)
-keep class com.fdzaki.adshield.vpn.** { *; }
-keep class com.fdzaki.adshield.receiver.** { *; }

# androidx.security:security-crypto -> transitive Google Tink pulls compile-time-only
# annotations (errorprone, javax.annotation) yang tidak ada di runtime classpath.
# Fix untuk R8 FAILURE "Missing class ... CanIgnoreReturnValue/CheckReturnValue/
# Immutable/RestrictedApi/Nullable/GuardedBy" pada minifyReleaseWithR8 (lihat
# PROJECT_STATE.md - Crash Log History).
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
