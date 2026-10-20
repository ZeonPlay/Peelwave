package dev.zeonplay.peelwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackRulesTest {
    private fun valid(name: String = "sticker.webp") =
        StickerFileFacts(name = name, exists = true, width = 512, height = 512, bytes = 40_000)

    @Test
    fun packCountRequiresAtLeastThreeStickers() {
        assertFalse(PackRules.validateStickerCount(2).isEmpty())
        assertTrue(PackRules.validateStickerCount(3).isEmpty())
    }

    @Test
    fun packCountCannotExceedThirtyStickers() {
        assertTrue(PackRules.validateStickerCount(30).isEmpty())
        assertFalse(PackRules.validateStickerCount(31).isEmpty())
    }

    @Test
    fun validStaticStickerPasses() {
        assertTrue(PackRules.validatePack(listOf(valid(), valid("two.webp"), valid("three.webp"))).isEmpty())
    }

    @Test
    fun invalidDimensionsAreReported() {
        val issues = PackRules.validateFiles(listOf(valid().copy(width = 256)))
        assertEquals(1, issues.size)
        assertTrue(issues.first().contains("512"))
    }

    @Test
    fun nonWebpOutputIsRejected() {
        assertFalse(PackRules.validateFiles(listOf(valid().copy(mimeType = "image/png"))).isEmpty())
    }

    @Test
    fun fileOverOneHundredKbIsRejected() {
        val issues = PackRules.validateFiles(listOf(valid().copy(bytes = 102_401)))
        assertEquals(1, issues.size)
        assertTrue(issues.first().contains("100 KB"))
    }

    @Test
    fun missingFileIsRejected() {
        assertFalse(PackRules.validateFiles(listOf(valid().copy(exists = false))).isEmpty())
    }

    private fun validTrayIcon() = TrayIconFacts(
        exists = true,
        width = 96,
        height = 96,
        bytes = 24_000,
        mimeType = "image/png"
    )

    @Test
    fun validWhatsAppTrayIconPasses() {
        assertTrue(PackRules.validateTrayIcon(validTrayIcon()).isEmpty())
    }

    @Test
    fun trayIconMustBeNinetySixPixels() {
        assertTrue(
            PackRules.validateTrayIcon(validTrayIcon().copy(width = 128))
                .any { it.contains("96 × 96") }
        )
    }

    @Test
    fun trayIconMustBePngAndAtMostFiftyKb() {
        val wrongType = PackRules.validateTrayIcon(validTrayIcon().copy(mimeType = "image/webp"))
        val tooLarge = PackRules.validateTrayIcon(
            validTrayIcon().copy(bytes = PackRules.MAX_TRAY_ICON_BYTES + 1)
        )
        assertTrue(wrongType.any { it.contains("PNG") })
        assertTrue(tooLarge.any { it.contains("50 KB") })
    }

    @Test
    fun missingTrayIconIsRejected() {
        val issues = PackRules.validateTrayIcon(validTrayIcon().copy(exists = false))
        assertFalse(issues.isEmpty())
        assertTrue(issues.first().contains("belum disiapkan"))
    }

    @Test
    fun whatsAppPackLimitIsTen() {
        assertEquals(10, PackRules.MAX_PACKS)
    }
}
