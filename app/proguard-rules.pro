# Keep jcifs-ng SMB
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# Keep Glide
-keep class com.bumptech.glide.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
