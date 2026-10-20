package dev.zeonplay.peelwave

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF11130F)
private val SurfaceInk = Color(0xFF1B1E19)
private val RaisedInk = Color(0xFF242820)
private val Accent = Color(0xFFD8F887)
private val Foreground = Color(0xFFF2F4ED)
private val Muted = Color(0xFFA5AC9D)
private val Hairline = Color(0xFF34392F)

private data class StickerLayer(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.78f,
    val sizeSp: Float = 28f,
    val colorHex: String = "#FFFFFF",
    val outlineWidth: Float = 3f
)

private data class StickerAsset(
    val id: String = UUID.randomUUID().toString(),
    val sourceFile: String,
    val exportFile: String? = null,
    val layers: List<StickerLayer> = emptyList(),
    val cropToFill: Boolean = false
)

private data class StickerPack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String,
    val assets: List<StickerAsset> = emptyList(),
    val iconSourceFile: String? = null,
    val trayIconFile: String? = null
)

private data class RenderOutcome(val fileName: String? = null, val error: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(17, 19, 15)
        window.navigationBarColor = android.graphics.Color.rgb(17, 19, 15)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Ink,
                    secondary = Accent,
                    background = Ink,
                    onBackground = Foreground,
                    surface = SurfaceInk,
                    onSurface = Foreground,
                    surfaceVariant = RaisedInk,
                    onSurfaceVariant = Muted,
                    outline = Hairline
                )
            ) {
                PeelwaveApp()
            }
        }
    }
}

