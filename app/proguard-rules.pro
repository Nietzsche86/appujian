# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Data Models
-keep class id.sch.examkiosk.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
