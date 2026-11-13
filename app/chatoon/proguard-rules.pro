# 차툰 release(R8) 규칙.

# Pleos SDK: AIDL로 서비스와 통신하고 내부에서 리플렉션·직렬화를 쓴다. 이름이 바뀌면 연결이 끊긴다.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# Pleos SDK가 쓰는 Gson 2.10.1은 R8 규칙을 함께 싣지 않는다(2.11부터 포함). 아래는 Gson 공식 규칙이다.
# 없으면 R8이 TypeToken의 제네릭 정보를 지워 SDK 기능 설정(문 잠금·주행거리)이 실패한다.
-keepattributes Signature, EnclosingMethod, InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-dontwarn com.google.gson.**

# Pleos SDK 안의 kotlinx.serialization(NaviHelper는 뺐지만 다른 SDK가 쓸 수 있어 규칙은 남긴다. release 실주행으로 확인할 것)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn kotlinx.serialization.**

# Android Automotive 차량 API(useLibrary("android.car"))는 기기에 있다.
-dontwarn android.car.**

# release에서는 진단 로그(verbose·debug·info)를 지운다. 경고·오류는 남긴다.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
