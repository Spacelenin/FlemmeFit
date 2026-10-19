# Format d'import FlemmeFit — version 1

Un programme d'entraînement est un fichier **JSON** (extension `.json` conseillée, encodage UTF-8).
Ce format est **stable** : un fichier valide en version 1 restera toujours importable.

## Exemple minimal

```json
{
  "format": "flemmefit",
  "version": 1,
  "name": "Pompes tous les deux jours",
  "weeks": [
    {
      "days": [
        { "day": 1, "exercises": [ { "name": "Pompes", "sets": 3, "reps": "12" } ] },
        { "day": 3, "exercises": [ { "name": "Pompes", "sets": 3, "reps": "12" } ] },
        { "day": 5, "exercises": [ { "name": "Pompes", "sets": 3, "reps": "15" } ] }
      ]
    }
  ]
}
```

Exemple complet : [`examples/exemple-full-body.json`](examples/exemple-full-body.json) (aussi disponible
dans l'app : *Importer → Programme d'exemple*).

## Comment l'app place les séances dans le calendrier

- À l'import, tu choisis une **date de début** : c'est le **jour 1 de la semaine 1**.
- Une semaine = 7 jours **à partir de la date de début** (pas forcément du lundi au dimanche).
  Si tu commences un mercredi, `"day": 1` = mercredi, `"day": 2` = jeudi, etc.
- Les jours **absents** d'une semaine sont des jours de **repos**.
- Les semaines s'enchaînent dans l'ordre du tableau `weeks`.
- `repeat` répète **l'ensemble** des semaines : avec 2 semaines et `"repeat": 4`, le programme dure 8 semaines
  (S1, S2, S1, S2, S1, S2, S1, S2). Avec `"repeat": "forever"`, il tourne **sans fin**.
