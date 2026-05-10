# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.kbul.spicycrab.**$$serializer { *; }
-keepclassmembers class com.kbul.spicycrab.** {
    *** Companion;
}
-keepclasseswithmembers class com.kbul.spicycrab.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Optional compile-time annotations and SLF4J binders referenced by transitive libraries.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
