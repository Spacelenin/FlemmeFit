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
- **Réglage sur mesure 🎯**, exo par exo (variantes comprises) : ce qui est noté (toutes les séries, le temps en plus
  après 0, la série à fond) et le ressenti (dur / OK / facile, par exo ou pour la séance) règlent la suite. Rapide les
  premières semaines, puis ça se cale ; les tests de niveau servent de garde-fou. Toutes les séries faites valident
  l'exo, même en dessous du prévu (sauf jour Boss). En fin de séance, une fenêtre annonce ce qui change.
  Rythme au choix par programme (🐢 Doux, 🙂 Normal, 🚀 Vif), avec une suggestion quand tout est facile ou dur ;
  réglages fins dans le JSON (`tuning`), et export de la routine ou d'un modèle à modifier.
- **Changer un exo** 🔄 depuis la page du jour : un remplaçant prévu par la routine (Russian twist à la place des
  hip dips, dosé comme les abdos, reps × 2 ; extension triceps à la place des dips (le mercredi) ; extension lombaire à la place
  du hip thrust) ou un exo de la même famille, juste ce jour-là ou pour la suite.
- **Dose pleine, toujours** : 3 × 8 reste 3 × 8, en début comme en fin de séance, et un nouvel exo démarre à sa
  dose ; les tests et le réglage sur mesure corrigent. « ↕ Réorganiser » change l'ordre des exos du jour par
  glisser-déposer. « 🥵 Crevé » sur Beat Saber : −20 % de temps ce jour-là, et ça compte comme fait.
- **Excentrique ↘** sur les exos en reps (pompes, dips, abdos, tractions, squats…) : quand c'est trop dur, on ne
  travaille que la descente, le plus lentement possible. L'exo compte comme fait, mais le réglage, la progression et les
  records ne bougent pas (jamais un jour Boss ni sur un test).
- **Forme du jour** : fatigué 😮‍💨 (70 %) ou malade 🤒 (55 %), à choisir avant la séance. Toutes les séries sont réduites,
  la journée est sauvée (ta série 🔥 tient, sans point de séance), et le réglage, la progression et les records ne
  bougent pas. Burst et Chill ne se cumulent pas avec. Un Boss tombé ce jour-là est reporté une fois, sur un jour de
  séance de la semaine qui suit, dans le même bloc de 3 semaines (sinon il est perdu).
- **Mode séance** guidé : série par série, 4 s pour se mettre en place avant chaque série au temps, repos décompté,
  chrono qui continue en temps en plus, ressenti par exo.
- **Exos d'un côté à la fois** : une série par côté, 🔴 gauche / 🔵 droite. Chaque côté est suivi à part ; un côté en
  retard commence, fait une série de plus et rattrape l'autre (écart dans les stats).
- **Variantes 🎲** tirées certains jours (diamant, pike, une main, pistol, Perche…), dosées sur le test de leur famille.
  Le **Pont** se débloque (pompes pike 5, superman 30 s, hip thrust 15, plus un test de souplesse à confirmer) :
  mains surélevées d'abord, puis au sol, +2 s à chaque séance réussie.
- **Burst 🚀 et Chill 🛋** : une fois par semaine, renforcer ou alléger la séance du jour.
- **Jours Boss 💀** : un par bloc de 3 semaines de travail (entre deux semaines allégées), semaine et jour tirés au sort : séance renforcée et série à fond, avec pour objectif son dernier
  max + 1 (jamais moins que la dose du jour). Le réussir fait monter le réglage d'un cran au plus.
- **Tests et paliers 📏** (démarrage objectif, façon 100 pompes) : un test à fond choisit le palier de départ, refait en
  cours de route pour passer au palier suivant. Exemple : `examples/routine-maison.json`.
- **Mise à jour d'une routine** en la réimportant : le suivi (cases cochées, saisies, tests, date) est gardé.
- **Repousser** une séance d'un jour : tout le reste du programme est décalé (annulable).
- **Stats** par programme et globales : reps, séries, temps, records, évolution par exercice, volume par semaine,
  Boss vaincus, historique.
- **Rappels** (⚙ Réglages → Rappels), ton terriblement taquin : rappel du jour (18 h par défaut) les jours de séance,
  relance du soir désactivable (21 h) si la séance n'est pas finie, annonce du Boss la veille, bouton « Plus tard ».
- **Les potes 👥** : pseudo, avancée du jour de chacun, série, Boss vaincus et **exploits 🏆** (un max au moins
  2× supérieur à la série prévue), classement à points (Boss 5, exploit 3, séance 1). Sans serveur à nous :
  les apps échangent une petite carte via le relais public gratuit [ntfy.sh](https://ntfy.sh) (sans compte),
  et chacune garde la dernière carte connue des autres. Canal commun par défaut, modifiable dans les réglages.
- **Plusieurs programmes**, chacun séparé sur l'accueil avec sa couleur.
- **Pliables** (Galaxy Z Fold 5 et suivants) : écran extérieur = une colonne ; écran intérieur déplié =
  calendrier à gauche, détail du jour à droite, et accueil en grille. Le pliage/dépliage garde l'état.
- Données stockées **localement** sur le téléphone (inclus dans la sauvegarde Android).
- **Jamais bloqué** : un calcul de réglage qui échoue est ignoré ; après un plantage, l'appli s'ouvre sur un écran qui
  propose d'envoyer le rapport et d'installer la mise à jour, sans toucher aux séances.

## Installer l'APK

Chaque push sur `main` construit l'APK via GitHub Actions et le publie dans les
**[Releases de FlemmeFit-releases](https://github.com/Spacelenin/FlemmeFit-releases/releases)** (repo public qui ne contient que les APK) : ouvre la dernière release depuis le téléphone, télécharge
`FlemmeFit-N.apk` et installe-le (autoriser « sources inconnues » la première fois).

**Mises à jour dans l'app** : une fois FlemmeFit installé, plus besoin de revenir sur GitHub. L'app vérifie
les Releases au lancement (toutes les 6 h au plus, ou via *⚙ Réglages → Vérifier*), affiche un bandeau
« Nouvelle version disponible », télécharge l'APK (empreinte SHA-256 vérifiée) et lance l'installeur Android.
La première fois, Android demande d'autoriser FlemmeFit à installer des applications.

Les builds sont tous signés avec la même clé (`app/debug.keystore`) et ont un numéro de version croissant :
une nouvelle version s'installe **par-dessus** l'ancienne sans perdre la progression.

Les autres branches produisent aussi l'APK, en artefact de l'onglet *Actions*.

## Développement

- Kotlin + Jetpack Compose (Material 3), minSdk 34, targetSdk 36 (Android 14 minimum).
- `./gradlew testReleaseUnitTest assembleRelease` (JDK 17, SDK Android requis).

```
app/src/main/java/com/spacelenin/flemmefit/
├── model/   format d'import, validation, calcul du planning et de la progression (Kotlin pur, testé)
├── data/    stockage JSON local
└── ui/      écrans Compose (accueil, programme, calendrier, détail du jour, import)
examples/    programmes d'exemple (embarqués dans l'app)
```