- **Repousser** une séance (bouton dans l'app) la décale d'un jour **ainsi que tout le reste du programme**.
  Ce n'est pas stocké dans le fichier d'import.

## Référence des champs

### Racine

| Champ         | Type    | Obligatoire | Description |
|---------------|---------|-------------|-------------|
| `format`      | texte   | non (conseillé) | Toujours `"flemmefit"`. |
| `version`     | entier  | **oui**     | Toujours `1` pour ce format. |
| `name`        | texte   | **oui**     | Nom du programme (affiché sur l'accueil). |
| `description` | texte   | non         | Résumé, consignes générales. |
| `repeat`      | entier ou `"forever"` | non (défaut `1`) | Nombre de fois où la liste `weeks` est jouée (1 à 100), ou `"forever"` pour un programme illimité. |
| `deloadEvery` | entier  | non         | Une semaine de **décharge** toutes les N semaines (2 à 52), `0` = jamais. Par défaut : `4` si au moins un exercice a une `progression`, sinon `0`. |
| `boss`        | booléen | non (défaut `true`) | Jours **Boss** aléatoires. `false` pour les désactiver. |
| `tests`       | tableau | non         | **Tests de départ** : leur résultat choisit le palier des exercices (voir « Tests et paliers »). |
| `weeks`       | tableau | **oui**     | Au moins une semaine (voir ci-dessous). |

### Semaine (`weeks[]`)

| Champ   | Type    | Obligatoire | Description |
|---------|---------|-------------|-------------|
| `label` | texte   | non         | Nom de la semaine (« Semaine de décharge »…). Par défaut « Semaine N ». |
| `days`  | tableau | non (défaut vide) | Les séances de la semaine. Une semaine vide = 7 jours de repos. |

### Séance (`weeks[].days[]`)

| Champ       | Type    | Obligatoire | Description |
|-------------|---------|-------------|-------------|
| `day`       | entier  | **oui**     | 1 à 7, position dans la semaine. Un même numéro ne peut apparaître qu'une fois par semaine. |
| `title`     | texte   | non         | Nom de la séance (« Haut du corps »). Par défaut « Séance ». |
| `notes`     | texte   | non         | Consignes de la séance (échauffement, ressenti…). |
| `exercises` | tableau | **oui**     | Au moins un exercice. Chaque exercice est une case à cocher. |

### Exercice (`weeks[].days[].exercises[]`)

Tous les champs sauf `name` sont **indicatifs** : ils sont affichés tels quels, l'app ne calcule rien avec.
Ce sont des **textes libres** (tu peux écrire `"12"`, `"8-10"`, `"max"`, `"10 par jambe"`…).

| Champ         | Type   | Obligatoire | Exemple | Description |
|---------------|--------|-------------|---------|-------------|
| `name`        | texte  | **oui**     | `"Squat"` | Nom de l'exercice. |
| `id`          | texte  | non         | `"pompes"` | Relie le même exercice sur plusieurs jours (progression et stats communes). Par défaut : le nom, sans tenir compte des majuscules. |
| `sets`        | entier | non         | `4`     | Nombre de séries (1 à 100). |
| `reps`        | texte  | non         | `"8-10"` | Répétitions par série. Un nombre (`12`) est aussi accepté. |
| `duration`    | texte  | non         | `"45s"`, `"20min"` | Durée (gainage, cardio, étirements…). |
| `load`        | texte  | non         | `"60 kg"`, `"élastique rouge"` | Charge. |
| `rest`        | texte  | non         | `"90s"` | Repos entre les séries. |
| `description` | texte  | non         | `"Dos droit…"` | Explication détaillée, affichée en touchant l'exercice. |
| `progression` | objet  | non         | voir plus bas | Progression automatique. |
| `test`        | texte  | non         | `"pompes"` | Id d'un test de `tests` : l'exercice suit son résultat. |
| `levels`      | tableau | non        | voir plus bas | Paliers choisis d'après le résultat de `test`. |
| `amrap`       | booléen | non (défaut `false`) | `true` | Dernière série : le **max possible** (« au moins » le prévu), noté en cochant. |
| `retest`      | booléen | non (défaut `false`) | `true` | L'exercice **est** le test : une série à fond, son résultat devient la nouvelle référence. |

Affichage dans l'app : `sets × reps · load · repos rest`, par ex. **4 × 8-10 · 60 kg · repos 90s**.

**Valeurs chiffrées.** Pour progresser (et pour des stats précises), une valeur doit être chiffrée :
- `reps` : un nombre entier (`12` ou `"12"`). `"8-10"` ou `"max"` restent affichés tels quels, sans progression.
- `duration` : `"45s"`, `"45"`, `"2min"`, `"1min30"`, `"1m30s"` ou `"1:30"`.
- `load` : un nombre suivi d'une unité, `"8 kg"`, `"2,5kg"` (`kg` par défaut).

### Progression (`exercises[].progression`)

Chaque **cran** de progression ajoute les pas indiqués. Arrivé au plafond, on ajoute `addSets` série(s) et on
repart de la valeur de départ (« double progression ») ; une fois `maxSets` atteint, l'exercice reste au plafond.

| Champ         | Type   | Défaut   | Description |
|---------------|--------|----------|-------------|
| `basis`       | texte  | `"done"` | `"done"` : un cran par séance **validée** (une séance ratée ne fait pas progresser). `"week"` : un cran par semaine du calendrier. |
| `every`       | entier | `1`      | Nombre de séances validées (ou de semaines) pour un cran. Ex. un exo fait 2 fois par semaine avec `"every": 2` = +1 cran par semaine. |
| `reps`        | entier | —        | Répétitions ajoutées par cran. Nécessite `reps` chiffré. |
| `maxReps`     | entier | —        | Plafond de répétitions. |
| `duration`    | texte  | —        | Durée ajoutée par cran (`"5s"`). Nécessite `duration` chiffrée. |
| `maxDuration` | texte  | —        | Plafond de durée (`"90s"`). |
| `load`        | texte  | —        | Charge ajoutée par cran (`"1kg"`). Nécessite `load` chiffrée. |
| `maxLoad`     | texte  | —        | Plafond de charge. |
| `addSets`     | entier | `0`      | Séries ajoutées quand le plafond de reps (ou de durée) est atteint. 0 à 5. |
| `maxSets`     | entier | —        | Nombre de séries maximum. |

Au moins un pas (`reps`, `duration` ou `load`) est obligatoire.

Exemple : 3 × 8 pompes, +1 rep par séance validée jusqu'à 12, puis une série de plus en repartant à 8, 5 séries max.

```json
{
  "format": "flemmefit",
  "version": 1,
  "name": "Pompes progressives",
  "repeat": "forever",
  "weeks": [
    {
      "days": [
        {
          "day": 1,
          "exercises": [
            { "name": "Pompes", "sets": 3, "reps": 8, "rest": "60s",
              "progression": { "reps": 1, "maxReps": 12, "addSets": 1, "maxSets": 5 } },
            { "name": "Gainage", "sets": 2, "duration": "30s",
              "progression": { "duration": "5s", "maxDuration": "90s" } }
          ]
        }
      ]
    }
  ]
}
```

**Ajustement par le max.** Le max noté sur une série à fond ajuste la progression de l'exercice, prudemment :
nettement au-dessus du prévu (≥ 3 pas) = +1 cran, très au-dessus (≥ 6 pas) = +2 crans, en dessous = −1 cran
(au total entre −2 et +4 crans).

## Tests et paliers 📏

Pour un programme « à démarrage objectif » (100 pompes, 50 tractions…) : un test à fond choisit le palier de départ,
et le test refait plus tard fait passer au palier suivant.

```json
{
  "format": "flemmefit",
  "version": 1,
  "name": "100 pompes maison",
  "tests": [
    { "id": "pompes", "name": "Pompes", "unit": "reps", "description": "Max d'affilée, corps gainé." }
  ],
  "weeks": [
    { "days": [
      { "day": 1, "exercises": [
        { "name": "Pompes", "test": "pompes", "amrap": true, "rest": "60s",
          "levels": [
            { "from": 0,  "sets": 5, "reps": "2", "note": "Sur les genoux." },
            { "from": 6,  "sets": 5, "reps": "5" },
            { "from": 11, "sets": 5, "reps": "8" }
          ],
          "progression": { "reps": 1 } }
      ] }
    ] },
    { "label": "Test", "days": [
      { "day": 1, "exercises": [ { "name": "Test pompes", "test": "pompes", "retest": true } ] }
    ] }
  ]
}
```

- **Test** (`tests[]`) : `id` et `name` obligatoires, `description` (consigne), `unit` = `"reps"` (défaut) ou `"seconds"`.
- À l'import, l'app demande le résultat de chaque test (facultatif, modifiable ensuite par *⋮ → Tests de départ*).
  Sans résultat : palier le plus facile.
- **Palier** (`levels[]`) : le palier retenu est le dernier dont `from` ≤ résultat du test (`from` croissants).
  Ses champs `sets`, `reps`, `duration`, `load`, `rest` remplacent ceux de l'exercice ; `note` s'ajoute à la description.
- La `progression` s'applique **depuis le départ du palier**, et repart de zéro quand le palier change.
- Un exercice `retest` met à jour le résultat du test avec le max noté : les séances suivantes changent de palier
  si besoin. Il n'est jamais allégé par la décharge, ce qui permet de placer les tests en semaine de décharge.

## Semaine de décharge 🌿

Une semaine sur `deloadEvery` (la 4e, la 8e… par défaut) est allégée de **50 %** : séries divisées par deux
(arrondi au-dessus), ou répétitions/durée divisées par deux pour un exercice à une seule série.
Elle ne fait pas progresser et n'a jamais de Boss.

## Jours Boss 💀

Toutes les **3 à 4 semaines**, une séance tirée au hasard devient un Boss (tirage fixe : rouvrir l'app ne le
change pas ; repousser la séance déplace le Boss avec elle). Il se dévoile **la veille**.
- Chiffres de la semaine prochaine (+1 cran de progression), et **dernière série à fond** sur chaque exercice.
- En cochant un exercice, l'app demande le **max** fait sur la série à fond. Objectif affiché : dernier max + 1.
- Boss entièrement validé = **Boss vaincu** dans les stats.

## Suivi dans l'app

- Cocher = fait comme prévu. **Appui long** (ou crayon) = noter ce qui a réellement été fait :
  en dessous du prévu, l'exercice est **partiel** (la journée n'est pas validée, sa progression n'avance pas) ;
  on peut aussi y noter un max n'importe quel jour.
- **Stats** : par programme (onglet *Stats*) et globales (bouton *📊 Stats* de l'accueil). Les exercices
  de même `id` (ou même nom) sont regroupés, y compris entre programmes.

## Règles de validation

L'import est refusé (avec la liste des erreurs) si :

- le JSON est mal formé, ou `version` ≠ 1, ou `format` présent mais ≠ `"flemmefit"` ;
- `name` est vide, `weeks` est vide, ou le programme ne contient aucune séance ;
- `repeat` est hors de 1–100, ou `semaines × repeat` dépasse 520 ;
- un `day` est hors de 1–7 ou en double dans une même semaine ;
- une séance n'a aucun exercice, un exercice n'a pas de `name`, ou `sets` est hors de 1–100 ;
- `deloadEvery` vaut autre chose que 0 ou 2–52 ;
- un `test` ne correspond à aucun id de `tests`, des `levels` sont sans `test` ou avec des `from` non croissants,
  ou un exercice `retest` n'a pas de `test` ;
- une `progression` n'a aucun pas, a un pas sur une valeur non chiffrée (ex. `reps` = `"max"`),
  ou une valeur illisible.

Tolérances : les **champs inconnus sont ignorés** (tu peux ajouter tes propres notes, ex. `"_commentaire"`),
l'ordre des jours dans `days` est libre, et les espaces en trop sont retirés.

## Importer dans l'app

- **Depuis un fichier** : bouton *Importer → Depuis un fichier*, ou *Ouvrir avec FlemmeFit* depuis un
  gestionnaire de fichiers.
- **Coller du texte** : *Importer → Coller du texte*, ou *Partager → FlemmeFit* depuis une autre app
  (pratique pour un JSON généré dans une conversation).

Ensuite, choisis la date de début et valide.

## Évolution du format

- Les ajouts compatibles (nouveaux champs **optionnels**) restent en `"version": 1`.
- Un changement incompatible passerait en `"version": 2` ; l'app continuerait à lire la version 1.

## Écrire un programme en texte libre

Pour qu'un programme écrit à la main puisse être converti au mieux, voici ce qu'il peut contenir.
Seul ce qui est en **gras** est indispensable ; le reste a une valeur par défaut.

**Programme**
- **Nom.**
- **Jours d'entraînement et de repos** de la semaine type (Jour 1 à 7).
- Durée : illimitée par défaut, ou nombre de semaines.
- Niveau général (débutant complet par défaut), objectif (force, endurance, bouger…), matériel disponible.

**Séance**
- **Liste des exercices**, titre de la séance (sinon déduit), consignes générales.

**Exercice**
- **Nom**, et comment le faire (position, tempo, pièges).
- **Ce que tu arrives à faire aujourd'hui** (« 6 pompes lentes », « gainage 40 s ») : l'info la plus utile.
- Séries (3 par défaut), répétitions ou durée, repos (60 s par défaut, 90 s pour les exos durs), charge.
- Si c'est le même exercice qu'un autre jour (progression commune) ou non.

**Progression (facultatif)**
- Rythme (chaque semaine ou seulement si validé), objectif ou plafond, exercices qui ne progressent pas.

**Valeurs par défaut pour débutant**, quand rien n'est précisé :

| Type d'exercice | Départ | Progression | Plafond |
|---|---|---|---|
| Répétitions | ~70 % du max actuel | +1 rep par semaine validée | 12–15 reps, puis +1 série en repartant du départ, 4–5 séries max |
| Durée (gainage…) | ~70 % du max actuel | +5 s par semaine validée | 60–90 s, puis +1 série |
| Charge | celle indiquée | +1 kg quand le haut des reps est atteint | — |
| Cardio / jeu | durée indiquée | +2 à 5 min toutes les 2 semaines | 30–45 min |

Plus une semaine de décharge à 50 % toutes les 4 semaines et des jours Boss toutes les 3 à 4 semaines.
