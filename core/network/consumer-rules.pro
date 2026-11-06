# API models are serialized by kotlinx.serialization; keep their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.asl.wallet.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.asl.wallet.**$$serializer { *; }
