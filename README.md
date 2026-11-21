# Manga Reminder

Application Android pour se souvenir des tomes de manga qu'on possède déjà.

## Fonctionnalités

- Liste des séries avec recherche, nombre de tomes possédés et tomes manquants.
- Fiche d'une série : grille des tomes, un appui coche ou décoche un tome.
- Ajout rapide de plusieurs tomes : `1-12, 15, 17`.
- Nombre total de tomes optionnel (série en cours) : la case « + » marque le tome suivant.
- Données stockées sur le téléphone (`series.json`), incluses dans la sauvegarde Android.
- Export / import de la collection dans un fichier JSON (menu ⋮).
- Surveillance des sorties : la cloche 🔔 d'une série lance la recherche des nouveaux tomes
  (Google Books), deux fois par jour en arrière-plan, avec une notification quand un tome plus récent
  que le dernier possédé (ou que le total indiqué) apparaît. « Vérifier les sorties » (menu ⋮) force
  une recherche.

  L'API Google Books est appelée sans clé : son quota est partagé, une vérification peut échouer
  et sera retentée plus tard.
- Mise à jour intégrée (menu ⋮ > « Rechercher une mise à jour ») : l'app lit la dernière release
  stable de GitHub, télécharge son APK et l'installe, sans perdre la collection. Avant l'installation,
  elle vérifie la taille, le SHA-256 (`version.json` et l'empreinte calculée par GitHub), le nom du
  paquet, le `versionCode` (jamais de retour en arrière) et le certificat, qui doit être celui de
  l'app installée. Seuls les hôtes GitHub sont contactés, en HTTPS.

  La première fois, Android demande d'autoriser l'app à installer des applications (« Installer des
  applis inconnues »), puis confirme chaque mise à jour.

## Stack

Kotlin, Jetpack Compose (Material 3), minSdk 26 (Android 8.0), targetSdk 35.

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

## Compilation automatique (GitHub Actions)

Le workflow `.github/workflows/build-apk.yml` compile l'APK à chaque push, pull request ou lancement manuel
(onglet *Actions > Compilation APK > Run workflow*).

- **Tag `vX.Y`** : **version stable**, release `vX.Y` avec `manga-reminder-X.Y.apk` et `version.json`,
  marquée *Latest*. C'est elle que renvoie `GET /repos/mehdiAberkane/check-manga-android/releases/latest`.
- **Push sur `main`** : **build de développement**, pre-release `latest` (jamais marquée *Latest*), remplacée
  à chaque push, toujours au même lien :
  https://github.com/mehdiAberkane/check-manga-android/releases/download/latest/manga-reminder.apk
- **Pull request** : l'APK et `version.json` sont disponibles en artefact du run (onglet *Actions*).

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

Les pull requests et les lancements manuels sans secrets produisent un APK **debug** (clé jetable, non
installable par-dessus), uniquement en artefact.

Secrets à créer dans *Settings > Secrets and variables > Actions* :

| Secret              | Valeur                                        |
|---------------------|-----------------------------------------------|
| `KEYSTORE_BASE64`   | le fichier `.jks` encodé : `base64 -w0 manga-reminder-release.jks` |
| `KEYSTORE_PASSWORD` | `storePassword`                               |
| `KEY_ALIAS`         | `keyAlias` (ex. `manga-reminder`)             |
| `KEY_PASSWORD`      | `keyPassword`                                 |
