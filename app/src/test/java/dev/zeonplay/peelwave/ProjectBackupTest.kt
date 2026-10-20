package dev.zeonplay.peelwave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBackupTest {
    @Test
    fun acceptsOnlyWorkspaceFiles() {
        assertTrue(ProjectBackup.isAllowedEntryName("packs.json"))
        assertTrue(ProjectBackup.isAllowedEntryName("images/source-photo.webp"))
        assertTrue(ProjectBackup.isAllowedEntryName("exports/sticker.webp"))
        assertTrue(ProjectBackup.isAllowedEntryName("images/", isDirectory = true))
        assertTrue(ProjectBackup.isAllowedEntryName("exports/", isDirectory = true))
    }

    @Test
    fun rejectsPathsThatCouldEscapeTheWorkspace() {
        assertFalse(ProjectBackup.isAllowedEntryName("../packs.json"))
        assertFalse(ProjectBackup.isAllowedEntryName("images/../../packs.json"))
        assertFalse(ProjectBackup.isAllowedEntryName("/packs.json"))
        assertFalse(ProjectBackup.isAllowedEntryName("images\\..\\packs.json"))
        assertFalse(ProjectBackup.isAllowedEntryName("C:/packs.json"))
    }

    @Test
    fun rejectsUnexpectedFilesAndNestedDirectories() {
        assertFalse(ProjectBackup.isAllowedEntryName("metadata.json"))
        assertFalse(ProjectBackup.isAllowedEntryName("images/nested/source.png"))
        assertFalse(ProjectBackup.isAllowedEntryName("images/", isDirectory = false))
        assertFalse(ProjectBackup.isAllowedEntryName("temporary/", isDirectory = true))
    }
}
