package dev.zeonplay.peelwave

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Imports and exports a complete local Peelwave workspace.
 *
 * Restore is deliberately explicit and replaces the current local workspace only after
 * the archive has been fully extracted and validated. No network access is used.
 */
internal object ProjectBackup {
    private const val MAX_ENTRIES = 5_000
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_METADATA_BYTES = 20L * 1024L * 1024L
    private const val MAX_PACKS = 1_000

    fun export(context: Context, workspace: File, destination: Uri): String? {
        if (!workspace.isDirectory) return "Folder proyek lokal tidak ditemukan."
        val metadata = File(workspace, "packs.json")
        if (!metadata.isFile) return "Metadata proyek tidak ditemukan."
        val workspaceError = validateWorkspace(workspace)
        if (workspaceError != null) return "Backup tidak bisa dibuat: $workspaceError"

        return try {
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: return "Lokasi backup tidak bisa dibuka."

            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                val files = workspace.walkTopDown()
                    .filter { it.isFile }
                    .filter { file ->
                        val relative = file.relativeTo(workspace).invariantSeparatorsPath
                        isWorkspaceFile(relative)
                    }
                    .sortedBy { it.relativeTo(workspace).invariantSeparatorsPath }

                files.forEach { file ->
                    val relative = file.relativeTo(workspace).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    BufferedInputStream(FileInputStream(file)).use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
            null
        } catch (error: Exception) {
            "Backup gagal: " + (error.message ?: "kesalahan file yang tidak diketahui.")
        }
    }

    fun restore(context: Context, workspace: File, archiveUri: Uri): String? {
        val parent = workspace.parentFile ?: return "Folder penyimpanan tidak valid."
        val temporary = File(parent, "peelwave-restore-" + UUID.randomUUID())
        val previous = File(parent, "peelwave-previous-" + UUID.randomUUID())

        return try {
            if (!temporary.mkdirs()) return "Folder sementara untuk pemulihan tidak bisa dibuat."
            val input = context.contentResolver.openInputStream(archiveUri)
                ?: return "File backup tidak bisa dibuka."

            var entryCount = 0
            var totalBytes = 0L
            val seenEntries = mutableSetOf<String>()

            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > MAX_ENTRIES) {
                        return "Backup ditolak: jumlah file terlalu banyak."
                    }
                    if (!isAllowedEntryName(entry.name, entry.isDirectory)) {
                        return "Backup ditolak: struktur file tidak dikenal atau tidak aman."
                    }
                    if (!seenEntries.add(entry.name)) {
                        return "Backup ditolak: ada nama file duplikat."
                    }

                    val outputFile = File(temporary, entry.name)
                    val canonicalRoot = temporary.canonicalPath + File.separator
                    if (!outputFile.canonicalPath.startsWith(canonicalRoot)) {
                        return "Backup ditolak: lokasi file keluar dari folder proyek."
                    }

                    if (entry.isDirectory) {
                        if (!outputFile.isDirectory && !outputFile.mkdirs()) {
                            return "Folder dalam backup tidak bisa dibuat."
                        }
                    } else {
                        if (!outputFile.parentFile.exists() && !outputFile.parentFile.mkdirs()) {
                            return "Folder file backup tidak bisa dibuat."
                        }
                        var entryBytes = 0L
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read.toLong()
                                totalBytes += read.toLong()
                                if (entryBytes > MAX_ENTRY_BYTES ||
                                    totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES
                                ) {
                                    return "Backup ditolak: ukuran hasil ekstraksi melewati batas aman."
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }

            val validationError = validateWorkspace(temporary)
            if (validationError != null) return validationError

            if (workspace.exists() && !workspace.renameTo(previous)) {
                return "Data saat ini tidak bisa diamankan untuk pemulihan."
            }
            if (!temporary.renameTo(workspace)) {
                if (previous.exists()) previous.renameTo(workspace)
                return "Backup tidak bisa dipasang. Data sebelumnya dipertahankan."
            }
            File(workspace, "images").mkdirs()
            File(workspace, "exports").mkdirs()
            previous.deleteRecursively()
            null
        } catch (error: Exception) {
            "Pemulihan gagal: " + (error.message ?: "arsip tidak valid.")
        } finally {
            if (temporary.exists()) temporary.deleteRecursively()
        }
    }

    internal fun isAllowedEntryName(name: String, isDirectory: Boolean = false): Boolean {
        val normalized = if (isDirectory) name.removeSuffix("/") else name
        if (normalized.isBlank() || normalized.startsWith("/") ||
            normalized.contains('\\\\') || normalized.contains('\\u0000')
        ) {
            return false
        }
        if (normalized.split('/').any { it == "." || it == ".." || it.isBlank() }) return false

        if (isDirectory) return normalized == "images" || normalized == "exports"
        if (normalized == "packs.json") return true

        val parts = normalized.split('/')
        if (parts.size != 2 || parts[0] !in setOf("images", "exports")) return false
        return isSafeFileName(parts[1])
    }

    private fun isWorkspaceFile(relativePath: String): Boolean {
        if (relativePath == "packs.json") return true
        val parts = relativePath.split('/')
        return parts.size == 2 &&
            parts[0] in setOf("images", "exports") &&
            isSafeFileName(parts[1])
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            name.length <= 255 &&
            !name.startsWith(".") &&
            '/' !in name &&
            '\\' !in name &&
            '\u0000' !in name &&
            ':' !in name

    private fun validateWorkspace(workspace: File): String? {
        val metadata = File(workspace, "packs.json")
        if (!metadata.isFile || metadata.length() !in 1..MAX_METADATA_BYTES) {
            return "Backup tidak memiliki metadata proyek yang valid."
        }

        return try {
            val root = JSONObject(metadata.readText())
            if (root.optInt("schemaVersion", -1) != 1) {
                return "Versi backup tidak didukung. Data saat ini tidak diubah."
            }
            val packs = root.optJSONArray("packs")
                ?: return "Backup tidak memiliki daftar pack."
            if (packs.length() > MAX_PACKS) return "Backup memiliki terlalu banyak pack."

            for (packIndex in 0 until packs.length()) {
                val pack = packs.optJSONObject(packIndex)
                    ?: return "Data salah satu pack rusak."
                if (pack.optString("name").isBlank()) return "Ada pack tanpa nama."
                val iconValue = pack.opt("iconSourceFile")
                if (iconValue != null && iconValue != JSONObject.NULL) {
                    val iconName = iconValue.toString()
                    if (!isSafeFileName(iconName)) return "Nama file ikon dalam backup tidak aman."
                    val icon = File(workspace, "images/$iconName")
                    if (!icon.isFile || icon.length() !in 1..MAX_ENTRY_BYTES) {
                        return "Backup tidak lengkap: ikon pack tidak ditemukan atau terlalu besar."
                    }
                }
                val assets = pack.optJSONArray("assets")
                    ?: return "Daftar sticker pada salah satu pack rusak."
                if (assets.length() > 30) return "Backup memiliki pack dengan lebih dari 30 item."

                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex)
                        ?: return "Data salah satu sticker rusak."
                    val sourceName = asset.optString("sourceFile")
                    if (!isSafeFileName(sourceName)) return "Nama file sumber dalam backup tidak aman."
                    val source = File(workspace, "images/$sourceName")
                    if (!source.isFile || source.length() !in 1..MAX_ENTRY_BYTES) {
                        return "Backup tidak lengkap: gambar sumber tidak ditemukan atau terlalu besar."
                    }

                    val exportValue = asset.opt("exportFile")
                    if (exportValue != null && exportValue != JSONObject.NULL) {
                        val exportName = exportValue.toString()
                        if (!isSafeFileName(exportName)) return "Nama file hasil ekspor dalam backup tidak aman."
                        val exported = File(workspace, "exports/$exportName")
                        if (!exported.isFile || exported.length() !in 1..MAX_ENTRY_BYTES) {
                            return "Backup tidak lengkap: hasil ekspor sticker tidak ditemukan."
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            "Metadata backup rusak atau tidak bisa dibaca."
        }
    }
}
