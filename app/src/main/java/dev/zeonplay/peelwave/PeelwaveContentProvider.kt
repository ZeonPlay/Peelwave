package dev.zeonplay.peelwave

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dynamic provider implementing WhatsApp's public sticker-app contract.
 *
 * Only fully valid static packs are exposed. File access is restricted to a filename
 * explicitly recorded for the requested pack; arbitrary filesystem paths are never served.
 */
class PeelwaveContentProvider : ContentProvider() {
    private lateinit var uriMatcher: UriMatcher

    private data class StickerRecord(
        val fileName: String,
        val emoji: String = "💬",
        val accessibilityText: String
    )

    private data class PackRecord(
        val id: String,
        val name: String,
        val publisher: String,
        val trayIconFile: String,
        val stickers: List<StickerRecord>
    )

    override fun onCreate(): Boolean {
        val authority = context?.packageName?.plus(".stickercontentprovider")
            ?: return false
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "metadata", METADATA)
            addURI(authority, "metadata/*", SINGLE_PACK_METADATA)
            addURI(authority, "stickers/*", PACK_STICKERS)
            addURI(authority, "stickers_asset/*/*", STICKER_ASSET)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = when (uriMatcher.match(uri)) {
            METADATA -> packMetadataCursor(uri, readyPacks())
            SINGLE_PACK_METADATA -> {
                val id = uri.lastPathSegment.orEmpty()
                packMetadataCursor(uri, readyPacks().filter { it.id == id })
            }
            PACK_STICKERS -> {
                val id = uri.lastPathSegment.orEmpty()
                stickerCursor(uri, readyPacks().filter { it.id == id }.firstOrNull())
            }
            else -> throw IllegalArgumentException("Unknown sticker provider URI: $uri")
        }
        return result
    }

    override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
        SINGLE_PACK_METADATA -> "vnd.android.cursor.item/vnd.$authority.metadata"
        PACK_STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
        STICKER_ASSET -> {
            val packId = uri.pathSegments.getOrNull(1).orEmpty()
            val fileName = uri.pathSegments.getOrNull(2).orEmpty()
            val pack = readyPacks().firstOrNull { it.id == packId }
            if (pack != null && pack.trayIconFile == fileName) "image/png" else "image/webp"
        }
        else -> throw IllegalArgumentException("Unknown sticker provider URI: $uri")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uriMatcher.match(uri) != STICKER_ASSET) {
            throw FileNotFoundException("Only registered sticker assets can be opened read-only.")
        }
        val packId = uri.pathSegments.getOrNull(1)
            ?: throw FileNotFoundException("Missing pack ID.")
        val fileName = uri.pathSegments.getOrNull(2)
            ?: throw FileNotFoundException("Missing sticker filename.")
        val pack = readyPacks().firstOrNull { it.id == packId }
            ?: throw FileNotFoundException("Sticker pack is missing or invalid.")

        val isTrayIcon = pack.trayIconFile == fileName
        val stickerBelongsToPack = pack.stickers.any { it.fileName == fileName }
        if (!isTrayIcon && !stickerBelongsToPack) {
            throw FileNotFoundException("The requested asset does not belong to this sticker pack.")
        }
        val root = File(requireNotNull(context).filesDir, "peelwave/exports")
        val file = File(root, fileName)
        if (!file.isFile || file.parentFile?.canonicalPath != root.canonicalPath) {
            throw FileNotFoundException("Sticker file is unavailable.")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only sticker provider.")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only sticker provider.")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only sticker provider.")

    private val authority: String
        get() = requireNotNull(context).packageName + ".stickercontentprovider"

    private fun packMetadataCursor(uri: Uri, packs: List<PackRecord>): Cursor {
        val columns = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack"
        )
        val cursor = MatrixCursor(columns)
        packs.forEach { pack ->
            cursor.addRow(
                arrayOf<Any?>(
                    pack.id,
                    pack.name,
                    pack.publisher,
                    pack.trayIconFile,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "1",
                    0,
                    0
                )
            )
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, uri)
        return cursor
    }

    private fun stickerCursor(uri: Uri, pack: PackRecord?): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                "sticker_file_name",
                "sticker_emoji",
                "sticker_accessibility_text"
            )
        )
        pack?.stickers?.forEach { sticker ->
            cursor.addRow(arrayOf(sticker.fileName, sticker.emoji, sticker.accessibilityText))
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, uri)
        return cursor
    }

    private fun readyPacks(): List<PackRecord> {
        val appContext = context ?: return emptyList()
        val root = File(appContext.filesDir, "peelwave")
        val metadata = File(root, "packs.json")
        if (!metadata.isFile || metadata.length() !in 1..20L * 1024L * 1024L) return emptyList()

        return try {
            val packsArray = JSONObject(metadata.readText()).optJSONArray("packs") ?: JSONArray()
            val exports = File(root, "exports")
            buildList {
                for (packIndex in 0 until packsArray.length()) {
                    val packJson = packsArray.optJSONObject(packIndex) ?: continue
                    val id = packJson.optString("id")
                    val name = packJson.optString("name").trim()
                    val author = packJson.optString("author").trim().ifBlank { "Peelwave creator" }
                    val trayIconFile = packJson.optString("trayIconFile")
                    if (id.isBlank() || name.isBlank() || name.length > 128 ||
                        author.length > 128 || !isSafeBaseName(trayIconFile)
                    ) continue

                    val iconFile = File(exports, trayIconFile)
                    if (!isValidImage(iconFile, 96, 96, MAX_TRAY_ICON_BYTES, "image/png")) continue

                    val assets = packJson.optJSONArray("assets") ?: continue
                    if (assets.length() !in 3..30) continue
                    val stickerRecords = mutableListOf<StickerRecord>()
                    var validPack = true
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.optJSONObject(assetIndex) ?: run {
                            validPack = false
                            break
                        }
                        val fileName = asset.optString("exportFile")
                        if (!isSafeBaseName(fileName)) {
                            validPack = false
                            break
                        }
                        val stickerFile = File(exports, fileName)
                        if (!isValidImage(stickerFile, 512, 512, MAX_STICKER_BYTES, "image/webp")) {
                            validPack = false
                            break
                        }
                        stickerRecords += StickerRecord(
                            fileName = fileName,
                            accessibilityText = "Sticker " + (assetIndex + 1) + " dari " + name
                        )
                    }
                    if (validPack && stickerRecords.size == assets.length()) {
                        add(PackRecord(id, name, author, trayIconFile, stickerRecords))
                    }
                }
            }.take(MAX_PACKS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isValidImage(
        file: File,
        requiredWidth: Int,
        requiredHeight: Int,
        maxBytes: Long,
        requiredMimeType: String
    ): Boolean {
        if (!file.isFile || file.length() !in 1..maxBytes) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth == requiredWidth &&
            options.outHeight == requiredHeight &&
            options.outMimeType == requiredMimeType
    }

    private fun isSafeBaseName(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= 255 &&
            value != "." &&
            value != ".." &&
            !value.startsWith(".") &&
            '/' !in value &&
            '\\' !in value &&
            ':' !in value

    companion object {
        private const val METADATA = 1
        private const val SINGLE_PACK_METADATA = 2
        private const val PACK_STICKERS = 3
        private const val STICKER_ASSET = 4
        private const val MAX_PACKS = 10
        private const val MAX_STICKER_BYTES = 100 * 1024L
        private const val MAX_TRAY_ICON_BYTES = 50 * 1024L
    }
}
