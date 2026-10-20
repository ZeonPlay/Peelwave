package dev.zeonplay.peelwave

data class StickerFileFacts(
    val name: String,
    val exists: Boolean,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val mimeType: String = "image/webp"
)

object PackRules {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val MAX_PACKS = 10
    const val CANVAS_SIZE = 512
    const val MAX_STATIC_BYTES = 100 * 1024L
    const val TRAY_ICON_SIZE = 96
    const val MAX_TRAY_ICON_BYTES = 50 * 1024L

    fun validateStickerCount(count: Int): List<String> = buildList {
        if (count < MIN_STICKERS) {
            add("Pack perlu minimal " + MIN_STICKERS + " sticker yang sudah diekspor.")
        }
        if (count > MAX_STICKERS) {
            add("Satu pack maksimal " + MAX_STICKERS + " sticker.")
        }
    }

    fun validateFiles(files: List<StickerFileFacts>): List<String> = buildList {
        files.forEach { file ->
            if (!file.exists) {
                add(file.name + ": file hasil ekspor tidak ditemukan.")
                return@forEach
            }
            if (file.mimeType != "image/webp") {
                add(file.name + ": format hasil ekspor wajib WebP.")
            }
            if (file.width != CANVAS_SIZE || file.height != CANVAS_SIZE) {
                add(file.name + ": ukuran wajib 512 × 512 piksel.")
            }
            if (file.bytes <= 0L || file.bytes > MAX_STATIC_BYTES) {
                add(file.name + ": ukuran file harus di atas 0 dan maksimal 100 KB.")
            }
        }
    }

    fun validatePack(files: List<StickerFileFacts>): List<String> =
        validateStickerCount(files.size) + validateFiles(files)
}
