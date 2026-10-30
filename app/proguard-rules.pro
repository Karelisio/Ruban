# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.karelisio.ruban.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.karelisio.ruban.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Jsoup / OkHttp optional deps
-dontwarn org.jspecify.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**

# WorkManager workers & Glance receivers are instantiated by reflection
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
