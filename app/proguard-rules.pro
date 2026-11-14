# Keep application models, Room entities, DAOs, BroadcastReceivers, and Firestore serialization classes
-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.sync.** { *; }
-keep class com.example.auth.** { *; }
-keep class com.example.reminder.** { *; }
-keep class com.example.util.** { *; }
-keep class com.example.KhataWiseApp { *; }
-keep class com.example.PashuAaharApp { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, *Annotation*

# Keep Firebase & Google Play Services classes
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
