# FlemmeFit

App Android pour suivre un programme d'entraînement importé : un calendrier, une case à cocher par exercice,
et une progression.

- **Import** d'un programme au format JSON → voir **[FORMAT.md](FORMAT.md)** (format stable, versionné).
- **Calendrier** mensuel : séances faites / commencées / manquées / prévues.
- **Suivi** : on coche chaque exercice ; un appui affiche sa description. « Tout cocher » pour la séance.
- **Repousser** une séance d'un jour : tout le reste du programme est décalé (annulable).
- **Progression** : % d'exercices faits, séances faites, série en cours 🔥, séances manquées, prochaine séance.
- **Plusieurs programmes**, chacun séparé sur l'accueil avec sa couleur.
- **Pliables** (Galaxy Z Fold 5 et suivants) : écran extérieur = une colonne ; écran intérieur déplié =
  calendrier à gauche, détail du jour à droite, et accueil en grille. Le pliage/dépliage garde l'état.
- Données stockées **localement** sur le téléphone (inclus dans la sauvegarde Android).

## Installer l'APK

Chaque push sur `main` construit l'APK via GitHub Actions et le publie dans les
**[Releases](../../releases)** : ouvre la dernière release depuis le téléphone, télécharge
`FlemmeFit-N.apk` et installe-le (autoriser « sources inconnues » la première fois).

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
