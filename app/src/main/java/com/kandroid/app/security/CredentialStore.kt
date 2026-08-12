package com.kandroid.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kandroid.app.data.Credentials
import com.kandroid.app.data.AppMode
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "kandroid.credentials.v1"

    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    fun save(value: Credentials) {
        val plain = listOf(value.serverUrl.trim(), value.username, value.token).joinToString("\u0000").toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain))
        prefs.edit().putString("value", encoded).apply()
    }

    fun load(): Credentials? = runCatching {
        val raw = Base64.getDecoder().decode(prefs.getString("value", null) ?: return null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        }
        val parts = cipher.doFinal(raw.copyOfRange(12, raw.size)).decodeToString().split('\u0000')
        Credentials(parts[0], parts[1], parts[2])
    }.getOrNull()

    fun clear() { prefs.edit().clear().apply() }

    fun mode(): AppMode = prefs.getString("mode", null)?.let {
        runCatching { AppMode.valueOf(it) }.getOrNull()
    } ?: if (load() != null) AppMode.KANBOARD else AppMode.UNCONFIGURED

    fun setMode(mode: AppMode) { prefs.edit().putString("mode", mode.name).apply() }

    fun clearCredentials() { prefs.edit().remove("value").apply() }
}
