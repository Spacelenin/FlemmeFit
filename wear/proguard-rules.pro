# Les échanges avec le téléphone et l'état de la séance sont (dé)sérialisés en JSON : on garde leurs serializers.
-keep,includedescriptorclasses class com.spacelenin.flemmefit.montre.**$$serializer { *; }
-keepclassmembers class com.spacelenin.flemmefit.montre.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
