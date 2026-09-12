package com.localfm.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.localfm.app.ui.theme.LocalFileManagerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình chính: trình quản lý tệp local + bảng điều khiển Web Share.
 * Toàn bộ UI viết bằng Jetpack Compose + Material 3.
 * Không sử dụng emoji — chỉ Material Icons / Material Symbols đơn sắc.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = remember { mutableStateOf(FmSettings.darkMode(this@MainActivity)) }
            LocalFileManagerTheme(darkTheme = dark.value) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileManagerApp(darkMode = dark.value, onDarkMode = {
                        dark.value = it
                        FmSettings.setDarkMode(this@MainActivity, it)
                    })
                }
            }
        }
    }
}

private const val PREFS_NAME = "local_fm_prefs"
private const val KEY_FIRST_LAUNCH = "first_launch_done"
private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

private enum class SortMode { NAME, DATE, SIZE, TYPE }

/** Giữ instance server để không bị huỷ khi Compose recomposition. */
private object ServerHolder {
    var server: LocalWebServer? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerApp(darkMode: Boolean, onDarkMode: (Boolean) -> Unit) {
    val context = LocalContext.current
    val storageRoot = remember { defaultStorageRoot() }

    var currentDir by remember { mutableStateOf(storageRoot) }
    var entries by remember { mutableStateOf(listFilesSafe(currentDir)) }
    var hasAllFilesAccess by remember { mutableStateOf(hasManageStoragePermission()) }
    var serverRunning by remember { mutableStateOf(ServerHolder.server?.isAlive == true) }
    var localIp by remember { mutableStateOf(NetworkUtils.getLocalWifiIpv4(context)) }
    var showGuide by remember { mutableStateOf(!isFirstLaunchDone(context)) }

    // Trạng thái các dialog thao tác tệp — gọn như dialog sửa báo thức
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var pendingRename by remember { mutableStateOf<File?>(null) }
    var pendingDetails by remember { mutableStateOf<File?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var shortcut by remember { mutableStateOf("all") }
    var recursive by remember { mutableStateOf(false) }
    var webPass by remember { mutableStateOf(FmSettings.webPassword(context)) }
    var hideSizes by remember { mutableStateOf(FmSettings.hideSizes(context)) }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccess = hasManageStoragePermission()
        entries = listFilesSafe(currentDir, showHidden)
    }

    fun refresh() {
        hasAllFilesAccess = hasManageStoragePermission()
        localIp = NetworkUtils.getLocalWifiIpv4(context)
        entries = listFilesSafe(currentDir, showHidden)
        serverRunning = ServerHolder.server?.isAlive == true
        ServerHolder.server?.shareRoot = currentDir
        ServerHolder.server?.sharePassword = FmSettings.webPassword(context)
        ServerHolder.server?.hideSizes = FmSettings.hideSizes(context)
    }

    LaunchedEffect(currentDir, showHidden) {
        entries = listFilesSafe(currentDir, showHidden)
        ServerHolder.server?.shareRoot = currentDir
        ServerHolder.server?.sharePassword = FmSettings.webPassword(context)
        ServerHolder.server?.hideSizes = FmSettings.hideSizes(context)
    }

    val visibleFiles = remember(entries, query, sortMode, recursive, currentDir, shortcut) {
        val base = when (shortcut) {
            "fav" -> FmSettings.favorites(context).map { File(it) }.filter { it.exists() }
            "recent" -> FmSettings.recents(context).map { File(it) }.filter { it.exists() }
            else -> entries
        }
        val searched = if (query.isBlank()) base
        else if (recursive && shortcut == "all") searchRecursive(currentDir, query)
        else base.filter { it.name.contains(query, ignoreCase = true) }
        searched.sortedWith(sortComparator(sortMode))
    }

    val shareUrl = remember(localIp, serverRunning) {
        if (!localIp.isNullOrBlank()) "http://$localIp:${LocalWebServer.DEFAULT_PORT}" else null
    }

    // Dừng server khi Activity bị huỷ hoàn toàn
    DisposableEffect(Unit) {
        onDispose { /* giữ server khi xoay màn hình */ }
    }

    val canGoUp = currentDir.canonicalPath != storageRoot.canonicalPath &&
        currentDir.canonicalPath.startsWith(storageRoot.canonicalPath)

    BackHandler(enabled = canGoUp) {
        currentDir.parentFile?.let { currentDir = it }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = currentDir.name.ifBlank { "Bộ nhớ" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (canGoUp) {
                        IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Quay lại thư mục cha"
                            )
                        }
                    } else {
                        IconButton(onClick = { }) {
                            Icon(
                                imageVector = Icons.Outlined.SdStorage,
                                contentDescription = "Bộ nhớ trong"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHidden = !showHidden }) {
                        Icon(
                            imageVector = if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Ẩn hiện tệp ẩn"
                        )
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Sắp xếp")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("Theo tên") }, onClick = {
                            sortMode = SortMode.NAME
                            showSortMenu = false
                        })
                        DropdownMenuItem(text = { Text("Theo ngày") }, onClick = {
                            sortMode = SortMode.DATE
                            showSortMenu = false
                        })
                        DropdownMenuItem(text = { Text("Theo dung lượng") }, onClick = {
                            sortMode = SortMode.SIZE
                            showSortMenu = false
                        })
                        DropdownMenuItem(text = { Text("Theo loại") }, onClick = {
                            sortMode = SortMode.TYPE
                            showSortMenu = false
                        })
                    }
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Cài đặt")
                    }
                    IconButton(onClick = { showGuide = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Hướng dẫn")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (hasAllFilesAccess) {
                FloatingActionButton(
                    onClick = { showCreateFolder = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = "Tạo thư mục"
                    )
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            PathBar(path = currentDir.absolutePath)
            StorageBar(dir = currentDir)

            ShortcutRow(
                current = shortcut,
                onPick = { shortcut = it },
                onPictures = {
                    shortcut = "all"
                    currentDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                },
                onDownload = {
                    shortcut = "all"
                    currentDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                },
                onDocs = {
                    shortcut = "all"
                    currentDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                },
                onDcim = {
                    shortcut = "all"
                    currentDir = File(android.os.Environment.getExternalStorageDirectory(), "DCIM")
                }
            )

            ServerControlPanel(
                running = serverRunning,
                ip = localIp,
                canStart = hasAllFilesAccess,
                onCopyUrl = {
                    val url = shareUrl
                    if (url.isNullOrBlank()) {
                        toast(context, "Chưa có địa chỉ IP")
                    } else {
                        copyToClipboard(context, url)
                        toast(context, "Đã copy $url")
                    }
                },
                onShowQr = {
                    if (shareUrl.isNullOrBlank() || !serverRunning) {
                        toast(context, "Hãy bật Web Share trước")
                    } else {
                        showQr = true
                    }
                },
                onToggle = { enabled ->
                    if (enabled) {
                        val started = startServer(context, currentDir)
                        serverRunning = started
                        localIp = NetworkUtils.getLocalWifiIpv4(context)
                        if (!started) {
                            toast(context, "Không thể mở cổng 8080. Kiểm tra Wi-Fi hoặc cổng đang bận.")
                        }
                    } else {
                        stopServer()
                        serverRunning = false
                    }
                }
            )

            if (!hasAllFilesAccess) {
                PermissionBanner(
                    onGrant = {
                        permissionLauncher.launch(manageStorageIntent(context))
                    }
                )
            }

            SearchRow(query = query, onQuery = { query = it }, count = visibleFiles.size)
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tìm cả cây thư mục", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = recursive, onCheckedChange = { recursive = it })
            }
            if (selecting || selected.isNotEmpty()) {
                SelectionBar(
                    count = selected.size,
                    hasClip = ClipHolder.clip != null,
                    onClear = { selected = emptySet(); selecting = false },
                    onDelete = {
                        selected.map { File(it) }.forEach { deleteRecursivelySafe(it) }
                        selected = emptySet(); selecting = false
                        entries = listFilesSafe(currentDir, showHidden)
                    },
                    onCopy = {
                        ClipHolder.clip = ClipBoard(selected.toList(), false)
                        toast(context, "Đã copy ${selected.size} mục")
                    },
                    onCut = {
                        ClipHolder.clip = ClipBoard(selected.toList(), true)
                        toast(context, "Đã cắt ${selected.size} mục")
                    },
                    onPaste = {
                        val n = pasteClip(currentDir)
                        toast(context, if (n >= 0) "Đã dán $n mục" else "Không dán được")
                        entries = listFilesSafe(currentDir, showHidden)
                    },
                    onZip = {
                        val dest = File(currentDir, "archive_${System.currentTimeMillis()}.zip")
                        val ok = ZipUtils.zipTo(selected.map { File(it) }, dest)
                        toast(context, if (ok) "Đã nén ${dest.name}" else "Không nén được")
                        selected = emptySet(); selecting = false
                        entries = listFilesSafe(currentDir, showHidden)
                    },
                    onShare = {
                        shareFiles(context, selected.map { File(it) }.filter { it.isFile })
                    }
                )
            }

            FileList(
                files = visibleFiles,
                selected = selected,
                selecting = selecting,
                onToggleSelect = { file ->
                    selecting = true
                    selected = if (file.absolutePath in selected) selected - file.absolutePath else selected + file.absolutePath
                },
                onOpen = { file ->
                    if (selecting) {
                        selected = if (file.absolutePath in selected) selected - file.absolutePath else selected + file.absolutePath
                    } else if (file.isDirectory) {
                        currentDir = file
                    } else {
                        FmSettings.addRecent(context, file.absolutePath)
                        when {
                            isImage(file) || isMedia(file) || isText(file) || isPdf(file) -> previewFile = file
                            else -> openFile(context, file)
                        }
                    }
                },
                onRequestMenu = { menuFor = it },
                menuFor = menuFor,
                onDismissMenu = { menuFor = null },
                onRename = {
                    pendingRename = it
                    menuFor = null
                },
                onDelete = {
                    pendingDelete = it
                    menuFor = null
                },
                onDetails = {
                    pendingDetails = it
                    menuFor = null
                },
                onOpenFile = {
                    if (it.isDirectory) currentDir = it else openFile(context, it)
                    menuFor = null
                },
                onDuplicate = {
                    val ok = duplicateSafe(it)
                    toast(context, if (ok) "Đã tạo bản sao" else "Không sao chép được")
                    menuFor = null
                    entries = listFilesSafe(currentDir, showHidden)
                }
            )
        }
    }

    if (showGuide) {
        FirstLaunchDialog(
            onDismiss = {
                markFirstLaunchDone(context)
                showGuide = false
            },
            onGrantPermission = {
                markFirstLaunchDone(context)
                showGuide = false
                permissionLauncher.launch(manageStorageIntent(context))
            }
        )
    }

    pendingDelete?.let { target ->
        ConfirmDeleteDialog(
            file = target,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                val ok = deleteRecursivelySafe(target)
                toast(context, if (ok) "Đã xoá ${target.name}" else "Không xoá được")
                pendingDelete = null
                entries = listFilesSafe(currentDir, showHidden)
            }
        )
    }

    pendingRename?.let { target ->
        RenameDialog(
            file = target,
            onDismiss = { pendingRename = null },
            onConfirm = { newName ->
                val result = renameSafe(target, newName)
                toast(context, result ?: "Đã đổi tên")
                pendingRename = null
                entries = listFilesSafe(currentDir, showHidden)
            }
        )
    }

    pendingDetails?.let { target ->
        FileDetailsDialog(file = target, onDismiss = { pendingDetails = null })
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onConfirm = { name ->
                val created = createFolderSafe(currentDir, name)
                toast(context, if (created) "Đã tạo thư mục" else "Không tạo được thư mục")
                showCreateFolder = false
                entries = listFilesSafe(currentDir, showHidden)
            }
        )
    }

    if (showQr && !shareUrl.isNullOrBlank()) {
        QrShareDialog(
            url = shareUrl.orEmpty(),
            onDismiss = { showQr = false }
        )
    }

    previewFile?.let { f ->
        when {
            isImage(f) -> ImagePreviewDialog(f) { previewFile = null }
            isMedia(f) -> MediaPreviewDialog(f) { previewFile = null }
            isText(f) -> TextPreviewDialog(f) { previewFile = null }
            isPdf(f) -> PdfPreviewDialog(f) { previewFile = null }
            else -> previewFile = null
        }
    }

    if (showSettings) {
        SettingsDialog(
            dark = darkMode,
            password = webPass,
            hideSizes = hideSizes,
            onDark = onDarkMode,
            onPassword = {
                webPass = it
                FmSettings.setWebPassword(context, it)
                ServerHolder.server?.sharePassword = it
            },
            onHideSizes = {
                hideSizes = it
                FmSettings.setHideSizes(context, it)
                ServerHolder.server?.hideSizes = it
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun StorageBar(dir: File) {
    val usable = dir.usableSpace
    val total = dir.totalSpace.takeIf { it > 0 } ?: return
    Text(
        text = "Còn trống ${formatSize(usable)} / ${formatSize(total)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
    )
}

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, count: Int) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text("Tìm trong thư mục") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun QrShareDialog(url: String, onDismiss: () -> Unit) {
    val bitmap = remember(url) { QrCodeUtils.makeBitmap(url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.QrCode2, contentDescription = null) },
        title = { Text("Quét để mở Web Share") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Mã QR Web Share",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(url, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
private fun PathBar(path: String) {
    Text(
        text = path,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

/**
 * Bảng điều khiển server: công tắc lớn, IP local, URL truy cập từ máy tính.
 */
@Composable
private fun ServerControlPanel(
    running: Boolean,
    ip: String?,
    canStart: Boolean,
    onToggle: (Boolean) -> Unit,
    onCopyUrl: () -> Unit,
    onShowQr: () -> Unit
) {
    val url = if (!ip.isNullOrBlank()) "http://$ip:${LocalWebServer.DEFAULT_PORT}" else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (running) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                    contentDescription = if (running) "Server đang bật" else "Server đang tắt",
                    tint = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Web Share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (running) "Đang phát trên cổng 8080" else "Máy chủ đang tắt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = running,
                    enabled = canStart || running,
                    onCheckedChange = onToggle
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Địa chỉ chia sẻ",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !canStart -> "Cần quyền bộ nhớ trước khi mở server"
                            url != null && running -> url
                            url != null -> "Sẵn sàng: $url"
                            else -> "Không lấy được IP Wi-Fi (192.168.x.x)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onCopyUrl) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy địa chỉ")
                }
                IconButton(onClick = onShowQr) {
                    Icon(Icons.Outlined.QrCode2, contentDescription = "Mã QR")
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = "Thiếu quyền")
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Cần quyền quản lý tệp", fontWeight = FontWeight.Medium)
                Text(
                    "Mở cài đặt và bật All files access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onGrant) { Text("Cấp quyền") }
        }
    }
}

@Composable
private fun FileList(
    files: List<File>,
    selected: Set<String> = emptySet(),
    selecting: Boolean = false,
    onToggleSelect: (File) -> Unit = {},
    onOpen: (File) -> Unit,
    onRequestMenu: (File) -> Unit,
    menuFor: File?,
    onDismissMenu: () -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onDetails: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onDuplicate: (File) -> Unit
) {
    if (files.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = "Thư mục trống",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Không có tệp nào",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp, top = 4.dp)
    ) {
        items(files, key = { it.absolutePath }) { file ->
            FileRow(
                file = file,
                selected = file.absolutePath in selected,
                selecting = selecting,
                onToggleSelect = { onToggleSelect(file) },
                menuExpanded = menuFor?.absolutePath == file.absolutePath,
                onOpen = { onOpen(file) },
                onRequestMenu = { onRequestMenu(file) },
                onDismissMenu = onDismissMenu,
                onRename = { onRename(file) },
                onDelete = { onDelete(file) },
                onDetails = { onDetails(file) },
                onOpenFile = { onOpenFile(file) },
                onDuplicate = { onDuplicate(file) }
            )
        }
    }
}

@Composable
private fun FileRow(
    file: File,
    selected: Boolean = false,
    selecting: Boolean = false,
    onToggleSelect: () -> Unit = {},
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onOpenFile: () -> Unit,
    onDuplicate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        Icon(
            imageVector = iconFor(file),
            contentDescription = if (file.isDirectory) "Thư mục" else "Tệp",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitleFor(file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRequestMenu) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Tùy chọn")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("Mở") },
                onClick = onOpenFile,
                leadingIcon = {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Chi tiết") },
                onClick = onDetails,
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (FmSettings.isFavorite(LocalContext.current, file.absolutePath)) "Bỏ yêu thích" else "Yêu thích") },
                onClick = {
                    FmSettings.toggleFavorite(onOpen.let { LocalContext.current }, file.absolutePath)
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Chia sẻ") },
                onClick = {
                    shareFiles(LocalContext.current, listOf(file))
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Nén ZIP") },
                onClick = {
                    val dest = File(file.parentFile, file.nameWithoutExtension + ".zip")
                    val ok = ZipUtils.zipTo(listOf(file), dest)
                    android.widget.Toast.makeText(LocalContext.current, if (ok) "Đã nén" else "Lỗi nén", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Giải nén") },
                onClick = {
                    val dest = File(file.parentFile, file.nameWithoutExtension)
                    val err = ZipUtils.unzip(file, dest)
                    android.widget.Toast.makeText(LocalContext.current, err ?: "Đã giải nén", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Đổi tên") },
                onClick = onRename,
                leadingIcon = {
                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Tạo bản sao") },
                onClick = onDuplicate,
                leadingIcon = { Icon(Icons.Outlined.FileCopy, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Xoá") },
                onClick = onDelete,
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) }
            )
        }
    }
}

/**
 * Hướng dẫn lần đầu mở ứng dụng: cấp quyền + bật server chia sẻ.
 */
@Composable
private fun FirstLaunchDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text("Chào mừng") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ba bước để dùng Local File Manager & Web Share:")
                GuideStep(1, "Cấp quyền quản lý tất cả tệp (All files access).")
                GuideStep(2, "Kết nối điện thoại và máy tính cùng một Wi-Fi.")
                GuideStep(3, "Bật công tắc Web Share rồi mở địa chỉ http://IP:8080 trên trình duyệt.")
            }
        },
        confirmButton = {
            TextButton(onClick = onGrantPermission) { Text("Cấp quyền") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Để sau") }
        }
    )
}

@Composable
private fun GuideStep(index: Int, text: String) {
    Row {
        Text("$index.", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(22.dp))
        Text(text)
    }
}

@Composable
private fun ConfirmDeleteDialog(
    file: File,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text("Xoá mục này?") },
        text = {
            Text(
                if (file.isDirectory) {
                    "Thư mục \"${file.name}\" và toàn bộ nội dung sẽ bị xoá vĩnh viễn."
                } else {
                    "Tệp \"${file.name}\" sẽ bị xoá vĩnh viễn."
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Xoá") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

@Composable
private fun RenameDialog(
    file: File,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(file.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
        title = { Text("Đổi tên") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Tên mới") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && value != file.name,
                onClick = { onConfirm(value.trim()) }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
        title = { Text("Tạo thư mục") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Tên thư mục") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) }
            ) { Text("Tạo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

@Composable
private fun FileDetailsDialog(file: File, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text("Chi tiết") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Tên", file.name)
                DetailLine("Loại", if (file.isDirectory) "Thư mục" else file.extension.ifBlank { "Tệp" })
                DetailLine("Đường dẫn", file.absolutePath)
                DetailLine(
                    "Kích thước",
                    if (file.isDirectory) "—" else formatSize(file.length())
                )
                DetailLine("Sửa đổi", DATE_FMT.format(Date(file.lastModified())))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}


@Composable
private fun ShortcutRow(
    current: String,
    onPick: (String) -> Unit,
    onPictures: () -> Unit,
    onDownload: () -> Unit,
    onDocs: () -> Unit,
    onDcim: () -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = current == "all", onClick = { onPick("all") }, label = { Text("Tất cả") })
        FilterChip(selected = false, onClick = onPictures, label = { Text("Ảnh") })
        FilterChip(selected = false, onClick = onDownload, label = { Text("Tải xuống") })
        FilterChip(selected = false, onClick = onDocs, label = { Text("Tài liệu") })
        FilterChip(selected = false, onClick = onDcim, label = { Text("DCIM") })
        FilterChip(selected = current == "fav", onClick = { onPick("fav") }, label = { Text("Yêu thích") })
        FilterChip(selected = current == "recent", onClick = { onPick("recent") }, label = { Text("Gần đây") })
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    hasClip: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onZip: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$count đã chọn", modifier = Modifier.padding(end = 8.dp))
        TextButton(onClick = onCopy) { Text("Copy") }
        TextButton(onClick = onCut) { Text("Cắt") }
        if (hasClip) TextButton(onClick = onPaste) { Text("Dán") }
        TextButton(onClick = onZip) { Text("ZIP") }
        TextButton(onClick = onShare) { Text("Chia sẻ") }
        TextButton(onClick = onDelete) { Text("Xoá") }
        TextButton(onClick = onClear) { Text("Huỷ") }
    }
}

@Composable
private fun SettingsDialog(
    dark: Boolean,
    password: String,
    hideSizes: Boolean,
    onDark: (Boolean) -> Unit,
    onPassword: (String) -> Unit,
    onHideSizes: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pass by rememberSaveable { mutableStateOf(password) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Giao diện tối", modifier = Modifier.weight(1f))
                    Switch(checked = dark, onCheckedChange = onDark)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ẩn dung lượng trên Web Share", modifier = Modifier.weight(1f))
                    Switch(checked = hideSizes, onCheckedChange = onHideSizes)
                }
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; onPassword(it) },
                    label = { Text("Mật khẩu Web Share (trống = không)") },
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}

// -----------------------------------------------------------------------------
// Icon & nhãn tệp (Material Icons, không emoji)
// -----------------------------------------------------------------------------

private fun iconFor(file: File): ImageVector {
    if (file.isDirectory) return Icons.Outlined.Folder
    return when (file.extension.lowercase(Locale.US)) {
        "pdf" -> Icons.Outlined.PictureAsPdf
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> Icons.Outlined.Image
        "mp4", "mkv", "avi", "webm", "mov" -> Icons.Outlined.Movie
        "mp3", "wav", "aac", "ogg", "m4a", "flac" -> Icons.Outlined.AudioFile
        "txt", "md", "log", "json", "xml", "doc", "docx" -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private fun subtitleFor(file: File): String {
    val time = DATE_FMT.format(Date(file.lastModified()))
    return if (file.isDirectory) {
        val count = file.listFiles()?.size ?: 0
        "$count mục  ·  $time"
    } else {
        "${formatSize(file.length())}  ·  $time"
    }
}

// -----------------------------------------------------------------------------
// Quyền, server, thao tác tệp
// -----------------------------------------------------------------------------

private fun hasManageStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

private fun manageStorageIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

private fun defaultStorageRoot(): File {
    val ext = Environment.getExternalStorageDirectory()
    return if (ext != null && ext.exists()) ext else Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    )
}

private fun listFilesSafe(dir: File, showHidden: Boolean = false): List<File> {
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()
        ?.filter { showHidden || !it.name.startsWith(".") }
        .orEmpty()
}

private fun sortComparator(mode: SortMode): Comparator<File> {
    val foldersFirst = compareBy<File> { !it.isDirectory }
    return when (mode) {
        SortMode.NAME -> foldersFirst.thenBy { it.name.lowercase(Locale.US) }
        SortMode.DATE -> foldersFirst.thenByDescending { it.lastModified() }
        SortMode.SIZE -> foldersFirst.thenByDescending { if (it.isDirectory) 0L else it.length() }
        SortMode.TYPE -> foldersFirst.thenBy { it.extension.lowercase(Locale.US) }.thenBy { it.name.lowercase(Locale.US) }
    }
}

private fun duplicateSafe(file: File): Boolean {
    return try {
        val parent = file.parentFile ?: return false
        val dest = uniqueCopyName(parent, file)
        if (file.isDirectory) file.copyRecursively(dest, overwrite = false)
        else file.copyTo(dest, overwrite = false)
        dest.exists()
    } catch (_: Exception) {
        false
    }
}

private fun uniqueCopyName(parent: File, source: File): File {
    val base = source.nameWithoutExtension.ifBlank { source.name }
    val ext = source.extension
    var i = 1
    while (true) {
        val name = if (source.isDirectory || ext.isBlank()) "${base} ($i)" else "${base} ($i).$ext"
        val candidate = File(parent, name)
        if (!candidate.exists()) return candidate
        i++
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("web-share-url", text))
}

private fun startServer(context: Context, root: File): Boolean {
    stopServer()
    return try {
        val server = LocalWebServer(LocalWebServer.DEFAULT_PORT, root)
        server.sharePassword = FmSettings.webPassword(context)
        server.hideSizes = FmSettings.hideSizes(context)
        server.start()
        ServerHolder.server = server
        true
    } catch (t: Throwable) {
        ServerHolder.server = null
        false
    }
}

private fun stopServer() {
    try {
        ServerHolder.server?.stop()
    } catch (_: Exception) {
        // bỏ qua
    } finally {
        ServerHolder.server = null
    }
}

private fun deleteRecursivelySafe(file: File): Boolean {
    return try {
        file.deleteRecursively()
    } catch (_: Exception) {
        false
    }
}

private fun renameSafe(file: File, newName: String): String? {
    val cleaned = newName.trim()
    if (cleaned.isEmpty() || cleaned.contains("/") || cleaned.contains("\\")) {
        return "Tên không hợp lệ"
    }
    val dest = File(file.parentFile, cleaned)
    if (dest.exists()) return "Tên đã tồn tại"
    return if (file.renameTo(dest)) null else "Đổi tên thất bại"
}

private fun createFolderSafe(parent: File, name: String): Boolean {
    val cleaned = name.trim()
    if (cleaned.isEmpty() || cleaned.contains("/") || cleaned.contains("\\")) return false
    val dir = File(parent, cleaned)
    return !dir.exists() && dir.mkdirs()
}

private fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase(Locale.US))
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Mở tệp bằng"))
    } catch (_: Exception) {
        toast(context, "Không ứng dụng nào mở được tệp này")
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun isFirstLaunchDone(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_FIRST_LAUNCH, false)
}

private fun markFirstLaunchDone(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_FIRST_LAUNCH, true)
        .apply()
}


private fun searchRecursive(root: File, q: String, limit: Int = 300): List<File> {
    val out = mutableListOf<File>()
    fun walk(dir: File) {
        if (out.size >= limit || !dir.isDirectory) return
        dir.listFiles().orEmpty().forEach { f ->
            if (out.size >= limit) return
            if (f.name.contains(q, ignoreCase = true)) out.add(f)
            if (f.isDirectory && !f.name.startsWith(".")) walk(f)
        }
    }
    walk(root)
    return out
}

private fun pasteClip(destDir: File): Int {
    val clip = ClipHolder.clip ?: return -1
    var n = 0
    clip.paths.forEach { path ->
        val src = File(path)
        if (!src.exists()) return@forEach
        val target = uniqueCopyName(destDir, src)
        try {
            if (clip.cut) {
                if (src.renameTo(target)) n++ else {
                    if (src.isDirectory) src.copyRecursively(target) else src.copyTo(target)
                    src.deleteRecursively(); n++
                }
            } else {
                if (src.isDirectory) src.copyRecursively(target) else src.copyTo(target)
                n++
            }
        } catch (_: Exception) {}
    }
    if (clip.cut) ClipHolder.clip = null
    return n
}

private fun shareFiles(context: android.content.Context, files: List<File>) {
    val existing = files.filter { it.exists() && it.isFile }
    if (existing.isEmpty()) {
        toast(context, "Không có tệp để chia sẻ")
        return
    }
    try {
        val uris = ArrayList<android.net.Uri>(existing.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ"))
    } catch (_: Exception) {
        toast(context, "Không chia sẻ được")
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}


