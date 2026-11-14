# Keep application models, Room entities, DAOs, and Firestore serialization classes
-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.sync.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# Keep Firebase & Google Play Services classes
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
