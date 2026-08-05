# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep VPN service / receiver class names (referenced from manifest, reflection-safe)
-keep class com.fdzaki.adshield.vpn.** { *; }
-keep class com.fdzaki.adshield.receiver.** { *; }
