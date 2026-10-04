# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor references optional SLF4J / JVM-only classes that do not exist on Android.
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**
-dontwarn java.lang.management.**

# kotlinx.serialization: keep generated serializers for @Serializable DTOs and navigation routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.dosediary.**$$serializer { *; }
-keepclassmembers class com.example.dosediary.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.dosediary.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates the worker reflectively by class name.
-keep class com.example.dosediary.data.worker.MedicationReminderWorker { *; }
