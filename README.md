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

- **Push sur `main`** : l'APK est publié dans la release `latest`, toujours au même lien (pratique depuis le téléphone) :
  https://github.com/mehdiAberkane/check-manga-android/releases/download/latest/manga-reminder.apk
- **Tag `v*`** (ex. `v1.3`) : release versionnée avec `manga-reminder-<version>.apk`.
- **Pull request** : l'APK est disponible en artefact du run (onglet *Actions*).

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
