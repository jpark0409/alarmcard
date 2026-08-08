# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.jpark.alarmcard.**$$serializer { *; }
-keepclassmembers class com.jpark.alarmcard.** {
    *** Companion;
}
-keepclasseswithmembers class com.jpark.alarmcard.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
