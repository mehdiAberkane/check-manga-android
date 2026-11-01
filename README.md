# Manga Reminder

Application Android pour se souvenir des tomes de manga qu'on possède déjà.

## Fonctionnalités

- Liste des séries avec recherche, nombre de tomes possédés et tomes manquants.
- Fiche d'une série : grille des tomes, un appui coche ou décoche un tome.
- Ajout rapide de plusieurs tomes : `1-12, 15, 17`.
- Nombre total de tomes optionnel (série en cours) : la case « + » marque le tome suivant.
- Données stockées sur le téléphone (`series.json`), incluses dans la sauvegarde Android.
- Export / import de la collection dans un fichier JSON (menu ⋮).

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
