package com.gis.reverse.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import org.json.JSONObject
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences("bsi_credentials", Context.MODE_PRIVATE)
    private val alias = "bsi.credentials.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(username: String, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = JSONObject().put("username", username).put("password", password).toString()
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }
    fun read(): Pair<String, String>? = try {
        val payload = prefs.getString("payload", null)
        val iv = prefs.getString("iv", null)
        if (payload == null || iv == null) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val data = JSONObject(String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8))
            data.getString("username") to data.getString("password")
        }
    } catch (_: Exception) { clear(); null }
    fun clear() { prefs.edit().clear().apply() }
}

class AndroidSessionCookies(private val origin: String = PortalParser.ORIGIN) : SessionCookies {
    private val manager = CookieManager.getInstance().also { it.setAcceptCookie(true) }
    private val main = Handler(Looper.getMainLooper())
    override fun get(url: String): String = manager.getCookie(url).orEmpty()
    override fun put(url: String, cookie: String) = change { done -> manager.setCookie(url, cookie) { done() } }
    override fun clear() {
        val names = manager.getCookie(origin).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
        names.forEach { name -> put(origin, "$name=; Max-Age=0; Path=/") }
    }
    // Keep rotated session cookies available to every native request.
    private fun change(action: (() -> Unit) -> Unit) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Cookie changes must run on the network worker" }
        val latch = CountDownLatch(1)
        main.post { action { latch.countDown() } }
        if (!latch.await(10, TimeUnit.SECONDS)) throw PortalError("Penyimpanan sesi belum siap. Coba lagi.")
        manager.flush()
    }
}