@Composable
private fun PeelwaveApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { LocalPackStore(context) }
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf(store.load()) }
    var currentScreen by remember { mutableStateOf("home") }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var selectedAssetId by remember { mutableStateOf<String?>(null) }
    var showCreatePack by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val selectedPack = packs.firstOrNull { it.id == selectedPackId }
    val selectedAsset = selectedPack?.assets?.firstOrNull { it.id == selectedAssetId }

    BackHandler(enabled = currentScreen == "pack") {
        currentScreen = "home"
        selectedPackId = null
    }

    fun savePacks(updated: List<StickerPack>) {
        store.save(updated)
        packs = updated
    }

    fun updateAsset(updatedAsset: StickerAsset) {
        val packId = selectedPackId ?: return
        savePacks(packs.map { pack ->
            if (pack.id == packId) {
                pack.copy(assets = pack.assets.map { old ->
                    if (old.id == updatedAsset.id) updatedAsset else old
                })
            } else pack
        })
    }

    val backupExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val error = withContext(Dispatchers.IO) { store.exportBackup(uri) }
                statusMessage = if (error == null) {
                    "Backup berhasil diekspor."
                } else {
                    "Ekspor backup gagal: $error"
                }
            }
        }
    }
    val backupImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) pendingRestoreUri = uri
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val targetPackId = selectedPackId
        if (uri != null && targetPackId != null) {
            scope.launch {
                val copiedName = withContext(Dispatchers.IO) { store.importImage(uri) }
                if (copiedName == null) {
                    statusMessage = "Ikon tidak bisa dibuka. Coba gambar PNG, JPEG, atau WebP."
                } else {
                    val trayIcon = withContext(Dispatchers.IO) {
                        store.renderTrayIcon(copiedName, targetPackId)
                    }
                    val oldPack = packs.firstOrNull { it.id == targetPackId }
                    if (trayIcon == null || oldPack == null) {
                        withContext(Dispatchers.IO) {
                            store.sourceFile(copiedName).delete()
                            trayIcon?.let { store.deleteTrayIcon(it) }
                        }
                        statusMessage = "Ikon tidak bisa diproses. Pilih gambar lain."
                    } else {
                        savePacks(packs.map { pack ->
                            if (pack.id == targetPackId) {
                                pack.copy(iconSourceFile = copiedName, trayIconFile = trayIcon)
                            } else pack
                        })
                        if (oldPack.iconSourceFile != null && oldPack.iconSourceFile != copiedName) {
                            withContext(Dispatchers.IO) {
                                store.sourceFile(oldPack.iconSourceFile).delete()
                            }
                        }
                        statusMessage = "Ikon pack disimpan dan disiapkan untuk WhatsApp."
                    }
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val targetPackId = selectedPackId
        if (uri != null && targetPackId != null) {
            scope.launch {
                val copiedName = withContext(Dispatchers.IO) { store.importImage(uri) }
                if (copiedName == null) {
                    statusMessage = "Gambar tidak bisa dibuka. Coba format PNG, JPEG, atau WebP."
                } else {
                    val addedAsset = StickerAsset(sourceFile = copiedName)
                    val updated = packs.map { pack ->
                        if (pack.id == targetPackId && pack.assets.size < PackRules.MAX_STICKERS) {
                            pack.copy(assets = pack.assets + addedAsset)
                        } else pack
                    }
                    if (updated.firstOrNull { it.id == targetPackId }?.assets?.any { it.id == addedAsset.id } == true) {
                        savePacks(updated)
                        selectedAssetId = addedAsset.id
                        currentScreen = "editor"
                    } else {
                        store.sourceFile(copiedName).delete()
                        statusMessage = "Pack sudah berisi 30 item. Buat pack lain untuk gambar berikutnya."
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("pw", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("peelwave", color = Foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("offline sticker studio", color = Muted, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceInk)
                    .border(1.dp, Hairline, CircleShape)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text("LOCAL", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            currentScreen == "home" -> HomeScreen(
                packs = packs,
                store = store,
                onCreatePack = {
                    if (packs.size >= PackRules.MAX_PACKS) {
                        statusMessage = "WhatsApp mendukung maksimal 10 pack per aplikasi. Ekspor backup lalu hapus pack yang memang tidak lagi diperlukan."
                    } else {
                        showCreatePack = true
                    }
                },
                onSelectPack = { pack ->
                    selectedPackId = pack.id
                    currentScreen = "pack"
                },
                onExportBackup = { backupExporter.launch("Peelwave-backup.zip") },
                onImportBackup = {
                    backupImporter.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
            )
            currentScreen == "pack" && selectedPack != null -> PackScreen(
                pack = selectedPack,
                store = store,
                onBack = { currentScreen = "home"; selectedPackId = null },
                onSetIcon = {
                    iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImport = {
                    if (selectedPack.assets.size >= PackRules.MAX_STICKERS) {
                        statusMessage = "Pack sudah mencapai batas 30 item."
                    } else {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
                onSelectAsset = { asset ->
                    selectedAssetId = asset.id
                    currentScreen = "editor"
                },
                onRename = { newName, newAuthor ->
                    savePacks(packs.map {
                        if (it.id == selectedPack.id) it.copy(name = newName, author = newAuthor) else it
                    })
                    statusMessage = "Info pack disimpan."
                },
                onDelete = {
                    store.deletePackFiles(selectedPack)
                    savePacks(packs.filterNot { it.id == selectedPack.id })
                    selectedPackId = null
                    currentScreen = "home"
                    statusMessage = "Pack dihapus."
                },
                onValidate = {
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { store.validatePack(selectedPack) }
                        statusMessage = if (report.isEmpty()) {
                            "Pack lolos validasi sticker dan ikon."
                        } else report.joinToString(" • ")
                    }
                },
                onAddToWhatsApp = {
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { store.validatePack(selectedPack) }
                        if (report.isNotEmpty()) {
                            statusMessage = report.joinToString(" • ")
                        } else {
                            val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
                                putExtra("sticker_pack_id", selectedPack.id)
                                putExtra("sticker_pack_authority", context.packageName + ".stickercontentprovider")
                                putExtra("sticker_pack_name", selectedPack.name)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                statusMessage = "WhatsApp tidak ditemukan atau versi yang terpasang belum mendukung penambahan pack."
                            }
                        }
                    }
                }
            )
            currentScreen == "editor" && selectedPack != null && selectedAsset != null -> key(selectedAsset.id) {
                EditorScreen(
                    asset = selectedAsset,
                    store = store,
                    onBack = {
                        updateAsset(it)
                        currentScreen = "pack"
                    },
                    onSaved = {
                        updateAsset(it)
                        statusMessage = "Sticker dirender ke WebP dan disimpan lokal."
                    },
                    onMessage = { statusMessage = it }
                )
            }
            else -> HomeScreen(
                packs = packs,
                store = store,
                onCreatePack = {
                    if (packs.size >= PackRules.MAX_PACKS) {
                        statusMessage = "WhatsApp mendukung maksimal 10 pack per aplikasi. Ekspor backup lalu hapus pack yang memang tidak lagi diperlukan."
                    } else {
                        showCreatePack = true
                    }
                },
                onSelectPack = { pack -> selectedPackId = pack.id; currentScreen = "pack" },
                onExportBackup = { backupExporter.launch("Peelwave-backup.zip") },
                onImportBackup = {
                    backupImporter.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }
            )
        }
        }

        if (statusMessage != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RaisedInk)
                    .padding(start = 13.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    statusMessage.orEmpty(),
                    color = Foreground,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { statusMessage = null }) {
                    Text("OK", color = Accent)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (showCreatePack) {
        CreatePackDialog(
            onDismiss = { showCreatePack = false },
            onCreate = { name, author ->
                val pack = StickerPack(name = name, author = author)
                savePacks(packs + pack)
                selectedPackId = pack.id
                showCreatePack = false
                currentScreen = "pack"
            }
        )
    }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            containerColor = SurfaceInk,
            title = { Text("Pulihkan backup?", color = Foreground) },
            text = {
                Text(
                    "Pemulihan akan mengganti semua pack dan file lokal saat ini dengan isi backup. Pastikan kamu sudah mengekspor backup terbaru jika masih membutuhkannya.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val restoreUri = pendingRestoreUri
                    pendingRestoreUri = null
                    if (restoreUri != null) {
                        scope.launch {
                            val (error, restoredPacks) = withContext(Dispatchers.IO) {
                                val restoreError = store.restoreBackup(restoreUri)
                                restoreError to if (restoreError == null) store.load() else emptyList()
                            }
                            if (error == null) {
                                packs = restoredPacks
                                selectedPackId = null
                                selectedAssetId = null
                                currentScreen = "home"
                                statusMessage = "Backup dipulihkan. Data lokal sebelumnya telah diganti."
                            } else {
                                statusMessage = error
                            }
                        }
                    }
                }) { Text("Pulihkan", color = Color(0xFFFF9B91)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("Batal", color = Muted)
                }
            }
        )
    }

}

@Composable
private fun HomeScreen(
    packs: List<StickerPack>,
    store: LocalPackStore,
    onCreatePack: () -> Unit,
    onSelectPack: (StickerPack) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(13.dp))
        Text("Bikin sticker.", color = Foreground, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Text("Dengan cara kamu.", color = Muted, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(13.dp))
        Text(
            "Foto masuk. Ide jadi sticker. Semua proyek tetap di perangkatmu.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onCreatePack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
        ) {
            Text("＋   Buat pack baru", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExportBackup,
                modifier = Modifier.weight(1f).height(43.dp),
                border = BorderStroke(1.dp, Hairline)
            ) {
                Text("Ekspor backup", color = Foreground, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onImportBackup,
                modifier = Modifier.weight(1f).height(43.dp),
                border = BorderStroke(1.dp, Hairline)
            ) {
                Text("Pulihkan backup", color = Foreground, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PACK KAMU", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.weight(1f))
            Text(packs.size.toString() + " pack", color = Muted, fontSize = 12.sp)
        }

        if (packs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                    .background(SurfaceInk)
                    .padding(23.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✳", color = Accent, fontSize = 35.sp)
                    Spacer(Modifier.height(11.dp))
                    Text("Belum ada pack", color = Foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mulai dengan membuat pack, lalu masukkan gambar dari galeri.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(packs, key = { it.id }) { pack ->
                    val ready = pack.assets.count { it.exportFile != null }
                    Card(
                        onClick = { onSelectPack(pack) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceInk),
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(RaisedInk),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = pack.iconSourceFile?.let { store.sourceFile(it) }
                                val cover = pack.assets.firstOrNull()
                                if (icon != null && icon.isFile) {
                                    AsyncImage(
                                        model = icon,
                                        contentDescription = "Ikon " + pack.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (cover != null) {
                                    AsyncImage(
                                        model = store.sourceFile(cover.sourceFile),
                                        contentDescription = "Pratinjau " + pack.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("✳", color = Accent, fontSize = 25.sp)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pack.name, color = Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(pack.author.ifBlank { "Tanpa nama pembuat" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(7.dp))
                                Text(ready.toString() + "/30 sticker siap · " + (pack.assets.size - ready) + " draft", color = Muted, fontSize = 11.sp)
                            }
                            Text("›", color = Accent, fontSize = 25.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePackDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceInk,
        title = { Text("Pack baru", color = Foreground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nama paket") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it.take(40) },
                    label = { Text("Nama pembuat (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), author.trim()) }
            ) { Text("Buat pack", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = Muted) }
        }
    )
}

@Composable
private fun PackScreen(
    pack: StickerPack,
    store: LocalPackStore,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onSetIcon: () -> Unit,
    onSelectAsset: (StickerAsset) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: () -> Unit,
    onValidate: () -> Unit,
    onAddToWhatsApp: () -> Unit
) {
    var showPackInfo by remember(pack.id) { mutableStateOf(false) }
    var showDelete by remember(pack.id) { mutableStateOf(false) }
    val ready = pack.assets.count { it.exportFile != null }

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("←  Semua pack", color = Muted) }
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.name, color = Foreground, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(pack.author.ifBlank { "Nama pembuat belum diisi" }, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = { showPackInfo = true }) { Text("Edit info", color = Accent) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(ready.toString() + " / 30 siap")
            StatPill((pack.assets.size - ready).toString() + " draft")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSetIcon,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            border = BorderStroke(1.dp, Hairline)
        ) {
            Text(
                if (pack.iconSourceFile == null) "Pilih ikon pack" else "Ganti ikon pack",
                color = Foreground,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
        ) {
            Text("＋   Tambahkan gambar", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onValidate,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Hairline)
        ) {
            Text("Periksa kesiapan pack", color = Foreground)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onAddToWhatsApp,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
        ) {
            Text("Tambahkan ke WhatsApp", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Pack harus berisi 3–30 sticker valid dan memiliki ikon 96 × 96. WhatsApp akan meminta konfirmasi sebelum menambahkannya.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 23.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ISI PACK", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.weight(1f))
            Text(pack.assets.size.toString() + " / 30 item", color = Muted, fontSize = 12.sp)
        }

        if (pack.assets.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(SurfaceInk),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada gambar di pack ini.", color = Muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(pack.assets, key = { it.id }) { asset ->
                    Card(
                        onClick = { onSelectAsset(asset) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceInk),
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = store.sourceFile(asset.sourceFile),
                                contentDescription = "Gambar sumber sticker",
                                modifier = Modifier.size(67.dp).clip(RoundedCornerShape(12.dp)).background(RaisedInk),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (asset.exportFile != null) "Sticker tersimpan" else "Draft gambar",
                                    color = Foreground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (asset.exportFile != null) "WebP · 512 × 512" else "Belum dirender",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                            }
                            Text("Edit  ›", color = Accent, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Hapus pack", color = Color(0xFFFF9B91))
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    if (showPackInfo) {
        var name by remember(pack.id) { mutableStateOf(pack.name) }
        var author by remember(pack.id) { mutableStateOf(pack.author) }
        AlertDialog(
            onDismissRequest = { showPackInfo = false },
            containerColor = SurfaceInk,
            title = { Text("Info pack", color = Foreground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Nama paket") }, singleLine = true)
                    OutlinedTextField(value = author, onValueChange = { author = it.take(40) }, label = { Text("Nama pembuat") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onRename(name.trim(), author.trim()); showPackInfo = false }
                ) { Text("Simpan", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showPackInfo = false }) { Text("Batal", color = Muted) } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = SurfaceInk,
            title = { Text("Hapus pack?", color = Foreground) },
            text = { Text("Semua draft dan hasil ekspor lokal dalam pack ini akan dihapus. Tindakan ini tidak bisa dibatalkan.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) {
                    Text("Hapus", color = Color(0xFFFF9B91))
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal", color = Muted) } }
        )
    }
}

@Composable
private fun StatPill(label: String) {
    Box(
        modifier = Modifier.clip(CircleShape).background(SurfaceInk).border(1.dp, Hairline, CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun EditorScreen(
    asset: StickerAsset,
    store: LocalPackStore,
    onBack: (StickerAsset) -> Unit,
    onSaved: (StickerAsset) -> Unit,
    onMessage: (String) -> Unit
) {
    var history by remember(asset.id) { mutableStateOf(listOf(asset)) }
    var historyIndex by remember(asset.id) { mutableStateOf(0) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showEmojiDialog by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var outlineWidth by remember(asset.id) { mutableStateOf(asset.layers.firstOrNull { it.kind == "text" }?.outlineWidth ?: 3f) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = history.getOrElse(historyIndex) { asset }

    BackHandler { onBack(current) }

    fun push(next: StickerAsset) {
        val trimmed = history.take(historyIndex + 1)
        history = (trimmed + next.copy(exportFile = null)).takeLast(30)
        historyIndex = history.lastIndex
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onBack(current) }) { Text("←  Kembali", color = Muted) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (historyIndex > 0) historyIndex -= 1 }) {
                Text("↶", color = if (historyIndex > 0) Accent else Hairline, fontSize = 24.sp)
            }
            TextButton(onClick = { if (historyIndex < history.lastIndex) historyIndex += 1 }) {
                Text("↷", color = if (historyIndex < history.lastIndex) Accent else Hairline, fontSize = 24.sp)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF292D26))
                .border(1.dp, Hairline, RoundedCornerShape(22.dp))
        ) {
            AsyncImage(
                model = store.sourceFile(current.sourceFile),
                contentDescription = "Canvas sticker",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (current.cropToFill) ContentScale.Crop else ContentScale.Fit
            )
            current.layers.forEach { layer ->
                val xOffset = maxWidth * (layer.x - 0.5f)
                val yOffset = maxHeight * (layer.y - 0.5f)
                val color = try { Color(android.graphics.Color.parseColor(layer.colorHex)) } catch (_: Exception) { Foreground }
                if (layer.kind == "text" && layer.outlineWidth > 0f) {
                    Text(
                        text = layer.text,
                        modifier = Modifier.align(Alignment.Center).offset(x = xOffset, y = yOffset),
                        color = Color.Black,
                        fontSize = layer.sizeSp.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(drawStyle = Stroke(width = layer.outlineWidth * 2.5f))
                    )
                }
                Text(
                    text = layer.text,
                    modifier = Modifier.align(Alignment.Center).offset(x = xOffset, y = yOffset),
                    color = color,
                    fontSize = layer.sizeSp.sp,
                    fontWeight = if (layer.kind == "text") FontWeight.Bold else FontWeight.Normal
                )
            }
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(Color(0xAA11130F)).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(if (current.cropToFill) "Crop" else "Fit", color = Foreground, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("EDIT STICKER", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(9.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolButton("Crop", "Potong / fit", Modifier.weight(1f)) {
                push(current.copy(cropToFill = !current.cropToFill))
            }
            ToolButton("T", "Teks", Modifier.weight(1f)) {
                newText = ""
                showTextDialog = true
            }
            ToolButton("☺", "Emoji", Modifier.weight(1f)) { showEmojiDialog = true }
            ToolButton("▤", "Layer", Modifier.weight(1f)) { showLayersDialog = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ToolButton("◌", "Hapus latar", Modifier.weight(1f)) {
                onMessage("Hapus latar offline belum diimplementasikan. Tombol ini tidak mengubah gambar.")
            }
            ToolButton("◎", "Outline teks", Modifier.weight(1f)) {
                val nextWidth = if (outlineWidth > 0f) 0f else 3f
                outlineWidth = nextWidth
                push(current.copy(layers = current.layers.map {
                    if (it.kind == "text") it.copy(outlineWidth = nextWidth) else it
                }))
            }
            ToolButton("↶", "Undo", Modifier.weight(1f)) { if (historyIndex > 0) historyIndex -= 1 }
            ToolButton("↷", "Redo", Modifier.weight(1f)) { if (historyIndex < history.lastIndex) historyIndex += 1 }
        }

        if (current.layers.any { it.kind == "text" }) {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Outline teks", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.width(9.dp))
                Slider(
                    value = outlineWidth,
                    onValueChange = { outlineWidth = it },
                    onValueChangeFinished = {
                        push(current.copy(layers = current.layers.map {
                            if (it.kind == "text") it.copy(outlineWidth = outlineWidth) else it
                        }))
                    },
                    valueRange = 0f..8f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(13.dp))
        Button(
            onClick = {
                if (saving) return@Button
                saving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { store.render(current) }
                    saving = false
                    if (result.fileName != null) {
                        val saved = current.copy(exportFile = result.fileName)
                        history = history.take(historyIndex + 1) + saved
                        historyIndex = history.lastIndex
                        onSaved(saved)
                    } else {
                        onMessage(result.error ?: "Sticker gagal disimpan.")
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
        ) {
            Text(if (saving) "Menyimpan…" else "Simpan sticker", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = SurfaceInk,
            title = { Text("Tambah teks", color = Foreground) },
            text = {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it.take(100) },
                    label = { Text("Teks sticker") },
                    supportingText = { Text("Teks ditambahkan sebagai layer yang bisa di-undo.") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newText.isNotBlank(),
                    onClick = {
                        push(current.copy(layers = current.layers + StickerLayer(kind = "text", text = newText.trim(), y = 0.76f, outlineWidth = outlineWidth)))
                        showTextDialog = false
                    }
                ) { Text("Tambah", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Batal", color = Muted) } }
        )
    }

    if (showEmojiDialog) {
        val emojis = listOf("😀", "😂", "😭", "🥹", "😍", "😎", "🤨", "😡", "💀", "🔥", "✨", "❤️", "👍", "🙏", "🎉", "🫠")
        AlertDialog(
            onDismissRequest = { showEmojiDialog = false },
            containerColor = SurfaceInk,
            title = { Text("Pilih emoji", color = Foreground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojis.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { emoji ->
                                TextButton(onClick = {
                                    push(current.copy(layers = current.layers + StickerLayer(kind = "emoji", text = emoji, y = 0.76f, sizeSp = 44f)))
                                    showEmojiDialog = false
                                }) { Text(emoji, fontSize = 24.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEmojiDialog = false }) { Text("Tutup", color = Muted) } }
        )
    }

    if (showLayersDialog) {
        AlertDialog(
            onDismissRequest = { showLayersDialog = false },
            containerColor = SurfaceInk,
            title = { Text("Layer", color = Foreground) },
            text = {
                if (current.layers.isEmpty()) {
                    Text("Belum ada layer teks atau emoji.", color = Muted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(current.layers, key = { it.id }) { layer ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RaisedInk).padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(layer.text, color = Foreground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = {
                                    val index = current.layers.indexOfFirst { it.id == layer.id }
                                    if (index > 0) {
                                        val reordered = current.layers.toMutableList()
                                        val item = reordered.removeAt(index)
                                        reordered.add(index - 1, item)
                                        push(current.copy(layers = reordered))
                                    }
                                }) { Text("↑", color = Accent) }
                                TextButton(onClick = {
                                    val index = current.layers.indexOfFirst { it.id == layer.id }
                                    if (index >= 0 && index < current.layers.lastIndex) {
                                        val reordered = current.layers.toMutableList()
                                        val item = reordered.removeAt(index)
                                        reordered.add(index + 1, item)
                                        push(current.copy(layers = reordered))
                                    }
                                }) { Text("↓", color = Accent) }
                                TextButton(onClick = {
                                    push(current.copy(layers = current.layers.filterNot { it.id == layer.id }))
                                }) { Text("×", color = Color(0xFFFF9B91)) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLayersDialog = false }) { Text("Selesai", color = Accent) } }
        )
    }
}

@Composable
private fun ToolButton(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceInk)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Foreground, fontSize = 10.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private class LocalPackStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "peelwave").apply { mkdirs() }
    private val images = File(root, "images").apply { mkdirs() }
    private val exports = File(root, "exports").apply { mkdirs() }
    private val metadata = File(root, "packs.json")

    fun exportBackup(uri: Uri): String? = ProjectBackup.export(appContext, root, uri)

    fun restoreBackup(uri: Uri): String? = ProjectBackup.restore(appContext, root, uri)

    fun load(): List<StickerPack> {
        if (!metadata.exists()) return emptyList()
        return try {
            val rootJson = JSONObject(metadata.readText())
            val array = rootJson.optJSONArray("packs") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    add(packFromJson(array.getJSONObject(index)))
                }
            }
        } catch (_: Exception) {
            val recovery = File(root, "packs-recovery-" + System.currentTimeMillis() + ".json")
            metadata.copyTo(recovery, overwrite = true)
            emptyList()
        }
    }

    fun save(packs: List<StickerPack>) {
        val rootJson = JSONObject()
        rootJson.put("schemaVersion", 1)
        rootJson.put("packs", JSONArray().apply { packs.forEach { put(packToJson(it)) } })
        val temporary = File(root, "packs.json.tmp")
        temporary.writeText(rootJson.toString())
        if (!temporary.renameTo(metadata)) {
            temporary.copyTo(metadata, overwrite = true)
            temporary.delete()
        }
    }

    fun importImage(uri: Uri): String? {
        return try {
            val extension = when (appContext.contentResolver.getType(uri)?.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/heic", "image/heif" -> "heic"
                else -> "img"
            }
            val destination = File(images, UUID.randomUUID().toString() + "." + extension)
            val input = appContext.contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read.toLong()
                        if (total > 60L * 1024L * 1024L) {
                            destination.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (destination.length() <= 0L) {
                destination.delete()
                null
            } else destination.name
        } catch (_: Exception) {
            null
        }
    }

    fun sourceFile(name: String): File = File(images, name)
    private fun exportedFile(name: String): File = File(exports, name)

    fun deletePackFiles(pack: StickerPack) {
        pack.assets.forEach { asset ->
            sourceFile(asset.sourceFile).delete()
            asset.exportFile?.let { exportedFile(it).delete() }
        }
        pack.iconSourceFile?.let { sourceFile(it).delete() }
        pack.trayIconFile?.let { exportedFile(it).delete() }
    }

    fun deleteTrayIcon(name: String) {
        exportedFile(name).delete()
    }

    fun renderTrayIcon(sourceName: String, packId: String): String? {
        val source = sourceFile(sourceName)
        if (!source.isFile) return null
        val bitmap = decodeOriented(source) ?: return null
        val size = PackRules.TRAY_ICON_SIZE
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val outputName = "tray-" + packId + ".png"
        val outputFile = exportedFile(outputName)
        return try {
            val canvas = Canvas(output)
            val scale = max(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = (size - width) / 2f
            val top = (size - height) / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
            FileOutputStream(outputFile).use { stream ->
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    outputFile.delete()
                    return null
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outputFile.absolutePath, options)
            if (options.outWidth != size || options.outHeight != size ||
                options.outMimeType != "image/png" ||
                outputFile.length() !in 1L..PackRules.MAX_TRAY_ICON_BYTES
            ) {
                outputFile.delete()
                null
            } else outputName
        } catch (_: Exception) {
            outputFile.delete()
            null
        } finally {
            bitmap.recycle()
            output.recycle()
        }
    }

    fun validatePack(pack: StickerPack): List<String> {
        val facts = pack.assets.filter { it.exportFile != null }.map { asset ->
            val file = exportedFile(asset.exportFile.orEmpty())
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath, options)
            StickerFileFacts(
                name = file.name,
                exists = file.exists(),
                width = if (file.exists()) options.outWidth else 0,
                height = if (file.exists()) options.outHeight else 0,
                bytes = if (file.exists()) file.length() else 0L,
                mimeType = options.outMimeType ?: ""
            )
        }
        val issues = PackRules.validatePack(facts).toMutableList()
        val draftCount = pack.assets.count { it.exportFile == null }
        if (draftCount > 0) {
            issues += "$draftCount sticker masih berupa draft. Selesaikan atau hapus draft sebelum menambahkan pack ke WhatsApp."
        }
        val trayName = pack.trayIconFile
        val trayFile = trayName?.let { exportedFile(it) }
        val trayOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (trayFile != null && trayFile.isFile) {
            BitmapFactory.decodeFile(trayFile.absolutePath, trayOptions)
        }
        if (trayFile == null || !trayFile.isFile) {
            issues += "Ikon pack belum disiapkan. Pilih ikon pack terlebih dahulu."
        } else {
            if (trayOptions.outWidth != PackRules.TRAY_ICON_SIZE ||
                trayOptions.outHeight != PackRules.TRAY_ICON_SIZE
            ) issues += "Ikon WhatsApp harus berukuran 96 × 96 piksel."
            if (trayOptions.outMimeType != "image/png") issues += "Ikon WhatsApp harus berformat PNG."
            if (trayFile.length() > PackRules.MAX_TRAY_ICON_BYTES) issues += "Ukuran ikon WhatsApp maksimal 50 KB."
        }
        return issues
    }

    fun render(asset: StickerAsset): RenderOutcome {
        val source = sourceFile(asset.sourceFile)
        if (!source.exists()) return RenderOutcome(error = "File sumber sudah tidak ditemukan.")
        val bitmap = decodeOriented(source) ?: return RenderOutcome(error = "Format gambar tidak didukung atau file rusak.")
        val output = Bitmap.createBitmap(PackRules.CANVAS_SIZE, PackRules.CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val scale = if (asset.cropToFill) {
                max(PackRules.CANVAS_SIZE.toFloat() / bitmap.width, PackRules.CANVAS_SIZE.toFloat() / bitmap.height)
            } else {
                min(PackRules.CANVAS_SIZE.toFloat() / bitmap.width, PackRules.CANVAS_SIZE.toFloat() / bitmap.height)
            }
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = (PackRules.CANVAS_SIZE - width) / 2f
            val top = (PackRules.CANVAS_SIZE - height) / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)

            asset.layers.forEach { layer ->
                val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = layer.sizeSp * PackRules.CANVAS_SIZE.toFloat() / 320f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                val x = layer.x * PackRules.CANVAS_SIZE
                val y = layer.y * PackRules.CANVAS_SIZE
                val baseline = y - (layerPaint.ascent() + layerPaint.descent()) / 2f
                if (layer.kind == "text" && layer.outlineWidth > 0f) {
                    layerPaint.style = Paint.Style.STROKE
                    layerPaint.strokeWidth = layer.outlineWidth * PackRules.CANVAS_SIZE.toFloat() / 320f
                    layerPaint.strokeJoin = Paint.Join.ROUND
                    layerPaint.color = android.graphics.Color.BLACK
                    canvas.drawText(layer.text, x, baseline, layerPaint)
                }
                layerPaint.style = Paint.Style.FILL
                layerPaint.color = try {
                    android.graphics.Color.parseColor(layer.colorHex)
                } catch (_: Exception) {
                    android.graphics.Color.WHITE
                }
                canvas.drawText(layer.text, x, baseline, layerPaint)
            }

            val outputFile = exportedFile(asset.id + ".webp")
            var compressed = false
            for (quality in listOf(92, 86, 80, 74, 68, 62, 56, 50)) {
                FileOutputStream(outputFile).use { stream ->
                    compressed = output.compress(Bitmap.CompressFormat.WEBP, quality, stream)
                }
                if (compressed && outputFile.length() in 1L..PackRules.MAX_STATIC_BYTES) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(outputFile.absolutePath, options)
                    val issues = PackRules.validateFiles(
                        listOf(StickerFileFacts(outputFile.name, true, options.outWidth, options.outHeight, outputFile.length(), options.outMimeType.orEmpty()))
                    )
                    if (issues.isEmpty()) return RenderOutcome(fileName = outputFile.name)
                }
            }
            outputFile.delete()
            return RenderOutcome(error = "Hasil belum bisa dipadatkan sampai 100 KB. Coba gambar yang lebih sederhana.")
        } catch (_: Exception) {
            return RenderOutcome(error = "Gagal merender sticker. Sumber asli tetap aman.")
        } finally {
            bitmap.recycle()
            output.recycle()
        }
    }

    private fun decodeOriented(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return decoded
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun packToJson(pack: StickerPack): JSONObject {
        val assetsJson = JSONArray()
        pack.assets.forEach { asset ->
            val layersJson = JSONArray()
            asset.layers.forEach { layer ->
                layersJson.put(
                    JSONObject()
                        .put("id", layer.id)
                        .put("kind", layer.kind)
                        .put("text", layer.text)
                        .put("x", layer.x.toDouble())
                        .put("y", layer.y.toDouble())
                        .put("sizeSp", layer.sizeSp.toDouble())
                        .put("colorHex", layer.colorHex)
                        .put("outlineWidth", layer.outlineWidth.toDouble())
                )
            }
            assetsJson.put(
                JSONObject()
                    .put("id", asset.id)
                    .put("sourceFile", asset.sourceFile)
                    .put("exportFile", asset.exportFile ?: JSONObject.NULL)
                    .put("cropToFill", asset.cropToFill)
                    .put("layers", layersJson)
            )
        }
        return JSONObject()
            .put("id", pack.id)
            .put("name", pack.name)
            .put("author", pack.author)
            .put("assets", assetsJson)
            .put("iconSourceFile", pack.iconSourceFile ?: JSONObject.NULL)
            .put("trayIconFile", pack.trayIconFile ?: JSONObject.NULL)
    }

    private fun packFromJson(json: JSONObject): StickerPack {
        val assetsArray = json.optJSONArray("assets") ?: JSONArray()
        val assetsList = buildList {
            for (assetIndex in 0 until assetsArray.length()) {
                val item = assetsArray.getJSONObject(assetIndex)
                val layersArray = item.optJSONArray("layers") ?: JSONArray()
                val layersList = buildList {
                    for (layerIndex in 0 until layersArray.length()) {
                        val layer = layersArray.getJSONObject(layerIndex)
                        add(
                            StickerLayer(
                                id = layer.optString("id", UUID.randomUUID().toString()),
                                kind = layer.optString("kind", "text"),
                                text = layer.optString("text", ""),
                                x = layer.optDouble("x", 0.5).toFloat(),
                                y = layer.optDouble("y", 0.78).toFloat(),
                                sizeSp = layer.optDouble("sizeSp", 28.0).toFloat(),
                                colorHex = layer.optString("colorHex", "#FFFFFF"),
                                outlineWidth = layer.optDouble("outlineWidth", 3.0).toFloat()
                            )
                        )
                    }
                }
                val export = item.optString("exportFile").takeIf { it.isNotBlank() && it != "null" }
                add(
                    StickerAsset(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        sourceFile = item.optString("sourceFile", ""),
                        exportFile = export,
                        layers = layersList,
                        cropToFill = item.optBoolean("cropToFill", false)
                    )
                )
            }
        }
        return StickerPack(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Pack tanpa nama"),
            author = json.optString("author", ""),
            assets = assetsList,
            iconSourceFile = json.optString("iconSourceFile").takeIf { it.isNotBlank() && it != "null" },
            trayIconFile = json.optString("trayIconFile").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
