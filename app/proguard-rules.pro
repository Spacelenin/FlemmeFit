# Les classes du modèle sont (dé)sérialisées en JSON : on garde leurs noms et serializers.
-keep,includedescriptorclasses class com.spacelenin.flemmefit.model.**$$serializer { *; }
-keepclassmembers class com.spacelenin.flemmefit.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Échanges avec l'app montre (JSON).
-keep,includedescriptorclasses class com.spacelenin.flemmefit.watch.**$$serializer { *; }
-keepclassmembers class com.spacelenin.flemmefit.watch.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
