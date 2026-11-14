# App montre : idées gardées pour plus tard

## Installation de l'app montre depuis l'app du téléphone

Aujourd'hui, la première installation sur la montre passe par Wear Installer 2 (débogage sans fil).

Plus tard : l'app FlemmeFit du téléphone installerait elle-même l'app montre.
- Elle va chercher le dernier APK montre (`montre-N`) sur le repo public des releases.
- Elle l'envoie sur la montre, comme Wear Installer : débogage sans fil activé une fois, appairage avec code.
- Un bouton dans les Réglages : « Installer / mettre à jour l'app montre ».

À décider selon le résultat du test de mise à jour autonome de la montre.

## Écran pendant la séance (app montre)

But : ne jamais perdre le fil (minuteur, exo en cours) quand l'écran s'éteint ou que le poignet baisse.
- Minuteurs calculés à partir d'heures de départ enregistrées, pas d'un compteur qui tourne : rien ne se perd.
- Pendant la séance : écran gardé allumé (option, actif par défaut).
- Poignet baissé : mode toujours allumé basse conso (ambient), minuteur simplifié en blanc sur noir.
- « Activité en cours » Wear OS : poignet relevé = retour direct sur la séance, pas sur le cadran ; icône sur le cadran.
- Service de premier plan pendant la séance : la vibration de fin de repos part même app en fond.

## Communication montre ⇄ téléphone : Bluetooth uniquement

- Jamais de 4G (LTE) ni de passage par le cloud : uniquement le Bluetooth avec le téléphone.
- Data Layer : n'envoyer qu'au téléphone « à portée » (nœud nearby), sinon mettre en file d'attente sur la montre
  et tout envoyer au retour du Bluetooth (identifiants uniques, jamais compté deux fois).
- La montre ne parle jamais à Internet pendant une séance.
- Mises à jour de l'app montre : la montre télécharge elle-même via l'Internet partagé par le téléphone
  en Bluetooth (requête liée au transport Bluetooth, jamais la 4G). Lent mais suffisant pour quelques Mo.
  Plan B : le téléphone télécharge et transfère l'APK à la montre en Bluetooth (ChannelClient).

## Batterie

- Garde-fou sous 15 % : cardio coupé, écran éteint poignet baissé (seulement le mode basse conso).
- Récap de fin de séance : % de batterie consommé pendant la séance.
