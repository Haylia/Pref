# Preferans Scorer ProGuard rules

# Keep kotlinx.serialization serializers for our domain model
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class com.preferans.scorer.** {
    *** Companion;
}
-keepclasseswithmembers class com.preferans.scorer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
