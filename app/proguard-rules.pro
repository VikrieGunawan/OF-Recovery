# --- Kotlinx serialization (data models used by the OrangeFox bridge) ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.orangefox.unofficial.**$$serializer { *; }
-keepclassmembers class com.orangefox.unofficial.** {
    *** Companion;
}
-keepclasseswithmembers class com.orangefox.unofficial.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions**
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- OkHttp / Conscrypt ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
