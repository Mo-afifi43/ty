package com.mhma.smartlearn.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun `name and extension from a pluginfile url`() {
        val url = "https://smartlearning.must.edu.eg/pluginfile.php/1234/mod_resource/content/3/Lecture%201%20-%20Arrays%20%26%20Lists.pdf?forcedownload=1"
        assertEquals("Lecture 1 - Arrays & Lists.pdf", FileNames.fromUrl(url))
        assertEquals("pdf", FileNames.extensionFromUrl(url))
        assertEquals("", FileNames.extensionFromUrl("https://smartlearning.must.edu.eg/mod/resource/view.php?id=9101"))
    }

    @Test
    fun `content disposition variants`() {
        assertEquals("Lecture 1.pdf", FileNames.fromContentDisposition("attachment; filename=\"Lecture 1.pdf\""))
        assertEquals("slides.pptx", FileNames.fromContentDisposition("inline; filename=slides.pptx"))
        assertEquals("مقدمة.pdf", FileNames.fromContentDisposition("attachment; filename*=UTF-8''%D9%85%D9%82%D8%AF%D9%85%D8%A9.pdf"))
        assertNull(FileNames.fromContentDisposition(null))
        assertNull(FileNames.fromContentDisposition("attachment"))
    }

    @Test
    fun `sanitize strips characters file systems reject`() {
        assertEquals("Lecture 1_ Arrays _ Lists.pdf", FileNames.sanitize("Lecture 1: Arrays / Lists.pdf"))
        assertEquals("file", FileNames.sanitize("   "))
        assertTrue(FileNames.sanitize("a".repeat(500)).length <= 120)
        assertEquals("pdf", FileNames.extensionOf("Notes.PDF"))
        assertEquals("", FileNames.extensionOf("README"))
        assertEquals("", FileNames.extensionOf("weird.name.with.spaces here"))
    }

    @Test
    fun `stable ids are negative and repeatable`() {
        val a = LocalFiles.stableId("https://x/pluginfile.php/1/mod_folder/content/0/a.pdf")
        val b = LocalFiles.stableId("https://x/pluginfile.php/1/mod_folder/content/0/b.pdf")
        assertTrue(a < 0 && b < 0)
        assertTrue(a != b)
        assertEquals(a, LocalFiles.stableId("https://x/pluginfile.php/1/mod_folder/content/0/a.pdf"))
    }
}
