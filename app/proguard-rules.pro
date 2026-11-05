# kotlinx.serialization keep rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.leonidsm.rsdconverter.**$$serializer { *; }
-keepclassmembers class com.leonidsm.rsdconverter.** {
    *** Companion;
}
-keepclasseswithmembers class com.leonidsm.rsdconverter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
