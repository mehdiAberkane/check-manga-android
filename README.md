# Manga Reminder

Application Android pour se souvenir des tomes de manga qu'on possède déjà.

## Fonctionnalités

- Liste des séries avec recherche, nombre de tomes possédés et tomes manquants.
- Filtres de la liste (toutes, incomplètes, complètes, surveillées, nouveautés, prêtées) et tri (titre, ajout
  récent, tomes manquants), mémorisés entre deux ouvertures.
- Fiche d'une série : grille des tomes, un appui coche ou décoche un tome.
- Ajout rapide de plusieurs tomes : `1-12, 15, 17`.
- Appui long sur un tome possédé : « Lu » et « Prêté à… » (nom, date). Petites icônes dans la grille,
  résumé « Lus » / « Prêtés » sur la fiche et la carte, filtre « Prêtés » dans la liste. Décocher un tome
  efface son statut. Champs facultatifs `read` et `loans` dans `series.json` (donc dans l'export).
- Scanner un tome (menu ⋮) : le scanner de code-barres de Google (services Play, sans permission caméra
  dans l'app) lit l'ISBN, l'app le cherche sur Google Books et répond « tu l'as » / « tu ne l'as pas »
  (avec « Je l'ai acheté »), ou propose de créer la série. Saisie de l'ISBN au clavier si le scanner est
  fermé ou indisponible. Le module ML Kit du scanner inclut les statistiques d'utilisation anonymes de Google.
- En magasin : chercher « one piece 45 » (ou `t45`, `tome 45`, `#45`) répond directement en haut de la liste
  « tu l'as » ou « tu ne l'as pas », avec un bouton « Je l'ai acheté » qui coche le tome.
- Nombre total de tomes optionnel (série en cours) : la case « + » marque le tome suivant.
- Données stockées sur le téléphone (`series.json`), incluses dans la sauvegarde Android (Google et
  transfert vers un nouveau téléphone). Seule la collection est sauvegardée : pas les fichiers
  temporaires, ni le cache des sorties (`releases.json`, reconstruit), ni un `series.json.corrupt` mis de
  côté. Règles : `res/xml/data_extraction_rules.xml` (Android 12+) et `res/xml/backup_rules.xml` (8 à 11).
- Export / import de la collection dans un fichier JSON (menu ⋮). Après un import ou la suppression d'une série,
  un message « Annuler » permet de revenir en arrière pendant une dizaine de secondes.
- Sauvegarde automatique (menu ⋮) : on choisit une fois un dossier (téléphone, Google Drive…) et
  l'app y écrit la collection une trentaine de secondes après chaque modification. Un fichier par jour
  (`manga-reminder-AAAA-MM-JJ.json`, même format que l'export), les 7 derniers sont gardés ; aucun autre
  fichier du dossier n'est touché. Une collection vide ou illisible n'est jamais sauvegardée, pour ne pas
  écraser de bonnes versions. Restauration : « Importer une sauvegarde ».
- Surveillance des sorties : la cloche 🔔 d'une série lance la recherche des nouveaux tomes
  (Google Books), deux fois par jour en arrière-plan, avec une notification quand un tome plus récent
  que le dernier possédé (ou que le total indiqué) apparaît. « Vérifier les sorties » (menu ⋮) force
  une recherche. Le jour de la sortie d'un tome daté qu'on n'a pas encore (ou dans les 7 jours si le
  téléphone était hors ligne), une seconde notification rappelle qu'il est disponible, une seule fois.
  Les notifications et les lignes de sortie de la fiche ont un bouton « Je l'ai », qui coche le tome.

  Un livre n'est compté comme un tome de la série que si son titre commence par le titre de la série, suivi
  seulement de mots neutres (« édition originale », « tome »…) avant le numéro : les séries dérivées
  (« Dragon Ball Super », « Boruto - Naruto Next Generations ») et les éditions à numérotation différente
  (perfect, double…) sont écartées. Nommer la série comme sur la couverture.

  L'API Google Books est appelée sans clé : son quota est partagé, une vérification peut échouer
  et sera retentée plus tard.
- Couvertures : la fiche d'une série propose « Choisir la couverture » parmi celles trouvées sur Google
  Books (une recherche, seulement à la demande). Sans choix, une série surveillée prend la couverture du
  plus petit tome trouvé. Miniatures en HTTPS depuis les serveurs d'images de Google Books uniquement,
  500 Ko au plus, gardées dans le cache de l'app (hors sauvegarde). La couverture choisie est enregistrée
  dans `series.json` (champ `cover`, facultatif) et donc dans l'export.
- Liste « À acheter » (menu ⋮) : tomes manquants et nouveaux tomes trouvés par la surveillance (parus ou à
  paraître), toutes séries confondues, avec un bouton Partager (texte simple, ex. « One Piece : 45, 47-48 »).
- « Statistiques » (menu ⋮) : nombre de séries, de tomes possédés, de séries complètes, de tomes
  manquants, de séries surveillées, de tomes lus et prêtés, puis les séries les plus fournies et celles où il
  manque le plus de tomes (un appui ouvre la fiche).
- « Prochaines sorties » (menu ⋮) : tomes à paraître de toutes les séries surveillées, le plus proche
  d'abord, puis ceux déjà parus mais pas encore achetés. Lit les résultats de la surveillance, sans requête ;
  un appui ouvre la fiche de la série.
- Widget d'écran d'accueil « Tomes à acheter » : nombre de tomes à acheter (manquants, plus les tomes
  déjà parus après le dernier connu des séries surveillées) et les 3 prochaines sorties. Mis à jour après
  chaque modification et chaque vérification des sorties ; un appui ouvre la liste « À acheter ».
- Apparence (menu ⋮) : thème comme le téléphone / clair / sombre, noir pur (OLED), couleurs du téléphone
  (Material You, Android 12+) ou couleur principale au choix, taille des cases des tomes, fond d'écran tiré
  de la galerie (sélecteur de photos, sans permission), copié et réduit dans l'app, atténué par un filtre
  réglable, derrière toutes les pages. Réglages gardés sur le téléphone, hors collection et hors sauvegarde.
- Recherche de mise à jour (menu ⋮ > « Rechercher une mise à jour ») : l'app lit la dernière release
  stable de GitHub et vérifie son `version.json` (tag, `versionCode` plus récent, APK présent, taille,
  SHA-256 identique à l'empreinte calculée par GitHub). Seuls les hôtes GitHub sont contactés, en HTTPS.
  « Télécharger » ouvre le lien de l'APK dans le navigateur ; une fois le fichier ouvert, l'installeur
  d'Android met l'app à jour par-dessus, sans perdre la collection. L'empreinte SHA-256 attendue est
  affichée pour vérifier le fichier.

  Vérification automatique une fois par semaine (réseau requis) : si une version plus récente existe, une
  notification (canal « Mises à jour de l'application », distinct des sorties) le signale, une seule fois
  par version ; un appui ouvre la fenêtre de mise à jour. Rien n'est téléchargé sans l'utilisateur.
  Désactivable dans la fenêtre de mise à jour (« Vérifier chaque semaine et me prévenir »).

  L'app n'installe plus elle-même l'APK (ni permission `REQUEST_INSTALL_PACKAGES`) : Play Protect
  bloquait la mise à jour, une app qui télécharge et installe des APK ressemblant à une appli malveillante.
  Play Protect analyse quand même l'APK à l'installation depuis le navigateur.

## Stack

Kotlin, Jetpack Compose (Material 3), minSdk 26 (Android 8.0), targetSdk 36 (Android 16), compilé avec
les API d'Android 17 (compileSdk 37.2, exigé par les bibliothèques AndroidX). Gradle 9, AGP 9 (qui
compile Kotlin lui-même). Toutes les versions sont dans `gradle/libs.versions.toml`.

## Compiler

Avec Android Studio : *File > Open* sur ce dossier, puis *Run*.

En ligne de commande (JDK 17+ et SDK Android requis, `local.properties` avec `sdk.dir=...`) :

```bash
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

### Version release signée

Créer un fichier `keystore.properties` à la racine (ignoré par git) :

```properties
storeFile=C:/chemin/vers/manga-reminder-release.jks
storePassword=...
keyAlias=manga-reminder
keyPassword=...
```

puis :

```bash
./gradlew assembleRelease
```

L'APK signé est dans `app/build/outputs/apk/release/app-release.apk`. Garder la clé `.jks` et son mot de passe
en lieu sûr : sans eux, impossible de publier une mise à jour installable par-dessus.

### Intégrité de Gradle et des dépendances

- `gradle-wrapper.properties` contient `distributionSha256Sum` (somme officielle publiée sur gradle.org) :
  une distribution Gradle modifiée est refusée. La CI valide aussi `gradle-wrapper.jar`
  (`gradle/actions/wrapper-validation`).
- `gradle/verification-metadata.xml` liste le SHA-256 de chaque dépendance : un artefact modifié fait
  échouer la compilation.

Après une mise à jour de dépendance ou de plugin, régénérer ce fichier :

```bash
./gradlew --write-verification-metadata sha256 lintDebug testDebugUnitTest assembleDebug assembleRelease
```

`aapt2` est un artefact propre à chaque système : Gradle n'enregistre que celui de la machine. Ajouter à
la main les variantes `-linux.jar` (utilisée par la CI) et `-osx.jar`, avec leur SHA-256, en vérifiant le
fichier téléchargé contre le `.sha1` publié sur `https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/<version>/`.
Relire le diff du fichier : chaque nouvelle entrée doit correspondre à une dépendance attendue.

La CI part d'un cache Gradle vide et résout des fichiers de métadonnées (`.pom`, `.module` de BOM)
que la compilation locale ne télécharge pas. Pour les enregistrer d'un coup, régénérer avec un cache
vide (PowerShell) :

```powershell
$env:GRADLE_USER_HOME = "$env:TEMP\gradle-home-vierge"; .\gradlew.bat --no-daemon --write-verification-metadata sha256 lintDebug testDebugUnitTest assembleDebug assembleRelease; Remove-Item Env:GRADLE_USER_HOME
```

S'il en manque encore, le log de la CI indique « Dependency verification failed » avec le nom des
fichiers, à ajouter après vérification contre le `.sha1` du dépôt Maven.

## Compilation automatique (GitHub Actions)

Le workflow `.github/workflows/build-apk.yml` compile l'APK à chaque push, pull request ou lancement manuel
(onglet *Actions > Compilation APK > Run workflow*).

- **Tag `vX.Y`** : **version stable**, release `vX.Y` avec `manga-reminder-X.Y.apk` et `version.json`,
  marquée *Latest*. C'est elle que renvoie `GET /repos/mehdiAberkane/check-manga-android/releases/latest`.
- **Push sur `main`** : **build de développement**, pre-release `latest` (jamais marquée *Latest*), remplacée
  à chaque push, toujours au même lien :
  https://github.com/mehdiAberkane/check-manga-android/releases/download/latest/manga-reminder.apk
- **Pull request** et lancement manuel : tests et APK **debug** en artefact du run (onglet *Actions*),
  sans secret ni publication.

Le workflow a deux jobs :

- `build` (tous les événements) : tests unitaires et APK debug, en lecture seule, sans aucun secret.
  C'est le check exigé par la protection de `main`.
- `release` (push sur `main` ou tag `v*`, après `build`) : APK release signé et publication. Lui seul a le
  droit d'écrire dans les releases et accède à la clé, via l'environnement protégé `release`.

Les actions sont épinglées sur un SHA de commit ; Dependabot (`.github/dependabot.yml`) propose leurs
mises à jour.

`version.json` décrit l'APK de la release :

```json
{ "versionCode": 5, "versionName": "1.4", "apk": "manga-reminder-1.4.apk", "sha256": "…", "commit": "…" }
```

### Publier une version stable

1. Dans `app/build.gradle.kts`, augmenter `versionCode` (entier, +1) et `versionName` (ex. `1.4`).
2. Faire fusionner ce changement dans `main`.
3. Créer et pousser le tag `v<versionName>` sur `main`.

PowerShell :

```powershell
git checkout main; git pull; git tag -a v1.4 -m "Manga Reminder 1.4"; if ($?) { git push origin v1.4 }
```

bash :

```bash
git checkout main && git pull && git tag -a v1.4 -m "Manga Reminder 1.4" && git push origin v1.4
```

Le workflow refuse de publier (et ne crée pas la release) si le tag ne vaut pas `v<versionName>`, ou si
`versionCode` n'est pas supérieur à celui de la version stable précédente.

### Signature

Les APK publiés (push sur `main`, tags `v*`) sont toujours des APK **release** signés avec la clé de l'app,
installables en mise à jour par-dessus la version du téléphone. Le workflow échoue si :

- les secrets de signature manquent (il ne publie jamais d'APK debug) ;
- l'APK est signé avec un autre certificat que celui attendu (`RELEASE_CERT_SHA256` dans le workflow).

Les pull requests et les lancements manuels produisent un APK **debug** (clé jetable, non installable
par-dessus), uniquement en artefact : ils n'ont jamais accès à la clé.

Secrets à créer dans l'environnement `release` (*Settings > Environments > release*), qui n'est
accessible qu'à `main` et aux tags `v*` :

| Secret              | Valeur                                        |
|---------------------|-----------------------------------------------|
| `KEYSTORE_BASE64`   | le fichier `.jks` encodé : `base64 -w0 manga-reminder-release.jks` |
| `KEYSTORE_PASSWORD` | `storePassword`                               |
| `KEY_ALIAS`         | `keyAlias` (ex. `manga-reminder`)             |
| `KEY_PASSWORD`      | `keyPassword`                                 |
