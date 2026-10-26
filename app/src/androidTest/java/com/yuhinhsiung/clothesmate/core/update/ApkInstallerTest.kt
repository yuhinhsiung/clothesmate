package com.yuhinhsiung.clothesmate.core.update

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApkInstallerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun unknownSourcesIntentTargetsThisPackage() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
        val resolver = context.packageManager.resolveActivity(intent, 0)
        assertNotNull("system has no handler for unknown-app-sources settings", resolver)
        assertEquals("com.android.settings", resolver!!.activityInfo.packageName)
    }

    @Test
    fun openUnknownSourcesSettingsTargetsThisPackage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        var captured: Intent? = null
        val recording = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                captured = intent
            }
        }
        ApkInstaller(recording).openUnknownSourcesSettings()
        assertNotNull("no intent was passed to startActivity", captured)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, captured!!.action)
        assertEquals("package:${context.packageName}", captured!!.dataString)
    }
}
