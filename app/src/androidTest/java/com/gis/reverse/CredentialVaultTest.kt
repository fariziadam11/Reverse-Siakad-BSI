package com.gis.reverse

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gis.reverse.data.CredentialVault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialVaultTest {
    @Test fun syntheticCredentialsAreEncryptedAndCorruptedCiphertextIsRejected() {
        // Device-protected test preferences are separate from the app's normal credential preferences.
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val vault = CredentialVault(context)
        val preferences = context.getSharedPreferences("bsi_credentials", Context.MODE_PRIVATE)
        vault.clear()
        try {
            vault.save("fixture-nim", "fixture-password-for-keystore-test")
            assertEquals("fixture-nim" to "fixture-password-for-keystore-test", vault.read())
            assertFalse(preferences.all.values.any { it.toString().contains("fixture-password") || it.toString().contains("fixture-nim") })
            preferences.edit().putString("payload", "invalid-ciphertext").commit()
            assertNull(vault.read())
            assertTrue(preferences.all.isEmpty())
        } finally { vault.clear() }
    }
}
