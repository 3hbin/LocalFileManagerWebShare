package com.localfm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/** Một ổ lưu trữ: bộ nhớ trong hoặc thẻ nhớ SD. */
data class StorageItem(
    val name: String,
    val root: File,
    val removable: Boolean,
    val mounted: Boolean,
    val total: Long,
    val free: Long
)

object StorageVolumes {
    /**
     * Liệt kê ổ đĩa lấy từ hệ thống (không tạo dữ liệu giả).
     * Bộ nhớ trong luôn có. Thẻ nhớ chỉ xuất hiện khi Android báo đã gắn (getExternalFilesDirs trả về thư mục của ổ đó).
     */
    fun list(ctx: Context): List<StorageItem> {
        val out = mutableListOf<StorageItem>()
        val primary = Environment.getExternalStorageDirectory()
        val primaryMounted = Environment.getExternalStorageState(primary) == Environment.MEDIA_MOUNTED
        out += item("Bộ nhớ trong", primary, removable = false, mounted = primaryMounted)

        // Tên và loại ổ từ StorageManager (API 24+; minSdk của app là 26)
        val described = HashMap<String, Pair<String, Boolean>>()
        try {
            ctx.getSystemService(StorageManager::class.java)?.storageVolumes?.forEach { v ->
                if (!v.isPrimary) {
                    val uuid = v.uuid
                    if (uuid != null) described[uuid] = v.getDescription(ctx) to v.isRemovable
                }
            }
        } catch (_: Exception) {}

        val seen = HashSet<String>()
        seen += primary.absolutePath
        ctx.getExternalFilesDirs(null).forEach { appDir ->
            val root = appDir?.let { volumeRootFromAppDir(it) } ?: return@forEach
            if (!seen.add(root.absolutePath)) return@forEach
            val (desc, removable) = described[root.name] ?: ("Thẻ nhớ" to true)
            val mounted = Environment.getExternalStorageState(root) == Environment.MEDIA_MOUNTED
            out += item(desc.ifBlank { "Thẻ nhớ" }, root, removable, mounted)
        }
        return out
    }

    /** /storage/XXXX-XXXX/Android/data/pkg/files -> /storage/XXXX-XXXX */
    private fun volumeRootFromAppDir(f: File): File? {
        val path = f.absolutePath
        val idx = path.indexOf("/Android/")
        if (idx <= 0) return null
        return File(path.substring(0, idx))
    }

    private fun item(name: String, root: File, removable: Boolean, mounted: Boolean) = StorageItem(
        name = name,
        root = root,
        removable = removable,
        mounted = mounted,
        total = if (mounted) root.totalSpace else 0L,
        free = if (mounted) root.usableSpace else 0L
    )

    /** Thư mục có đọc trực tiếp được không. */
    fun readable(root: File): Boolean = root.isDirectory && root.canRead() && root.listFiles() != null

    /** Ổ chứa [dir] (ổ có đường dẫn dài nhất khớp). */
    fun rootOf(dir: File, items: List<StorageItem>): File? {
        val p = dir.absolutePath
        return items.map { it.root }
            .filter { p == it.absolutePath || p.startsWith(it.absolutePath + "/") }
            .maxByOrNull { it.absolutePath.length }
    }
}

/** Các thư mục thẻ nhớ mà người dùng đã cấp qua SAF (chọn thư mục). */
object SafGrants {
    fun save(ctx: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
            OpLog.add("Cấp quyền truy cập thư mục: ${Uri.decode(uri.toString())}")
        } catch (_: Exception) {
            OpLog.add("Không lưu được quyền truy cập thư mục đã chọn")
        }
    }

    fun count(ctx: Context): Int = try {
        ctx.contentResolver.persistedUriPermissions.count { it.isReadPermission }
    } catch (_: Exception) { 0 }
}

/** Hàng chip chọn ổ: bộ nhớ trong và thẻ nhớ. */
@Composable
fun VolumeChips(
    items: List<StorageItem>,
    current: File,
    onPick: (StorageItem) -> Unit,
    onNotMounted: (StorageItem) -> Unit,
    onNeedAccess: (StorageItem) -> Unit
) {
    val active = StorageVolumes.rootOf(current, items)
    // Đọc quyền truy cập một lần cho mỗi danh sách ổ, không đọc lại khi vẽ lại
    val readableMap = remember(items) { items.associate { it.root.absolutePath to StorageVolumes.readable(it.root) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { v ->
            val readable = v.mounted && (readableMap[v.root.absolutePath] == true)
            val label = when {
                !v.mounted -> "${v.name} · chưa gắn"
                !readable -> "${v.name} · cần cấp quyền"
                else -> "${v.name} · còn ${formatSize(v.free)}"
            }
            FilterChip(
                selected = active?.absolutePath == v.root.absolutePath,
                onClick = {
                    when {
                        !v.mounted -> onNotMounted(v)
                        !readable -> onNeedAccess(v)
                        else -> onPick(v)
                    }
                },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}

/**
 * Lắng nghe khi thẻ nhớ được gắn hoặc tháo ra, để cập nhật danh sách ổ.
 * Gọi [onChange] trên luồng chính.
 */
@Composable
fun VolumeListener(onChange: () -> Unit) {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onChange)
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                latest()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ctx.registerReceiver(receiver, filter)
        onDispose {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }
}
