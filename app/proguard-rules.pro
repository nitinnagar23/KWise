# Keep only reflection/serialization models, Room entities/DAOs, and AndroidManifest components (S-10)
-keep class com.example.model.** { *; }
-keep class com.example.KhataWiseApp { *; }
-keep class com.example.PashuAaharApp { *; }
-keep class com.example.reminder.PromiseReminderReceiver { *; }
-keep class com.example.reminder.BootCompletedReceiver { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, *Annotation*

# Keep Firebase & Google Play Services classes
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

