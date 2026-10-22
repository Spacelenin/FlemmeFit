# Plan validé : variantes d'exercices

Rien n'est codé pour l'instant : c'est le plan sur lequel on s'est mis d'accord.

## Nouveaux exercices

| Famille | Variante | Remplace des séries de | Taux de départ (1 rep =) | Version facile en dessous du seuil |
|---|---|---|---|---|
| Pompes | Diamant | Pompes classiques (J5) ou lentes (J1) | 3,3 pompes | sur les genoux (vraie version dès 15 au test pompes) |
| Pompes | Pike | idem | 10 pompes | pieds loin des mains (dès 25) |
| Pompes | À une main | idem | 5 pompes | main sur table → chaise → marche → sol (dès 40) |
| Jambes | Cossack squat | Squats sur une jambe (J6) | 2,5 squats classiques | — |
| Jambes | Squat sauté | idem | 2,5 squats classiques | — |
| Abdos | Battements de jambes 30 s | Abdos (J2 ou J6) | 15 abdos | — |
| Gainage | Bras pliés (mi-pompe) | Gainage (J2) | ×3 la durée | (dès 45 s au test gainage) |

Hypothèse de départ pour les jambes : 1 squat sur une jambe ≈ 4 squats classiques. Les mesures corrigent ensuite.

## Jours variante (tirage aléatoire)

- Au début de chaque cycle de 4 semaines, l'app tire au sort des jours variante en semaines 1 à 3.
- Un jour variante : moins de séries de l'exercice de base (environ 2 sur 5 en moins), puis un bloc de 3 séries de la variante à ~60 % du max estimé, la dernière au max.
- Seulement les jours où l'exercice de base est prévu. Au plus un bloc variante par séance. Jamais en semaine 4 (allégée et tests).
- Nombre de jours variante par variante et par cycle : 1 sous le seuil (version facile), 2 au niveau moyen, 3 à partir de 1,5× le seuil.
- Tirage fixe (stable si on rouvre l'app), non affiché à l'avance sur le calendrier.
- Prévenu en début de séance (page du jour et mode guidé : « 🎲 aujourd'hui : pike »), rappel au moment du bloc.
- Bouton « Pas aujourd'hui » : séance normale, le jour variante est replacé plus tard dans le cycle.

## Détection du niveau

- Max estimé de la variante = résultat du test de base ÷ taux.
- La série au max de chaque bloc donne le taux réel ; le taux perso est lissé (moyenne avec l'ancien) pour qu'une mauvaise séance ne fasse pas tout basculer.
- Le taux perso remplace celui de départ pour doser les blocs suivants.

## Autour

- Une courbe par variante dans Stats → Tests, avec « ton taux : x (départ y) ».
- Compteurs et badges : variantes comptées en équivalent de l'exercice de base, avec le taux perso.
- Tuto et aide mis à jour.
- Routine en `revision: 4` : la progression actuelle est gardée.
