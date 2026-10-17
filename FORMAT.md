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
  (S1, S2, S1, S2, S1, S2, S1, S2).
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
| `repeat`      | entier  | non (défaut `1`) | Nombre de fois où la liste `weeks` est jouée. 1 à 100. |
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
| `sets`        | entier | non         | `4`     | Nombre de séries (1 à 100). |
| `reps`        | texte  | non         | `"8-10"` | Répétitions par série. Un nombre (`12`) est aussi accepté. |
| `duration`    | texte  | non         | `"45s"`, `"20min"` | Durée (gainage, cardio, étirements…). |
| `load`        | texte  | non         | `"60 kg"`, `"élastique rouge"` | Charge. |
| `rest`        | texte  | non         | `"90s"` | Repos entre les séries. |
| `description` | texte  | non         | `"Dos droit…"` | Explication détaillée, affichée en touchant l'exercice. |

Affichage dans l'app : `sets × reps · load · repos rest`, par ex. **4 × 8-10 · 60 kg · repos 90s**.

## Règles de validation

L'import est refusé (avec la liste des erreurs) si :

- le JSON est mal formé, ou `version` ≠ 1, ou `format` présent mais ≠ `"flemmefit"` ;
- `name` est vide, `weeks` est vide, ou le programme ne contient aucune séance ;
- `repeat` est hors de 1–100, ou `semaines × repeat` dépasse 520 ;
- un `day` est hors de 1–7 ou en double dans une même semaine ;
- une séance n'a aucun exercice, un exercice n'a pas de `name`, ou `sets` est hors de 1–100.

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

## Générer un programme à partir d'un texte libre

Pour convertir un programme écrit à la main (ou trouvé ailleurs), donne-le à un assistant avec cette consigne :

> Convertis ce programme au format FlemmeFit v1 décrit dans FORMAT.md. Une entrée `exercises` par exercice,
> `day` = position 1–7 dans la semaine, jours de repos omis, textes libres pour `reps`/`duration`/`load`/`rest`,
> et utilise `repeat` si les semaines se répètent à l'identique.
