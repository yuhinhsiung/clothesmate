# 本项目个人的 Room 实体 / Hilt 组件通过反射与生代码引用，保留注解与泛型签名
-keepattributes *Annotation*, Signature, InnerClasses

# Retrofit 接口声明
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**
-keep class kotlin.Metadata { *; }

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.yuhinhsiung.clothesmate.**$$serializer { *; }
-keepclassmembers class com.yuhinhsiung.clothesmate.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# TFLite（模型与 support 库依赖 native 调用，保留）
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# OkHttp / Okio（platform 类缺失属预期）
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 和风天气 JWT（eddsa 0.3.0）：库的 verify 路径引用 JDK 内部 X509Key，
# Android 运行时没有该类；App 只用签名路径（token 生成），忽略即可。
-dontwarn sun.security.x509.X509Key
-keep class net.i2p.crypto.eddsa.** { *; }
