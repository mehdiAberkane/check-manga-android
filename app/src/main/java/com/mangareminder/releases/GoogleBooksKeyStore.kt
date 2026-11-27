package com.mangareminder.releases

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clé d'API Google Books de l'utilisateur, chiffrée par une clé AES du Keystore Android (qui ne
 * quitte pas le téléphone), dans des préférences privées. La sauvegarde Android ne garde que
 * series.json (data_extraction_rules.xml) : la clé n'est ni sauvegardée, ni exportée, ni transférée.
 */
object GoogleBooksKeyStore {
    private const val PREFS = "google_books"
    private const val VALUE = "api_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "google_books_api_key"
    private const val GCM_TAG_BITS = 128

    @Volatile private var loaded = false
    @Volatile private var cached: String? = null

    /** Clé à joindre aux requêtes, ou null (requêtes anonymes). Appeler [init] avant. */
    val key: String? get() = cached

    /** Lit la clé une fois (hors du fil principal : déchiffrement). */
    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            cached = runCatching { read(context) }.getOrNull()
            loaded = true
        }
    }

    /** @return false si la clé n'a pas la bonne forme ou n'a pas pu être chiffrée (rien n'est changé). */
    fun save(context: Context, raw: String): Boolean {
        val clean = normalizeApiKey(raw) ?: return false
        val stored = runCatching { encrypt(clean) }.getOrNull() ?: return false
        prefs(context).edit { putString(VALUE, stored) }
        cached = clean
        loaded = true
        return true
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(VALUE) }
        cached = null
        loaded = true
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): String? {
        val stored = prefs(context).getString(VALUE, null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val ivSize = bytes[0].toInt()
        if (ivSize !in 12..16 || bytes.size <= 1 + ivSize) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, 1, ivSize))
        val plain = cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize).toString(Charsets.UTF_8)
        return normalizeApiKey(plain)
    }

    /** [taille de l'IV][IV][texte chiffré + étiquette], en Base64. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val out = byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
