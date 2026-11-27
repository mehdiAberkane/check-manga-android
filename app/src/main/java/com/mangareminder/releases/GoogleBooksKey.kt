package com.mangareminder.releases

// --- Clé d'API Google Books personnelle, sans dépendance Android (testé en unitaire). ---
// Chaque utilisateur crée sa propre clé : l'app et le dépôt sont publics, aucune clé n'y est livrée.

/** Une clé Google fait 39 caractères ; bornes larges pour accepter d'autres formats. */
private val API_KEY = Regex("[A-Za-z0-9_-]{20,100}")

/** Forme acceptable (longueur, lettres, chiffres, `-`, `_`), espaces autour retirés. */
fun normalizeApiKey(raw: String): String? = raw.trim().takeIf { it.matches(API_KEY) }

/**
 * Ajoute la clé à une adresse de l'API (qui a déjà des paramètres). Sans clé, ou avec une clé de
 * forme invalide, l'adresse reste anonyme. La clé n'a que des caractères sûrs : pas d'encodage.
 */
fun withApiKey(url: String, key: String?): String {
    val clean = key?.let { normalizeApiKey(it) } ?: return url
    return "$url&key=$clean"
}

/** Pour l'affichage : seulement les 4 derniers caractères (« …a1B2 »). */
fun maskApiKey(key: String): String = "…" + key.takeLast(4)

enum class KeyTestResult { OK, REFUSED, QUOTA, NETWORK }

/** Résultat d'un test de clé à partir de l'erreur levée par la requête (null : réponse 200). */
fun keyTestResult(error: Throwable?): KeyTestResult = when {
    error == null -> KeyTestResult.OK
    // 400 (clé invalide) et 403 (API non activée, clé restreinte) : la clé ne convient pas.
    error is HttpStatusException && error.code in setOf(400, 401, 403) -> KeyTestResult.REFUSED
    error is HttpStatusException && error.code == 429 -> KeyTestResult.QUOTA
    else -> KeyTestResult.NETWORK
}
