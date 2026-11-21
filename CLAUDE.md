# Manga Reminder, suivi des tomes de manga sur Android

Application Android pour se souvenir des tomes de manga qu'on possède (Kotlin, Jetpack Compose). Dépôt public sur GitHub, branche `main`. Le `README.md` décrit les fonctionnalités, la compilation et la publication des APK.

Les chemins et identifiants propres à la machine (dossier Todoom, ids des colonnes, JDK, SDK, clé de signature) sont dans `CLAUDE.local.md`, ignoré par Git : ne jamais les recopier dans un fichier commité.

## Tickets (règle impérative)

Les tickets sont dans le tableau Todoom « Manga Reminder » (clé `MR`).

1. **On ne fait que les tickets de la colonne « À faire ».** Ne jamais toucher aux autres colonnes (« Backlog », « En cours » d'un autre travail, « A verifier », « Terminé », « Give Up ») ni aux cartes qu'elles contiennent, même si on le demande en passant : le signaler à l'utilisateur.
2. En commençant un ticket, le déplacer de « À faire » vers « En cours », avant de coder.
3. Développer sur une branche dédiée, commiter, ouvrir la pull request vers `main` sur GitHub (c'est la « MR » dont parle l'utilisateur), puis activer sa fusion automatique (`gh pr merge <n> --auto --squash`) : GitHub la fusionne dès que la CI est verte, jamais avant. Tant que la fusion automatique n'est pas activée sur le dépôt (ticket MR-11), fusionner soi-même la pull request (`gh pr merge <n> --squash --delete-branch`), uniquement quand la CI est verte.
4. Une fois fusionnée : revenir sur `main` et la mettre à jour (`git checkout main` puis `git pull`), puis déplacer le ticket de « En cours » vers « A verifier ». L'agent n'est pas prévenu de la fusion : il vérifie l'état de la pull request au message suivant de l'utilisateur.
5. « A verifier » → « Terminé » : **uniquement l'utilisateur**, qui teste lui-même.

Les seuls déplacements permis à l'agent : « À faire » → « En cours » → « A verifier ». Si la pull request ne couvre qu'une partie d'un ticket, le dire et ne pas le déplacer vers « A verifier ». Le premier ticket d'une colonne est celui dont l'ordre est le plus bas. Déplacer les cartes avec les outils Todoom (`move_card`).

## Principes de l'application

- **La collection de l'utilisateur ne doit jamais être perdue.** `series.json` est écrit en tout-ou-rien (fichier temporaire puis renommage) ; un fichier illisible est mis de côté, jamais écrasé.
- **Format de `series.json`** : c'est aussi le format de l'export / import. Un nouveau champ est facultatif à la lecture (valeur par défaut s'il manque), pour relire les anciennes sauvegardes.
- **Données importées = données non fiables** : toute valeur lue (`SeriesRepository.fromJson`, cache `releases.json`, réponses réseau) est bornée avant usage (tomes 1..999, longueur des titres, id uniques, taille des fichiers).
- **Réseau** : HTTPS uniquement, taille de réponse limitée, aucun jeton ni secret dans l'application (le dépôt et l'APK sont publics).
- Textes de l'interface en français.

## Compiler

```bash
./gradlew assembleRelease   # Windows : .\gradlew.bat assembleRelease (le .\ est obligatoire)
./gradlew assembleDebug
```

`JAVA_HOME` doit pointer vers un JDK 17 ou plus récent.

- Le téléphone de l'utilisateur tourne en **version de publication**, signée avec la clé de `keystore.properties` (hors dépôt). **Ne jamais désinstaller l'application du téléphone** : cela efface la collection.
- Un APK signé avec une autre clé (debug, clé jetable de la CI) ne s'installe pas par-dessus : ne pas en proposer à l'utilisateur.

## Tests et intégration continue

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug   # avant de pousser
```

- Tests unitaires dans `app/src/test` (JUnit 4). Toute logique pure modifiée ou ajoutée vient avec ses tests dans la même pull request.
- CI : `.github/workflows/build-apk.yml` sur chaque pull request et sur `main`. Ne fusionner une pull request que si la CI est verte.
- Chaque push sur `main` republie la release `latest` ; les tags `vX.Y` publient une release versionnée.

## Git

- Messages de commit en français, courts, à l'impératif ou descriptifs.
- Ne jamais commiter `local.properties`, `keystore.properties`, `CLAUDE.local.md`, des clés de signature ou des APK.
- L'utilisateur travaille sous Windows PowerShell 5.1 : les commandes qu'on lui donne n'utilisent pas `&&`.
