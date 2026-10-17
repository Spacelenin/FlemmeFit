# FlemmeFit

App Android pour suivre un programme d'entraînement importé : un calendrier, une case à cocher par exercice,
et une progression.

- **Import** d'un programme au format JSON → voir **[FORMAT.md](FORMAT.md)** (format stable, versionné).
- **Calendrier** mensuel : séances faites / commencées / manquées / prévues.
- **Suivi** : on coche chaque exercice ; un appui affiche sa description. « Tout cocher » pour la séance.
  Appui long : noter ce qui a vraiment été fait (exercice partiel) ou son max. % de la journée, séances et
  exos faits, série en cours 🔥, séances manquées, prochaine séance.
- **Programmes illimités** (`"repeat": "forever"`) : le calendrier se remplit sans fin.
- **Progression automatique** des séries / reps / durées / charges, semaine de **décharge** à 50 %.
- **Jours Boss 💀** aléatoires toutes les 3 à 4 semaines : séance renforcée et série à fond, on note son max.
- **Repousser** une séance d'un jour : tout le reste du programme est décalé (annulable).
- **Stats** par programme et globales : reps, séries, temps, records, évolution par exercice, volume par semaine,
  Boss vaincus, historique.
- **Rappels** (⋮ → Rappels), ton terriblement taquin : rappel du jour (18 h par défaut) les jours de séance,
  relance du soir désactivable (21 h) si la séance n'est pas finie, annonce du Boss la veille, bouton « Plus tard ».
- **Les potes 👥** : pseudo, avancée du jour de chacun, série, Boss vaincus et **exploits 🏆** (un max au moins
  2× supérieur à la série prévue), classement à points (Boss 5, exploit 3, séance 1). Sans serveur à nous :
  les apps échangent une petite carte via le relais public gratuit [ntfy.sh](https://ntfy.sh) (sans compte),
  et chacune garde la dernière carte connue des autres. Canal commun par défaut, modifiable dans les réglages.
- **Plusieurs programmes**, chacun séparé sur l'accueil avec sa couleur.
- **Pliables** (Galaxy Z Fold 5 et suivants) : écran extérieur = une colonne ; écran intérieur déplié =
  calendrier à gauche, détail du jour à droite, et accueil en grille. Le pliage/dépliage garde l'état.
- Données stockées **localement** sur le téléphone (inclus dans la sauvegarde Android).

## Installer l'APK

Chaque push sur `main` construit l'APK via GitHub Actions et le publie dans les
**[Releases](../../releases)** : ouvre la dernière release depuis le téléphone, télécharge
`FlemmeFit-N.apk` et installe-le (autoriser « sources inconnues » la première fois).

**Mises à jour dans l'app** : une fois FlemmeFit installé, plus besoin de revenir sur GitHub. L'app vérifie
les Releases au lancement (toutes les 6 h au plus, ou via *⋮ → Vérifier les mises à jour*), affiche un bandeau
« Nouvelle version disponible », télécharge l'APK (empreinte SHA-256 vérifiée) et lance l'installeur Android.
La première fois, Android demande d'autoriser FlemmeFit à installer des applications.

Les builds sont tous signés avec la même clé (`app/debug.keystore`) et ont un numéro de version croissant :
une nouvelle version s'installe **par-dessus** l'ancienne sans perdre la progression.

Les autres branches produisent aussi l'APK, en artefact de l'onglet *Actions*.

## Développement

- Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 35.
- `./gradlew testReleaseUnitTest assembleRelease` (JDK 17, SDK Android requis).

```
app/src/main/java/com/spacelenin/flemmefit/
├── model/   format d'import, validation, calcul du planning et de la progression (Kotlin pur, testé)
├── data/    stockage JSON local
└── ui/      écrans Compose (accueil, programme, calendrier, détail du jour, import)
examples/    programmes d'exemple (embarqués dans l'app)
```
