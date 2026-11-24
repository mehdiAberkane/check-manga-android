package com.mangareminder.releases

import java.io.IOException

// --- Échecs de la vérification des sorties, sans dépendance Android (testé en unitaire). ---

/** Réponse HTTP autre que 200 (le code permet de distinguer un quota épuisé d'une panne). */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

enum class CheckFailure {
    /** HTTP 429 : quota anonyme de Google Books épuisé. Ne se résout pas en quelques secondes. */
    QUOTA,
    /** Pas de réseau, délai dépassé, erreur serveur : un nouvel essai peut réussir. */
    NETWORK,
    /** Réponse illisible. */
    INVALID,
}

fun failureOf(error: Exception): CheckFailure = when {
    error is HttpStatusException && error.code == 429 -> CheckFailure.QUOTA
    error is IOException -> CheckFailure.NETWORK
    else -> CheckFailure.INVALID
}

/** Nouveaux essais au plus, pour une panne passagère ; ensuite, la vérification périodique prend le relais. */
const val MAX_CHECK_RETRIES = 2

/**
 * Faut-il demander un nouvel essai à WorkManager ?
 * Seulement si toutes les séries ont échoué pour une raison passagère (réseau), et pas plus de
 * [MAX_CHECK_RETRIES] fois. Jamais sur un quota épuisé : réessayer à quelques secondes d'écart
 * ne sert à rien et bloquait « Vérifier maintenant ».
 */
fun shouldRetry(failures: List<CheckFailure>, checked: Int, attempt: Int): Boolean =
    checked > 0 &&
        failures.size == checked &&
        failures.all { it == CheckFailure.NETWORK } &&
        attempt < MAX_CHECK_RETRIES

/** Dernier échec, gardé avec sa date pour être affiché même après un redémarrage. */
data class CheckError(val failure: CheckFailure, val failedSeries: Int, val at: Long)

/** Erreur à retenir après une vérification : la plus grave si plusieurs, aucune si tout a réussi. */
fun checkError(failures: List<CheckFailure>, now: Long): CheckError? {
    if (failures.isEmpty()) return null
    val main = when {
        CheckFailure.QUOTA in failures -> CheckFailure.QUOTA
        CheckFailure.NETWORK in failures -> CheckFailure.NETWORK
        else -> CheckFailure.INVALID
    }
    return CheckError(main, failures.size, now)
}
