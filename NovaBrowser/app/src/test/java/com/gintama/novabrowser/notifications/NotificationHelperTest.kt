package com.gintama.novabrowser.notifications

import com.gintama.novabrowser.downloads.DownloadHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying notification channel definitions and quarantine file inspection policies.
 */
class NotificationHelperTest {

    @Test
    fun verifyNotificationChannelDefinitions() {
        assertEquals("nova_downloads", NovaNotificationHelper.CHANNEL_DOWNLOADS)
        assertEquals("nova_security", NovaNotificationHelper.CHANNEL_SECURITY)
        assertEquals("nova_offline", NovaNotificationHelper.CHANNEL_OFFLINE)
    }

    @Test
    fun downloadHandler_correctlyIdentifiesRiskyExecutableExtensions() {
        assertTrue("apk must trigger quarantine", DownloadHandler.isRiskyExtension("apk"))
        assertTrue("dex must trigger quarantine", DownloadHandler.isRiskyExtension("dex"))
        assertTrue("exe must trigger quarantine", DownloadHandler.isRiskyExtension("exe"))
        assertTrue("sh script must trigger quarantine", DownloadHandler.isRiskyExtension("sh"))
        assertTrue("bat script must trigger quarantine", DownloadHandler.isRiskyExtension("bat"))
        assertTrue("js script must trigger quarantine", DownloadHandler.isRiskyExtension("js"))
        assertTrue("msi installer must trigger quarantine", DownloadHandler.isRiskyExtension("msi"))
        assertTrue("leading dot extension must trigger quarantine", DownloadHandler.isRiskyExtension(".apk"))
    }

    @Test
    fun downloadHandler_permitsSafeExtensionsWithoutQuarantine() {
        assertFalse("pdf must be safe", DownloadHandler.isRiskyExtension("pdf"))
        assertFalse("png image must be safe", DownloadHandler.isRiskyExtension("png"))
        assertFalse("jpg image must be safe", DownloadHandler.isRiskyExtension("jpg"))
        assertFalse("html document must be safe", DownloadHandler.isRiskyExtension("html"))
        assertFalse("mht web archive must be safe", DownloadHandler.isRiskyExtension("mht"))
        assertFalse("zip archive must be safe", DownloadHandler.isRiskyExtension("zip"))
        assertFalse("mp3 audio must be safe", DownloadHandler.isRiskyExtension("mp3"))
        assertFalse("mp4 video must be safe", DownloadHandler.isRiskyExtension("mp4"))
    }
}
