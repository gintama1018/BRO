package com.gintama.novabrowser.offline

import com.gintama.novabrowser.core.navigation.UrlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying OfflinePageManager filename sanitization, archive format detection,
 * and UrlSanitizer local content scheme preservation.
 */
class OfflinePageManagerTest {

    @Test
    fun sanitizeFileName_removesIllegalFilesystemCharacters() {
        val rawTitle = "Article: Why 100% On-Device AI <Wins> in 2026? / A Deep-Dive"
        val sanitized = OfflinePageManager.sanitizeFileName(rawTitle, "pdf")

        assertFalse("Filename should not contain colons", sanitized.contains(":"))
        assertFalse("Filename should not contain question marks", sanitized.contains("?"))
        assertFalse("Filename should not contain slashes", sanitized.contains("/"))
        assertFalse("Filename should not contain angle brackets", sanitized.contains("<") || sanitized.contains(">"))
        assertTrue("Filename should end with .pdf", sanitized.endsWith(".pdf"))
    }

    @Test
    fun sanitizeFileName_preventsDuplicateExtensions() {
        val alreadyPdf = "financial_report.pdf"
        val result = OfflinePageManager.sanitizeFileName(alreadyPdf, "pdf")
        assertEquals("financial_report.pdf", result)

        val alreadyMht = "wikipedia_article.mht"
        val mhtResult = OfflinePageManager.sanitizeFileName(alreadyMht, "mht")
        assertEquals("wikipedia_article.mht", mhtResult)
    }

    @Test
    fun sanitizeFileName_handlesBlankAndPathTraversal() {
        val traversal = "../../../etc/passwd"
        val sanitized = OfflinePageManager.sanitizeFileName(traversal, "mht")
        assertFalse("Should not have path traversal slashes", sanitized.contains("/"))
        assertFalse("Should not have path traversal backslashes", sanitized.contains("\\"))
        assertTrue("Should end with .mht", sanitized.endsWith(".mht"))

        val empty = "     "
        val emptyResult = OfflinePageManager.sanitizeFileName(empty, "pdf")
        assertEquals("Nova_Saved_Page.pdf", emptyResult)
    }

    @Test
    fun isWebArchive_detectsMhtAndMhtml() {
        assertTrue(OfflinePageManager.isWebArchive("article.mht"))
        assertTrue(OfflinePageManager.isWebArchive("article.mhtml"))
        assertTrue(OfflinePageManager.isWebArchive("DOWNLOAD_SNAPSHOT.MHT"))
        assertTrue(OfflinePageManager.isWebArchive("https://example.com/archive.mhtml?token=123#heading"))

        assertFalse(OfflinePageManager.isWebArchive("page.html"))
        assertFalse(OfflinePageManager.isWebArchive("document.pdf"))
        assertFalse(OfflinePageManager.isWebArchive(""))
        assertFalse(OfflinePageManager.isWebArchive(null))
    }

    @Test
    fun urlSanitizer_preservesContentAndFileSchemes() {
        val contentUri = "content://com.gintama.novabrowser.fileprovider/downloads/saved.mht"
        val sanitizedContent = UrlSanitizer.sanitizeInput(contentUri)
        assertEquals("content:// scheme must be preserved as valid URL", contentUri, sanitizedContent)

        val fileUri = "file:///storage/emulated/0/Download/snapshot.mht"
        val sanitizedFile = UrlSanitizer.sanitizeInput(fileUri)
        assertEquals("file:// scheme must be preserved as valid URL", fileUri, sanitizedFile)
    }
}
