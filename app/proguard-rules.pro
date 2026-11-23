# Règles R8 de la version de publication (minification et réduction des ressources activées).
#
# Le dépôt est public : renommer les classes n'apporte rien, et des traces de plantage lisibles
# valent mieux qu'un fichier de correspondance à garder pour chaque version. R8 réduit et optimise
# le code, sans l'obfusquer.
-dontobfuscate

# Pas de règle propre au code de l'app : aucune réflexion (le JSON passe par org.json, les écrans par
# Compose). WorkManager et les services Play fournissent leurs règles dans leurs bibliothèques
# (consumer rules) ; les workers et récepteurs déclarés dans le manifeste sont gardés par AAPT.
# Seul ML Kit en demande en plus, ci-dessous.

# ML Kit (scanner de code-barres) retrouve ses composants par réflexion (registrars Firebase déclarés
# dans le manifeste). Sans ces règles, R8 fusionne ou retire ces classes et l'app plante à
# l'ouverture du scanner (NullPointerException dans ML Kit, constaté sur le téléphone).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
