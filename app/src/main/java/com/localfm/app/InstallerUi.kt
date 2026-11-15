package com.localfm.app

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun methodLabel(m: InstallMethod): String = when (m) {
    InstallMethod.AUTO -> "Tự động"
    InstallMethod.ANDROID -> "Trình cài Android"
    InstallMethod.SHIZUKU -> "Shizuku"
}

/** Trình cài toàn màn hình: tìm APK/XAPK trên bộ nhớ trong và thẻ nhớ, chọn và cài nhiều gói. */
@Composable
fun InstallerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    InstallPrefs.init(ctx)
    InstallHistory.init(ctx)

    var entries by remember { mutableStateOf<List<PkgEntry>>(emptyList()) }
    var checks by remember { mutableStateOf<Map<String, Pair<Conflict, String>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<String>() }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var shizuku by remember { mutableStateOf(ShizukuInstaller.State.NOT_INSTALLED) }

    fun checkFor(list: List<PkgEntry>): Map<String, Pair<Conflict, String>> =
        list.associate { it.file.absolutePath to InstallCheck.check(ctx, it) }

    fun scanAll() {
        loading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { InstallScanner.scan(ctx).map { PkgParser.parse(ctx, it) } }
            val map = withContext(Dispatchers.IO) { checkFor(list) }
            entries = list
            checks = map
            loading = false
            shizuku = ShizukuInstaller.state(ctx)
        }
    }

    LaunchedEffect(Unit) { scanAll() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) {
                    uris.mapNotNull { IncomingFiles.toFile(ctx, it, null) }
                        .filter { ApkInstaller.isInstallable(it) }
                        .map { PkgParser.parse(ctx, it) }
                }
                if (added.isEmpty()) {
                    Toast.makeText(ctx, "Không có APK/XAPK trong lựa chọn", Toast.LENGTH_SHORT).show()
                } else {
                    val map = withContext(Dispatchers.IO) { checkFor(added) }
                    entries = (entries + added).distinctBy { it.file.absolutePath }
                    checks = checks + map
                }
            }
        }
    }

    fun installSelected() {
        val blocked = setOf(Conflict.DOWNGRADE, Conflict.SIGNATURE_MISMATCH, Conflict.UNKNOWN)
        val chosen = entries.filter { e ->
            e.file.absolutePath in selected &&
                (checks[e.file.absolutePath]?.first?.let { it !in blocked } ?: true)
        }
        val skipped = selected.size - chosen.size
        if (chosen.isEmpty()) {
            Toast.makeText(ctx, "Không có gói nào cài được trong lựa chọn", Toast.LENGTH_SHORT).show()
            return
        }
        running = true
        results.clear()
        progress = 0f
        val method = InstallPrefs.method
        scope.launch {
            chosen.forEachIndexed { i, e ->
                current = e.label
                val out = InstallRunner.run(ctx, e, method)
                val sym = when {
                    out.ok -> "✓"
                    out.pending -> "⏳"
                    else -> "✗"
                }
                results.add("$sym ${e.label}: ${out.message}")
                progress = (i + 1f) / chosen.size
            }
            if (skipped > 0) results.add("Bỏ qua $skipped gói có xung đột (xem ghi chú)")
            running = false
            current = ""
            shizuku = ShizukuInstaller.state(ctx)
        }
    }

    Dialog(
        onDismissRequest = { if (!running) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Trình cài APK / XAPK",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Outlined.History, contentDescription = "Lịch sử cài đặt")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Cài đặt trình cài")
                    }
                    IconButton(onClick = { if (!running) onDismiss() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Đóng")
                    }
                }
                Text(
                    "Phương thức: ${methodLabel(InstallPrefs.method)} · Shizuku: ${ShizukuInstaller.label(shizuku)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !running) {
                        Text("Chọn tệp")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { scanAll() }, enabled = !running && !loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Quét lại")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${entries.size} gói", style = MaterialTheme.typography.bodySmall)
                }
                if (loading) {
                    ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (!loading && entries.isEmpty()) {
                        item {
                            Text(
                                "Không tìm thấy APK/XAPK trên bộ nhớ trong hoặc thẻ nhớ. Bấm \"Chọn tệp\" để chọn thủ công.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                    items(entries, key = { it.file.absolutePath }) { e ->
                        val path = e.file.absolutePath
                        PkgRow(
                            e = e,
                            check = checks[path],
                            selected = path in selected,
                            enabled = !running,
                            onToggle = { on -> selected = if (on) selected + path else selected - path }
                        )
                    }
                }
                if (running || results.isNotEmpty()) {
                    ExpressiveLinearProgress(
                        progress = if (running) progress else 1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (running) {
                        Text("Đang cài: $current", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                        results.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { installSelected() },
                    enabled = !running && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cài đã chọn (${selected.size})")
                }
            }
        }
    }

    if (showSettings) {
        InstallSettingsDialog(
            onDismiss = {
                showSettings = false
                shizuku = ShizukuInstaller.state(ctx)
            },
            onOpenHistory = {
                showSettings = false
                showHistory = true
            }
        )
    }
    if (showHistory) InstallHistoryDialog(onDismiss = { showHistory = false })
}

@Composable
private fun PkgRow(
    e: PkgEntry,
    check: Pair<Conflict, String>?,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val icon by produceState<Bitmap?>(null, e.file.absolutePath) {
        value = if (e.error == null) withContext(Dispatchers.IO) { ThumbCache.apk(ctx, e.file) } else null
    }
    val bmp = icon
    val warnColor = when (check?.first) {
        Conflict.DOWNGRADE, Conflict.SIGNATURE_MISMATCH, Conflict.UNKNOWN -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val detail = buildString {
        append(e.packageName ?: "không rõ tên gói")
        append(" · v").append(e.versionName ?: e.versionCode.toString())
        append(" · ").append(formatSize(e.sizeBytes))
        if (e.isXapk) {
            append(" · XAPK ${e.apkNames.size} APK")
            if (e.obbNames.isNotEmpty()) append(" · ${e.obbNames.size} OBB")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onToggle,
            enabled = enabled && e.error == null
        )
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = e.label,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = if (e.isXapk) Icons.Outlined.Archive else Icons.Outlined.Android,
                    contentDescription = if (e.isXapk) "XAPK" else "APK",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val note = e.error ?: check?.second
            if (!note.isNullOrBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = warnColor, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun InstallSettingsDialog(onDismiss: () -> Unit, onOpenHistory: () -> Unit) {
    val ctx = LocalContext.current
    InstallPrefs.init(ctx)
    var state by remember { mutableStateOf(ShizukuInstaller.state(ctx)) }
    val options = listOf(
        InstallMethod.AUTO to "Tự động (Shizuku nếu sẵn sàng, không thì trình cài Android)",
        InstallMethod.ANDROID to "Trình cài Android (có màn hình xác nhận)",
        InstallMethod.SHIZUKU to "Shizuku (không sẵn sàng thì dùng trình cài Android)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt trình cài APK") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Phương thức cài đặt", style = MaterialTheme.typography.titleSmall)
                options.forEach { (m, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { InstallPrefs.setMethod(ctx, m) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = InstallPrefs.method == m, onClick = { InstallPrefs.setMethod(ctx, m) })
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tự xoá tệp tạm sau khi cài", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Chỉ xoá bản giải nén tạm. Không xoá tệp APK/XAPK gốc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = InstallPrefs.deleteTemp,
                        onCheckedChange = { InstallPrefs.setDeleteTemp(ctx, it) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Shizuku", style = MaterialTheme.typography.titleSmall)
                Text(ShizukuInstaller.label(state), style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = {
                        Toast.makeText(ctx, RootAccess.requestShizuku(), Toast.LENGTH_LONG).show()
                    }) { Text("Xin quyền Shizuku") }
                    TextButton(onClick = { state = ShizukuInstaller.state(ctx) }) { Text("Kiểm tra lại") }
                }
                TextButton(onClick = onOpenHistory) { Text("Xem lịch sử cài đặt") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}

@Composable
fun InstallHistoryDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    InstallHistory.init(ctx)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lịch sử cài đặt") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (InstallHistory.entries.isEmpty()) Text("Chưa có lần cài nào.")
                InstallHistory.entries.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        dismissButton = {
            if (InstallHistory.entries.isNotEmpty()) {
                TextButton(onClick = { InstallHistory.clear(ctx) }) { Text("Xoá lịch sử") }
            }
        }
    )
}
