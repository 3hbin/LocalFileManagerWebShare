package com.localfm.app

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.DateRange
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
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.localfm.app.ui.theme.LocalFileManagerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
            val incoming = remember { IncomingFiles.fromIntent(this@MainActivity, intent) }
            LocalFileManagerTheme(darkTheme = dark.value) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileManagerApp(darkMode = dark.value, startFile = incoming, onDarkMode = {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileManagerApp(darkMode: Boolean, startFile: File? = null, onDarkMode: (Boolean) -> Unit) {
    val context = LocalContext.current
    var lang by remember { mutableStateOf(FmSettings.lang(context)) }
    fun tr(vi: String, en: String) = if (lang == "en") en else vi
    val storageRoot = remember { defaultStorageRoot() }

    val pickMode = remember { IncomingFiles.isPicker((context as? android.app.Activity)?.intent) }
    var currentDir by remember { mutableStateOf(storageRoot) }
    var volumeTick by remember { mutableIntStateOf(0) }
    val volumes = remember(volumeTick) { StorageVolumes.list(context) }
    VolumeListener { volumeTick++ }
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            SafGrants.save(context, uri)
            toast(context, "Đã lưu quyền truy cập thư mục này")
        }
    }
    var entries by remember { mutableStateOf(listFilesSafe(currentDir)) }
    var hasAllFilesAccess by remember { mutableStateOf(hasManageStoragePermission()) }
    var serverRunning by remember { mutableStateOf(ServerBridge.server?.isAlive == true) }
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
    var showMakeQr by remember { mutableStateOf(false) }
    var showCreateFile by remember { mutableStateOf(false) }
    var pendingUnzip by remember { mutableStateOf<File?>(null) }
    var unzipPass by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(startFile) }
    var htmlEdit by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var shortcut by remember { mutableStateOf("all") }
    var recursive by remember { mutableStateOf(true) }
    var webPass by remember { mutableStateOf(FmSettings.webPassword(context)) }
    var hideSizes by remember { mutableStateOf(FmSettings.hideSizes(context)) }
    var showMore by remember { mutableStateOf(false) }
    var latestTag by remember { mutableStateOf<String?>(null) }

    var locked by remember { mutableStateOf(AppLock.hasPin(context)) }
    var filter by remember { mutableStateOf("all") }
    var grid by remember { mutableStateOf(false) }
    var sortAsc by remember { mutableStateOf(true) }
    var showTrash by remember { mutableStateOf(false) }
    var showRoot by remember { mutableStateOf(false) }
    var showDriveRestore by remember { mutableStateOf(false) }
    var showManualEmail by remember { mutableStateOf(false) }
    var showLockSetup by remember { mutableStateOf(false) }
    var compareReq by remember { mutableStateOf<Pair<File, File>?>(null) }
    var compareResult by remember { mutableStateOf<String?>(null) }
    var zipJob by remember { mutableStateOf<ZipJob?>(null) }
    var openingFile by remember { mutableStateOf<File?>(null) }
    var fileFilter by remember { mutableStateOf(FileFilter()) }
    var showFilter by remember { mutableStateOf(false) }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var showAccess by remember { mutableStateOf(false) }
    var showNameLike by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showOpLog by remember { mutableStateOf(false) }
    var showLan by remember { mutableStateOf(false) }
    var showNfc by remember { mutableStateOf(false) }
    var showShareName by remember { mutableStateOf(false) }
    var showInstaller by remember { mutableStateOf(false) }
    var deviceCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(serverRunning) {
        while (true) {
            deviceCount = if (serverRunning) ServerBridge.server?.activeClients() ?: 0 else 0
            delay(2000)
        }
    }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    OpLog.init(context)
    ShareName.init(context)
    PowerMode.init(context)
    UpdatePrefs.init(context)
    PowerSaveListener()
    LaunchedEffect(Unit) {
        if (UpdatePrefs.enabled) {
            val info = withContext(Dispatchers.IO) { UpdateCheck.fetch() }
            if (info != null && AppUpdate.isNewer(info.tag, AppUpdate.installed(context))) updateInfo = info
        }
    }
    AutoClean.init(context)
    FolderLock.init(context)
    val appScope = rememberCoroutineScope()
    ReadOnlyLocks.init(context)
    var showDuplicates by remember { mutableStateOf(false) }


    var googleMail by remember { mutableStateOf(FmSettings.googleEmail(context)) }
    var loading by remember { mutableStateOf(false) }
    var accessDenied by remember { mutableStateOf(false) }
    var clipRev by remember { mutableStateOf(0) }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { TrashBin.purgeOld(context, 30) } }
    val googleSignLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode != android.app.Activity.RESULT_OK) {
            toast(context, "Google mã ${res.resultCode} — nhập email thủ công hoặc thêm SHA-1")
            showManualEmail = true
            return@rememberLauncherForActivityResult
        }
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(res.data)
        if (task.isSuccessful) {
            val acc = task.result
            val email = acc?.email.orEmpty()
            val name = acc?.displayName.orEmpty()
            if (email.isNotBlank()) {
                FmSettings.setGoogleEmail(context, email)
                googleMail = email
                toast(context, if (name.isBlank()) "Đã chọn $email" else "Xin chào $name")
            } else toast(context, "Không lấy được email")
        } else {
            val code = (task.exception as? com.google.android.gms.common.api.ApiException)?.statusCode
            toast(context, "Đăng nhập Google lỗi" + if (code != null) " (mã $code)" else "")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccess = hasManageStoragePermission()
        entries = listFilesSafe(currentDir, showHidden)
    }
    val dataTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            toast(context, "Chưa chọn thư mục. Android 13+ thường chặn Android/data trong bộ chọn.")
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            toast(context, "Đã cấp quyền SAF: ${uri.lastPathSegment}")
        } catch (e: Exception) {
            toast(context, "Không lưu quyền SAF")
        }
    }
    fun openAndroidDataSaf() {
        val start = try {
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Android/data"
            )
        } catch (_: Exception) { null }
        dataTreeLauncher.launch(start)
    }

    fun refresh() {
        hasAllFilesAccess = hasManageStoragePermission()
        localIp = NetworkUtils.getLocalWifiIpv4(context)
        serverRunning = ServerBridge.server?.isAlive == true
        refreshTick++
    }

    LaunchedEffect(currentDir, showHidden, refreshTick) {
        loading = true
        val listed = withContext(Dispatchers.IO) { listDirDetailed(currentDir, showHidden, context) }
        entries = listed.files
        accessDenied = listed.denied
        ServerBridge.server?.shareRoot = currentDir
        ServerBridge.server?.sharePassword = FmSettings.webPassword(context)
        ServerBridge.server?.hideSizes = FmSettings.hideSizes(context)
        loading = false
    }

    var deepResults by remember { mutableStateOf<List<File>?>(null) }
    LaunchedEffect(query, recursive, currentDir, shortcut) {
        if (!recursive || shortcut != "all" || query.length < 2) {
            deepResults = null
            return@LaunchedEffect
        }
        delay(280)
        deepResults = withContext(Dispatchers.IO) { searchRecursive(currentDir, query) }
    }
    val visibleFiles = remember(entries, query, sortMode, shortcut, deepResults, recursive, fileFilter) {
        val base = when (shortcut) {
            "fav" -> FmSettings.favorites(context).map { File(it) }.filter { it.exists() }
            "recent" -> FmSettings.recents(context).map { File(it) }.filter { it.exists() }
            else -> entries
        }
        val searched = when {
            query.isBlank() -> base
            recursive && shortcut == "all" && query.length >= 2 -> deepResults ?: base.filter { it.name.contains(query, true) }
            else -> base.filter { it.name.contains(query, ignoreCase = true) }
        }
        val typed = searched.filter { f ->
            fileFilter.matches(f) && when (filter) {
                "img" -> isImage(f)
                "vid" -> f.extension.lowercase() in setOf("mp4","mkv","webm","avi","mov")
                "aud" -> f.extension.lowercase() in setOf("mp3","wav","aac","ogg","m4a","flac")
                "doc" -> f.extension.lowercase() in setOf("pdf","txt","doc","docx","xls","xlsx")
                "zip" -> f.extension.lowercase() in setOf("zip","rar","7z")
                else -> true
            }
        }
        val sorted = sortFilesFast(typed, sortMode)
        if (sortAsc) sorted else sorted.reversed()
    }

    val shareBase = remember(localIp, serverRunning) {
        if (!localIp.isNullOrBlank()) "http://$localIp:${FmSettings.port(context).coerceIn(1024, 65535)}" else null
    }
    // Link hiển thị: có tên đẹp thì dùng /tên, không thì dùng địa chỉ IP
    val shareUrl = shareBase?.let { ShareName.url(it) }

    // Dừng server khi Activity bị huỷ hoàn toàn
    DisposableEffect(Unit) {
        onDispose { /* giữ server khi xoay màn hình */ }
    }

    // Lên thư mục cha tới gốc của ổ đang mở (bộ nhớ trong hoặc thẻ nhớ)
    val volRoot = StorageVolumes.rootOf(currentDir, volumes) ?: storageRoot
    val canGoUp = currentDir.canonicalPath != volRoot.canonicalPath &&
        currentDir.canonicalPath.startsWith(volRoot.canonicalPath)

    // Thư mục biến mất (ví dụ thẻ nhớ bị tháo): quay về bộ nhớ trong và báo cho người dùng
    LaunchedEffect(currentDir.absolutePath, volumeTick) {
        if (!currentDir.exists()) {
            currentDir = storageRoot
            toast(context, "Không còn truy cập được thư mục (thẻ nhớ có thể đã tháo ra). Đã về bộ nhớ trong.")
        }
    }

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
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Sắp xếp")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("Theo tên") }, onClick = { sortMode = SortMode.NAME; showSortMenu = false })
                        DropdownMenuItem(text = { Text("Theo ngày") }, onClick = { sortMode = SortMode.DATE; showSortMenu = false })
                        DropdownMenuItem(text = { Text("Theo dung lượng") }, onClick = { sortMode = SortMode.SIZE; showSortMenu = false })
                        DropdownMenuItem(text = { Text("Ảnh") }, onClick = { filter = "img"; showSortMenu = false })
                        DropdownMenuItem(text = { Text("Video") }, onClick = { filter = "vid"; showSortMenu = false })
                        DropdownMenuItem(text = { Text("Tất cả loại") }, onClick = { filter = "all"; showSortMenu = false })
                        DropdownMenuItem(text = { Text(if (sortAsc) "Đảo giảm dần" else "Đảo tăng dần") }, onClick = { sortAsc = !sortAsc; showSortMenu = false })
                        DropdownMenuItem(text = { Text(if (showHidden) "Ẩn tệp chấm" else "Hiện tệp ẩn") }, onClick = { showHidden = !showHidden; showSortMenu = false })
                    }
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
                    }
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Thêm")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { BottomOverlayHost(onRefresh = { entries = listFilesSafe(currentDir, showHidden) }) },
        floatingActionButton = {
            if (hasAllFilesAccess && !SystemGuard.isProtected(currentDir)) {
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
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (loading) {
                    ExpressiveLinearProgress(modifier = Modifier.fillMaxWidth())
                }
            }
            if (volumes.size > 1) {
                VolumeChips(
                    items = volumes,
                    current = currentDir,
                    onPick = { currentDir = it.root },
                    onNotMounted = { toast(context, "${it.name} chưa được gắn") },
                    onNeedAccess = { safLauncher.launch(null) }
                )
            }
            PathBar(path = currentDir.absolutePath)
            if (!landscape) StorageBar(dir = currentDir)

            ShortcutRow(
                current = shortcut,
                onPick = { shortcut = it },
                onPictures = {
                    shortcut = "pic"
                    filter = "all"
                    currentDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                },
                onDownload = {
                    shortcut = "dl"
                    filter = "all"
                    currentDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                },
                onDocs = {
                    shortcut = "docs"
                    filter = "all"
                    currentDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                },
                onDcim = {
                    shortcut = "dcim"
                    filter = "all"
                    currentDir = File(android.os.Environment.getExternalStorageDirectory(), "DCIM")
                }
            )

            if (!landscape) ServerControlPanel(
                running = serverRunning,
                ip = localIp,
                devices = deviceCount,
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
                        stopServer(context)
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

            SearchRow(
                query = query,
                onQuery = { query = it },
                count = visibleFiles.size,
                recursive = recursive,
                onRecursive = { recursive = it },
                compact = landscape
            )
            if (selecting || selected.isNotEmpty() || ClipHolder.clip != null) {
                SelectionBar(
                    count = selected.size,
                    hasClip = (clipRev >= 0 && ClipHolder.clip != null),
                    canWrite = !SystemGuard.isProtected(currentDir),
                    onClear = { selected = emptySet(); selecting = false },
                    onDelete = {
                        val trashed = mutableListOf<Pair<File, File>>()
                        var skipped = 0
                        selected.map { File(it) }.forEach { f ->
                            if (ReadOnlyLocks.isLocked(f)) skipped++
                            else TrashBin.moveToTrashFile(context, f)?.let { t -> trashed.add(t to (f.parentFile ?: currentDir)) }
                        }
                        if (trashed.isNotEmpty()) UndoStack.push("Đã xoá ${trashed.size} mục") {
                            trashed.forEach { (t, p) -> TrashBin.restore(context, t, p) }
                        }
                        if (skipped > 0) toast(context, "Bỏ qua $skipped mục đang khoá chỉ đọc")
                        selected = emptySet(); selecting = false
                        entries = listFilesSafe(currentDir, showHidden)
                    },
                    onCopy = {
                        ClipHolder.clip = ClipBoard(selected.toList(), false)
                        clipRev++
                        selected = emptySet()
                        selecting = false
                        toast(context, "Đã copy. Vào thư mục đích rồi bấm Dán")
                    },
                    onCut = {
                        ClipHolder.clip = ClipBoard(selected.toList(), true)
                        clipRev++
                        selected = emptySet()
                        selecting = false
                        toast(context, "Đã cắt. Vào thư mục đích rồi bấm Dán")
                    },
                    onPaste = {
                        val clip = ClipHolder.clip
                        val dest = currentDir
                        if (clip == null) toast(context, "Không có gì để dán")
                        else {
                            val task = TaskCenter.start("Dán ${clip.paths.size} mục")
                            appScope.launch {
                                val out = withContext(Dispatchers.IO) {
                                    runPaste(
                                        context, clip, dest,
                                        { p, d -> appScope.launch(Dispatchers.Main) { task.progress = p; task.detail = d } },
                                        { task.cancelled }
                                    )
                                }
                                TaskCenter.finish(task)
                                if (out.ok > 0) UndoStack.push("Đã dán ${out.ok} mục") { out.undo.reversed().forEach { it() } }
                                toast(context, when {
                                    out.cancelled -> "Đã huỷ dán"
                                    out.fail > 0 -> "Dán ${out.ok} mục, bỏ qua ${out.fail} mục (khoá chỉ đọc hoặc lỗi)"
                                    else -> "Đã dán ${out.ok} mục vào ${dest.name.ifBlank { dest.absolutePath }}"
                                })
                                clipRev++
                                selected = emptySet()
                                selecting = false
                                refreshTick++
                                entries = listFilesSafe(currentDir, showHidden)
                            }
                        }
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
                    },
                    onSelectAll = {
                        selected = visibleFiles.map { it.absolutePath }.toSet()
                        selecting = true
                    }
                )
            }

            Box(Modifier.weight(1f)) {
            FileList(
                files = visibleFiles,
                accessDenied = accessDenied,
                dirPath = currentDir.absolutePath,
                selected = selected,
                selecting = selecting,
                onToggleSelect = { file ->
                    selecting = true
                    selected = if (file.absolutePath in selected) selected - file.absolutePath else selected + file.absolutePath
                },
                onOpen = { file ->
                    if (file.isDirectory) {
                        currentDir = file
                    } else if (selecting) {
                        selected = if (file.absolutePath in selected) selected - file.absolutePath else selected + file.absolutePath
                    } else if (pickMode) {
                        if (!IncomingFiles.returnPicked(context, file)) {
                            toast(context, "Không gửi được tệp")
                        }
                    } else {
                        FmSettings.addRecent(context, file.absolutePath)
                        openingFile = file
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
                    if (it.isDirectory) currentDir = it else openingFile = it
                    menuFor = null
                },
                grid = gridMode,
                onZip = { f, kind -> zipJob = ZipJob(kind, listOf(f)); menuFor = null },
                onDuplicate = {
                    val ok = duplicateSafe(it)
                    toast(context, if (ok) "Đã tạo bản sao" else "Không sao chép được")
                    menuFor = null
                    refreshTick++
                }
            )
            }
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


    if (locked) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Khoá ứng dụng") },
            text = {
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (AppLock.check(context, pin)) locked = false
                    else toast(context, "Sai PIN")
                }) { Text("Mở") }
            }
        )
    }

    LaunchedEffect(showMore) {
        if (showMore) {
            latestTag = withContext(Dispatchers.IO) { AppUpdate.latestTag() }
        }
    }
    if (showMore) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            title = { Text("Thêm") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Phiên bản v${AppUpdate.installed(context)}")
                    val remote = latestTag
                    if (!remote.isNullOrBlank()) {
                        val newer = AppUpdate.isNewer(remote, AppUpdate.installed(context))
                        Text(if (newer) "Có bản mới: $remote" else "Đang ở bản mới nhất ($remote)",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Thiết bị: ${DeviceInfo.name()}", style = MaterialTheme.typography.bodySmall)
                    MoreItem(Icons.Outlined.SystemUpdate, tr("Cập nhật ứng dụng", "Check for updates")) {
                        try { AppUpdate.openStore(context) } catch (_: Exception) {
                            toast(context, "Không mở được trang cập nhật")
                        }
                    }
                    if (googleMail.isNotBlank()) {
                        GoogleProfileRow(email = googleMail)
                    }
                    MoreItem(Icons.Outlined.Folder, tr("Về bộ nhớ trong", "Internal storage")) {
                        currentDir = storageRoot; showMore = false
                    }
                    MoreItem(Icons.Outlined.Settings, tr("Cài đặt", "Settings")) { showSettings = true; showMore = false }
                    MoreItem(Icons.Outlined.Info, tr("Chính sách quyền riêng tư", "Privacy Policy")) {
                        showPrivacy = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.HelpOutline, tr("Hướng dẫn", "Guide")) { showGuide = true; showMore = false }
                    MoreItem(Icons.Outlined.Lock, tr("App lock (PIN)", "App lock (PIN)")) { showLockSetup = true; showMore = false }
                    MoreItem(Icons.Outlined.DeleteSweep, tr("Dọn rác (cache)", "Clear cache")) {
                        showMore = false
                        Thread {
                            val msg = JunkCleaner.clean(context)
                            android.os.Handler(android.os.Looper.getMainLooper()).post { toast(context, msg) }
                        }.start()
                    }
                    MoreItem(Icons.Outlined.RestoreFromTrash, tr("Thùng rác", "Trash")) { showTrash = true; showMore = false }
                    MoreItem(Icons.Outlined.Security, "ROOT") { showRoot = true; showMore = false }
                    MoreItem(Icons.Outlined.FolderOpen, tr("Mở Android/data (SAF)", "Open Android/data (SAF)")) {
                        openAndroidDataSaf(); showMore = false
                    }
                    if (RootAccess.systemFiles(context)) {
                        MoreItem(Icons.Outlined.Folder, "Gốc hệ thống /") {
                            currentDir = File("/"); showMore = false
                        }
                    }
                    MoreItem(Icons.Outlined.CloudUpload, "Sao lưu cấu hình + tên máy") {
                        val backupTask = TaskCenter.start("Sao lưu lên Drive")
                        Thread {
                            val msg = DriveSync.uploadBackup(context)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                toast(context, msg)
                                TaskCenter.finish(backupTask)
                            }
                        }.start()
                        val dir = BackupKit.backupFolder(context)
                        toast(context, "Đã lưu ${dir.absolutePath}")
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.CloudDownload, "Khôi phục từ Google Drive") {
                        showDriveRestore = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.Email, "Nhập email thủ công") {
                        showManualEmail = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.AccountCircle, "Đăng nhập Google") {
                        try {
                            val gso = DriveSync.signInOptionsDrive()
                            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                            googleSignLauncher.launch(client.signInIntent)
                            showMore = false
                        } catch (_: Exception) {
                            toast(context, "Không mở được đăng nhập Google")
                        }
                    }
                    if (!SystemGuard.isProtected(currentDir)) {
                        MoreItem(Icons.Outlined.NoteAdd, "Tạo tệp theo đuôi") {
                            showCreateFile = true; showMore = false
                        }
                    }
                    MoreItem(Icons.Outlined.Archive, "Nén mục đã chọn") {
                        val src = selected.map { File(it) }.filter { it.exists() }
                        if (src.isEmpty()) toast(context, "Chọn tệp hoặc thư mục để nén")
                        else zipJob = ZipJob(ZipKind.COMPRESS, src)
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.ContentCopy, "Tìm file trùng") {
                        showDuplicates = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.FilterList, if (fileFilter.active) "Bộ lọc (đang bật)" else "Bộ lọc tìm kiếm") {
                        showFilter = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.GridView, if (gridMode) "Tắt chế độ lưới" else "Chế độ lưới") {
                        gridMode = !gridMode; showMore = false
                    }
                    if (FolderLock.isPinned(currentDir)) {
                        MoreItem(Icons.Outlined.Lock, "Gỡ khoá PIN thư mục này") {
                            FolderLock.removePin(context, currentDir)
                            toast(context, "Đã gỡ khoá PIN")
                            showMore = false
                        }
                    } else {
                        MoreItem(Icons.Outlined.Lock, "Khoá PIN thư mục này") {
                            showPinSetup = true; showMore = false
                        }
                    }
                    MoreItem(Icons.Outlined.DateRange, "Dòng thời gian ảnh/video") {
                        showTimeline = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.Folder, "Đặt thư mục này làm nguồn đồng bộ") {
                        SyncState.source = currentDir
                        toast(context, "Đã đặt nguồn: ${currentDir.name.ifBlank { currentDir.absolutePath }}")
                        showMore = false
                    }
                    if (SyncState.source != null && SyncState.source != currentDir) {
                        MoreItem(Icons.Outlined.Sync, "Đồng bộ với nguồn đã đặt") {
                            showSync = true; showMore = false
                        }
                    }
                    MoreItem(Icons.Outlined.Security, "Quyền truy cập thư mục (SAF)") {
                        showAccess = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.ContentCopy, "Tìm file tên gần giống") {
                        showNameLike = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.Edit, "Mẫu tên file khi tạo mới") {
                        showTemplates = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.Android, "Trình cài APK / XAPK") { showInstaller = true; showMore = false }
                    MoreItem(Icons.Outlined.Edit, "Tên link Web Share") { showShareName = true; showMore = false }
                    MoreItem(Icons.Outlined.History, "Lịch sử thao tác") { showOpLog = true; showMore = false }
                    MoreItem(Icons.Outlined.Share, "Gửi nhanh qua mạng nội bộ") {
                        if (selected.none { File(it).isFile }) toast(context, "Chọn tệp để gửi")
                        else showLan = true
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.Link, "Ghi link Web Share vào thẻ NFC") {
                        if (shareUrl.isNullOrBlank() || !serverRunning) toast(context, "Hãy bật Web Share trước")
                        else showNfc = true
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.Settings, if (PowerMode.manual) "Tắt chế độ tiết kiệm" else "Bật chế độ tiết kiệm") {
                        PowerMode.setManual(context, !PowerMode.manual)
                        toast(context, if (PowerMode.manual) "Đã bật tiết kiệm: không tạo thumbnail" else "Đã tắt chế độ tiết kiệm")
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.SystemUpdate, if (UpdatePrefs.enabled) "Tắt tự kiểm tra cập nhật" else "Bật tự kiểm tra cập nhật") {
                        UpdatePrefs.set(context, !UpdatePrefs.enabled)
                        toast(context, if (UpdatePrefs.enabled) "Sẽ tự kiểm tra cập nhật khi mở app" else "Đã tắt tự kiểm tra cập nhật")
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.Schedule, if (AutoClean.enabled) "Tắt dọn tự động hàng tuần" else "Bật dọn tự động hàng tuần") {
                        AutoClean.set(context, !AutoClean.enabled)
                        toast(context, if (AutoClean.enabled) "Đã bật dọn tự động hàng tuần" else "Đã tắt dọn tự động")
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.DriveFileRenameOutline, "Đổi đuôi thành .bak") {
                        val one = selected.map { File(it) }.firstOrNull()
                        if (one==null || one.isDirectory) toast(context, "Chọn 1 tệp để đổi đuôi")
                        else {
                            val neu = File(one.parentFile, one.nameWithoutExtension + ".bak")
                            if (one.renameTo(neu)) toast(context, "Đã đổi thành ${neu.name}")
                            else toast(context, "Không đổi được (có thể đã tồn tại ${neu.name})")
                            entries = listFilesSafe(currentDir, showHidden)
                        }
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.QrCode2, "Lưu / xem QR Web Share") {
                        if (shareUrl.isNullOrBlank() || !serverRunning) toast(context, "Hãy bật Web Share trước")
                        else showQr = true
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.QrCode2, "Tạo mã QR") {
                        showMakeQr = true; showMore = false
                    }
                    MoreItem(Icons.Outlined.Android, "Tìm APK đã build") {
                        val found = findBuiltApks(currentDir)
                        toast(context, if (found.isEmpty()) "Không thấy APK trong thư mục này (điện thoại không chạy Gradle)" else found.joinToString { it.name })
                        if (found.isNotEmpty()) currentDir = found.first().parentFile ?: currentDir
                        showMore = false
                    }
                    MoreItem(Icons.Outlined.CompareArrows, "So sánh 2 tệp đã chọn") {
                        val two = selected.map { File(it) }.take(2)
                        when {
                            two.size < 2 -> toast(context, "Chọn đúng 2 tệp để so sánh")
                            two.any { it.isDirectory } -> toast(context, "Chỉ so sánh tệp, không so sánh thư mục")
                            else -> compareReq = two[0] to two[1]
                        }
                        showMore = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMore = false }) { Text("Đóng") } }
        )
    }

    if (showLockSetup) {
        var p1 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLockSetup = false },
            title = { Text("Đặt PIN") },
            text = { OutlinedTextField(value = p1, onValueChange = { p1 = it }, label = { Text("PIN mới") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (p1.length >= 4) { AppLock.setPin(context, p1); showLockSetup = false; toast(context, "Đã khoá app") }
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { AppLock.clear(context); showLockSetup = false }) { Text("Tắt khoá") } }
        )
    }

    if (showManualEmail) {
        ManualEmailDialog(
            initial = googleMail,
            onDismiss = { showManualEmail = false },
            onSave = { mail ->
                FmSettings.setGoogleEmail(context, mail)
                googleMail = mail
                showManualEmail = false
                toast(context, "Đã lưu $mail")
            }
        )
    }
    if (showDriveRestore) {
        DriveRestoreDialog(onDismiss = { showDriveRestore = false })
    }
    if (showRoot) {
        RootSettingsDialog(onDismiss = { showRoot = false })
    }
    if (showTrash) {
        TrashSwipeDialog(
            items = TrashBin.list(context),
            reload = { TrashBin.list(context) },
            onRestore = { f ->
                val ok = TrashBin.restore(context, f, currentDir)
                toast(context, if (ok) "Đã khôi phục ${f.name.substringAfter('_')}" else "Không khôi phục được")
                refreshTick++
                ok
            },
            onDeleteForever = { f ->
                val ok = TrashBin.deleteForever(f)
                toast(context, if (ok) "Đã xóa hẳn" else "Không xóa được")
                ok
            },
            onDismiss = { showTrash = false }
        )
    }

    pendingDelete?.let { target ->
        ConfirmDeleteDialog(
            file = target,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                if (ReadOnlyLocks.isLocked(target)) toast(context, "Đang khoá chỉ đọc, không xoá")
                else {
                    val parent = target.parentFile ?: currentDir
                    val trashed = TrashBin.moveToTrashFile(context, target)
                    toast(context, if (trashed != null) "Đã đưa vào thùng rác ${target.name}" else "Không xoá được")
                    if (trashed != null) UndoStack.push("Đã xoá ${target.name}") { TrashBin.restore(context, trashed, parent) }
                }
                entries = listFilesSafe(currentDir, showHidden)
            }
        )
    }

    pendingRename?.let { target ->
        RenameDialog(
            file = target,
            onDismiss = { pendingRename = null },
            onConfirm = { newName ->
                if (ReadOnlyLocks.isLocked(target)) toast(context, "Đang khoá chỉ đọc, không đổi tên")
                else {
                    val result = renameSafe(target, newName)
                    toast(context, result ?: "Đã đổi tên")
                    if (result == null) {
                        val renamed = File(target.parentFile, newName.trim())
                        UndoStack.push("Đã đổi tên thành ${renamed.name}") { renamed.renameTo(target) }
                    }
                }
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

    if (showCreateFile) {
        CreateFileDialog(dir = currentDir, onDismiss = { showCreateFile = false }, onCreated = {
            entries = listFilesSafe(currentDir, showHidden)
            showCreateFile = false
        })
    }
    if (showMakeQr) {
        MakeQrDialog(
            initial = selected.firstOrNull() ?: shareUrl ?: currentDir.absolutePath,
            saveDir = currentDir,
            onDismiss = { showMakeQr = false }
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
            isHtml(f) && htmlEdit -> CodeEditorDialog(f) { htmlEdit = false }
            isHtml(f) -> HtmlPreviewDialog(f, onDismiss = { previewFile = null; htmlEdit = false }, onEdit = { htmlEdit = true })
            f.extension.lowercase() == "zip" -> ZipPeekDialog(
                onExtract = { pw -> previewFile = null; zipJob = ZipJob(ZipKind.EXTRACT, listOf(f), pw) },
                file = f,
                onDismiss = { previewFile = null }
            )
            isImage(f) -> ImagePreviewDialog(f) { previewFile = null }
            isMedia(f) -> MediaPreviewDialog(f) { previewFile = null }
            isEditable(f) -> CodeEditorDialog(f) { previewFile = null }
            isPdf(f) -> PdfViewerDialog(f) { previewFile = null }
            else -> previewFile = null
        }
    }

    LaunchedEffect(compareReq) {
        val pair = compareReq ?: return@LaunchedEffect
        compareResult = withContext(Dispatchers.IO) { FileCompare.compare(pair.first, pair.second) }
        compareReq = null
    }
    compareResult?.let { res ->
        AlertDialog(
            onDismissRequest = { compareResult = null },
            title = { Text("So sánh 2 tệp") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(res, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { compareResult = null }) { Text("Đóng") } }
        )
    }

    // Loading M3 khi mở bất kỳ loại tệp nào: đọc thử tệp, rồi mở xem trước hoặc chuyển cho app khác
    openingFile?.let { f ->
        LaunchedEffect(f.absolutePath) {
            val started = System.currentTimeMillis()
            val readable = withContext(Dispatchers.IO) {
                try {
                    if (f.isFile) f.inputStream().use { it.read(ByteArray(8192)) }
                    true
                } catch (_: Exception) { false }
            }
            val wait = 250L - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            if (!readable) {
                toast(context, "Không đọc được tệp")
            } else {
                when {
                    isHtml(f) -> openHtmlInBrowser(context, f)
                    f.extension.lowercase() == "zip" -> previewFile = f
                    isImage(f) || isMedia(f) || isEditable(f) || isPdf(f) -> previewFile = f
                    else -> openFile(context, f)
                }
            }
            openingFile = null
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Đang mở tệp") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    ExpressiveCircularProgress()
                    Spacer(Modifier.height(12.dp))
                    Text(f.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {}
        )
    }

    ToolRequests.qrFile?.let { f -> QrScanDialog(file = f, onDismiss = { ToolRequests.qrFile = null }) }
    ToolRequests.editFile?.let { f ->
        ImageEditDialog(
            file = f,
            onSaved = { entries = listFilesSafe(currentDir, showHidden) },
            onDismiss = { ToolRequests.editFile = null }
        )
    }
    if (showSync && SyncState.source != null) {
        SyncDialog(
            a = SyncState.source!!,
            b = currentDir,
            onDone = { entries = listFilesSafe(currentDir, showHidden) },
            onDismiss = { showSync = false }
        )
    }
    if (showTimeline) {
        TimelineDialog(
            root = currentDir,
            onOpen = { showTimeline = false; openingFile = it },
            onDismiss = { showTimeline = false }
        )
    }

    if (showInstaller) InstallerDialog(onDismiss = { showInstaller = false })
    if (showShareName) ShareNameDialog(baseUrl = shareBase.orEmpty(), onDismiss = { showShareName = false })
    updateInfo?.let { u -> UpdateDialog(info = u, onDismiss = { updateInfo = null }) }
    if (showOpLog) OpLogDialog(onDismiss = { showOpLog = false })
    if (showLan) LanShareDialog(files = selected.map { File(it) }.filter { it.isFile }, onDismiss = { showLan = false })
    shareUrl?.let { url -> if (showNfc) NfcWriteDialog(url = url, onDismiss = { showNfc = false }) }
    ToolRequests.linkFile?.let { f ->
        TempLinkDialog(file = f, baseUrl = shareUrl.orEmpty(), onDismiss = { ToolRequests.linkFile = null })
    }
    ToolRequests.checksumFile?.let { f -> ChecksumDialog(file = f, onDismiss = { ToolRequests.checksumFile = null }) }
    if (showAccess) AccessDialog(onDismiss = { showAccess = false })
    if (showNameLike) {
        NameLikeDialog(root = currentDir, onOpen = { showNameLike = false; openingFile = it }, onDismiss = { showNameLike = false })
    }
    if (showTemplates) TemplatesDialog(onDismiss = { showTemplates = false })

    val pinBlock = FolderLock.blockingFolder(currentDir)
    if (pinBlock != null) {
        PinUnlockDialog(
            folder = File(pinBlock),
            onCancel = { currentDir = File(pinBlock).parentFile ?: currentDir }
        )
    }
    if (showPinSetup) {
        PinSetupDialog(
            folder = currentDir,
            onDone = { showPinSetup = false; toast(context, "Đã khoá thư mục bằng PIN") },
            onCancel = { showPinSetup = false }
        )
    }

    zipJob?.let { job ->
        ZipJobDialog(
            job = job,
            onFinished = { entries = listFilesSafe(currentDir, showHidden) },
            onDismiss = { zipJob = null; entries = listFilesSafe(currentDir, showHidden) }
        )
    }
    if (showFilter) {
        FilterDialog(
            initial = fileFilter,
            onApply = { fileFilter = it; showFilter = false },
            onDismiss = { showFilter = false }
        )
    }
    if (showDuplicates) {
        DuplicateDialog(
            root = currentDir,
            onChanged = { entries = listFilesSafe(currentDir, showHidden) },
            onDismiss = { showDuplicates = false }
        )
    }

    if (showPrivacy) {
        PrivacyPolicyDialog(en = lang == "en", onDismiss = { showPrivacy = false })
    }
    if (showSettings) {
        SettingsDialog(
            dark = darkMode,
            lang = lang,
            password = webPass,
            hideSizes = hideSizes,
            onDark = onDarkMode,
            onLang = { lang = it; FmSettings.setLang(context, it) },
            onPassword = {
                webPass = it
                FmSettings.setWebPassword(context, it)
                ServerBridge.server?.sharePassword = it
            },
            onHideSizes = {
                hideSizes = it
                FmSettings.setHideSizes(context, it)
                ServerBridge.server?.hideSizes = it
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
        text = "Tổng ${formatSize(total)} · đã dùng ${formatSize(total - usable)} · còn trống ${formatSize(usable)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
    )
}

@Composable
private fun SearchRow(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    recursive: Boolean,
    onRecursive: (Boolean) -> Unit,
    compact: Boolean = false
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = if (compact) 0.dp else 4.dp),
        singleLine = true,
        placeholder = { Text(if (recursive && query.length >= 2) "Tìm cả máy…" else "Tìm thư mục này") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                TextButton(onClick = { onRecursive(!recursive) }) {
                    Text(if (recursive && query.length >= 2) "Cả máy" else "Thư mục", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        shape = RoundedCornerShape(14.dp)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        dismissButton = {
            val ctx = LocalContext.current
            TextButton(onClick = {
                try {
                    val bmp = QrCodeUtils.makeBitmap(url) ?: return@TextButton
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    dir.mkdirs()
                    val f = java.io.File(dir, "webshare_qr.png")
                    java.io.FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    toast(ctx, "Đã lưu ${f.absolutePath}")
                } catch (_: Exception) { toast(ctx, "Không lưu được QR") }
            }) { Text("Lưu ảnh QR") }
        }
    )
}

@Composable
private fun PathBar(path: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * Bảng điều khiển server: công tắc lớn, IP local, URL truy cập từ máy tính.
 */
@Composable
private fun ServerControlPanel(
    running: Boolean,
    ip: String?,
    devices: Int = 0,
    canStart: Boolean,
    onToggle: (Boolean) -> Unit,
    onCopyUrl: () -> Unit,
    onShowQr: () -> Unit
) {
    val ctx = LocalContext.current
    val url = if (!ip.isNullOrBlank()) ShareName.url("http://$ip:${FmSettings.port(ctx).coerceIn(1024, 65535)}") else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                        text = if (running) {
                            val n = if (devices == 1) "1 thiết bị" else "$devices thiết bị"
                            "Cổng ${FmSettings.port(ctx)} · " + (if (devices > 0) n else "chưa có thiết bị kết nối")
                        } else "Máy chủ đang tắt",
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

            if (running) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(Modifier.height(8.dp))

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
    accessDenied: Boolean = false,
    dirPath: String = "",
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
    onZip: (File, ZipKind) -> Unit,
    grid: Boolean = false,
    onDuplicate: (File) -> Unit
) {
    if (grid) {
        FileGrid(files = files, selected = selected, onOpen = onOpen, onToggleSelect = onToggleSelect)
        return
    }
    if (files.isEmpty()) {
        val blocked = accessDenied || isAndroidRestrictedPath(dirPath)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (blocked) Icons.Outlined.Lock else Icons.Outlined.FolderOpen,
                contentDescription = if (blocked) "Truy cập từ chối" else "Thư mục trống",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (blocked) "Truy cập từ chối" else "Không có tệp nào",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            if (blocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android chặn thư mục này. Bật Shizuku hoặc ROOT rồi mở lại. Quyền mọi tệp không đủ.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                onZip = { kind -> onZip(file, kind) },
                onDuplicate = { onDuplicate(file) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onZip: (ZipKind) -> Unit,
    onDuplicate: () -> Unit
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onToggleSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        }
        val downloading = isDownloadingFile(file)
        val pkg = packageIdOfDir(file)
        val apkLike = file.extension.lowercase() in setOf("apk", "xapk", "apks")
        val lastMod = file.lastModified()
        val thumbState by produceState<android.graphics.Bitmap?>(null, file.absolutePath, lastMod, downloading, pkg) {
            value = withContext(Dispatchers.IO) {
                when {
                    downloading -> null
                    pkg != null -> ThumbCache.appPkg(ctx, pkg)
                    isImage(file) -> ThumbCache.image(file)
                    file.extension.lowercase() in setOf("mp4", "mkv", "webm", "3gp") -> ThumbCache.video(file)
                    apkLike -> ThumbCache.apk(ctx, file)
                    else -> null
                }
            }
        }
        val thumb = thumbState
        val pkgLabel by produceState<String?>(null, pkg) {
            value = pkg?.let { withContext(Dispatchers.IO) { ThumbCache.appLabel(ctx, it) } }
        }
        val subtitle by produceState("", file.absolutePath, lastMod, downloading) {
            value = withContext(Dispatchers.IO) { subtitleFor(file) }
        }
        when {
            downloading -> Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(typeIconRes(file)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
                ExpressiveCircularProgress(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            thumb != null -> Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = file.name,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            else -> Image(
                painter = painterResource(typeIconRes(file)),
                contentDescription = file.name,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = (if (!pkgLabel.isNullOrBlank()) "$pkgLabel  ·  $subtitle" else subtitle) +
                    if (ReadOnlyLocks.isLocked(file)) "  ·  Chỉ đọc" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            if (!file.isDirectory) {
                DropdownMenuItem(
                    text = { Text("Tính checksum") },
                    onClick = { ToolRequests.checksumFile = file; onDismissMenu() },
                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Tạo link tạm thời") },
                    onClick = { ToolRequests.linkFile = file; onDismissMenu() },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) }
                )
            }
            if (isImage(file)) {
                DropdownMenuItem(
                    text = { Text("Đọc mã QR") },
                    onClick = { ToolRequests.qrFile = file; onDismissMenu() },
                    leadingIcon = { Icon(Icons.Outlined.QrCode2, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Chỉnh sửa ảnh") },
                    onClick = { ToolRequests.editFile = file; onDismissMenu() },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
                )
            }
            if (file.extension.lowercase() == "zip") {
                DropdownMenuItem(
                    text = { Text("Giải nén tại đây") },
                    onClick = { onZip(ZipKind.EXTRACT); onDismissMenu() },
                    leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Kiểm tra ZIP") },
                    onClick = { onZip(ZipKind.TEST); onDismissMenu() },
                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text("Nén") },
                onClick = { onZip(ZipKind.COMPRESS); onDismissMenu() },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (ReadOnlyLocks.isLocked(file)) "Bỏ khoá chỉ đọc" else "Khoá chỉ đọc") },
                onClick = { ReadOnlyLocks.toggle(ctx, file); onDismissMenu() },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Chi tiết") },
                onClick = onDetails,
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(if (FmSettings.isFavorite(ctx, file.absolutePath)) "Bỏ yêu thích" else "Yêu thích") },
                onClick = {
                    FmSettings.toggleFavorite(ctx, file.absolutePath)
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Sao chép đường dẫn") },
                onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("path", file.absolutePath))
                    android.widget.Toast.makeText(ctx, file.absolutePath, android.widget.Toast.LENGTH_SHORT).show()
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Mở bằng ứng dụng khác") },
                onClick = {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(intent, "Mở bằng"))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(ctx, "Không mở được", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Chia sẻ") },
                onClick = {
                    shareFiles(ctx, listOf(file))
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Nén ZIP") },
                onClick = {
                    val dest = File(file.parentFile, file.nameWithoutExtension + ".zip")
                    val ok = ZipUtils.zipTo(listOf(file), dest)
                    android.widget.Toast.makeText(ctx, if (ok) "Đã nén" else "Lỗi nén", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Giải nén") },
                onClick = {
                    val dest = File(file.parentFile, file.nameWithoutExtension)
                    val err = ZipUtils.unzip(file, dest)
                    val msg = when (err) {
                        "ZIP_PASSWORD" -> "ZIP có mật khẩu — bấm vào file ZIP rồi nhập mật khẩu"
                        null -> "Đã giải nén"
                        else -> err
                    }
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                    onDismissMenu()
                },
                leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) }
            )
            if (!ReadOnlyLocks.isLocked(file)) {
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
                val sizeText by produceState("…", file.absolutePath) {
                    value = withContext(Dispatchers.IO) {
                        if (file.isDirectory) formatSize(FolderSize.of(file)) else formatSize(file.length())
                    }
                }
                DetailLine("Kích thước", sizeText)
                DetailLine("Thiết bị", DeviceInfo.name())
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
        FilterChip(selected = current == "all", onClick = { onPick("all") }, label = { Text("Tất cả", style = MaterialTheme.typography.labelMedium) })
        FilterChip(selected = current == "pic", onClick = onPictures, label = { Text("Ảnh") })
        FilterChip(selected = current == "dl", onClick = onDownload, label = { Text("Tải xuống") })
        FilterChip(selected = current == "docs", onClick = onDocs, label = { Text("Tài liệu") })
        FilterChip(selected = current == "dcim", onClick = onDcim, label = { Text("DCIM") })
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
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
    canWrite: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (hasClip && count == 0) "Bấm Dán trong thư mục đang mở" else "$count đã chọn", modifier = Modifier.padding(end = 8.dp))
        TextButton(onClick = onSelectAll) { Text("Chọn hết") }
        if (count > 0) TextButton(onClick = onCopy) { Text("Sao chép") }
        if (canWrite && count > 0) TextButton(onClick = onCut) { Text("Cắt") }
        if (canWrite && hasClip) TextButton(onClick = onPaste) { Text("Dán vào đây") }
        TextButton(onClick = onZip) { Text("ZIP") }
        TextButton(onClick = onShare) { Text("Chia sẻ") }
        if (canWrite) TextButton(onClick = onDelete) { Text("Xoá") }
        TextButton(onClick = onClear) { Text("Huỷ") }
    }
    }
}


@Composable
private fun GoogleProfileRow(email: String) {
    val acc = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(androidx.compose.ui.platform.LocalContext.current)
    val url = acc?.photoUrl?.toString().orEmpty()
    var bmp by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        bmp = try {
            java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
    val letter = email.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val b = bmp
            if (b != null) {
                androidx.compose.foundation.Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                )
            } else {
                Text(letter, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(email, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MoreItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PrivacyPolicyDialog(en: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Privacy Policy" else "Chính sách quyền riêng tư") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (en)
                    "Local File Manager does not sell your data. Storage is used only after you allow it. Google sign-in is optional (email, profile photo, settings backup to Drive App Data). Web Share works on your Wi-Fi only when you turn it on."
                else
                    "Local File Manager không bán dữ liệu. Chỉ đọc/ghi tệp khi bạn cấp quyền. Đăng nhập Google là tuỳ chọn (email, ảnh hồ sơ, sao lưu cài đặt Drive). Web Share chỉ chạy Wi-Fi khi bạn bật."
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "OK" else "Đóng") } }
    )
}

@Composable
private fun SettingsDialog(
    dark: Boolean,
    lang: String = "vi",
    password: String,
    hideSizes: Boolean,
    onDark: (Boolean) -> Unit,
    onLang: (String) -> Unit = {},
    onPassword: (String) -> Unit,
    onHideSizes: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pass by rememberSaveable { mutableStateOf(password) }
    val en = lang == "en"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Settings" else "Cài đặt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (en) "Theme" else "Giao diện", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = !dark, onClick = { onDark(false) })
                    Text(if (en) "Light" else "Trắng", modifier = Modifier.clickable { onDark(false) })
                    Spacer(Modifier.width(16.dp))
                    androidx.compose.material3.RadioButton(selected = dark, onClick = { onDark(true) })
                    Text(if (en) "Dark" else "Đen", modifier = Modifier.clickable { onDark(true) })
                }
                Text(if (en) "Language" else "Ngôn ngữ", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = !en, onClick = { onLang("vi") })
                    Text("Tiếng Việt", modifier = Modifier.clickable { onLang("vi") })
                    Spacer(Modifier.width(16.dp))
                    androidx.compose.material3.RadioButton(selected = en, onClick = { onLang("en") })
                    Text("English", modifier = Modifier.clickable { onLang("en") })
                }
                val ctx = LocalContext.current
                TextButton(onClick = {
                    try {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(FmSettings.PRIVACY_URL)))
                    } catch (_: Exception) {}
                }) { Text(if (en) "Open Privacy Policy page" else "Mở trang Chính sách (nếu đã đăng GitHub)") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (en) "Hide sizes on Web Share" else "Ẩn dung lượng trên Web Share", modifier = Modifier.weight(1f))
                    Switch(checked = hideSizes, onCheckedChange = onHideSizes)
                }
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; onPassword(it) },
                    label = { Text(if (en) "Web Share password (empty = none)" else "Mật khẩu Web Share (trống = không)") },
                    singleLine = true
                )
                var portTxt by rememberSaveable { mutableStateOf(FmSettings.port(ctx).toString()) }
                OutlinedTextField(value = portTxt, onValueChange = {
                    portTxt = it
                    it.toIntOrNull()?.let { n -> if (n in 1024..65535) FmSettings.setPort(ctx, n) }
                }, label = { Text("Cổng Web Share") }, singleLine = true)
                Text("Tự tắt server")
                Row {
                    listOf(0,15,30,60).forEach { m ->
                        TextButton(onClick = { FmSettings.setAutoOffMin(ctx, m) }) { Text(if (m==0) "Không" else "${m}p") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chỉ nhận tệp (web)", modifier = Modifier.weight(1f))
                    Switch(checked = FmSettings.receiveOnly(ctx), onCheckedChange = { FmSettings.setReceiveOnly(ctx, it) })
                }
                var mail by rememberSaveable { mutableStateOf(FmSettings.googleEmail(ctx)) }
                OutlinedTextField(value = mail, onValueChange = { mail = it; FmSettings.setGoogleEmail(ctx, it) },
                    label = { Text("Email Google / tên khôi phục") }, singleLine = true)

            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Done" else "Xong") } }
    )
}

// -----------------------------------------------------------------------------
// Icon & nhãn tệp (Material Icons, không emoji)
// -----------------------------------------------------------------------------

private val KNOWN_EXTS = setOf(
    "txt","docx","doc","pdf","xlsx","xls","pptx","ppt","odt","ods","rtf",
    "jpg","jpeg","png","gif","svg","webp","psd","ai","ico","bmp","heic",
    "mp3","wav","flac","aac","ogg","m4a",
    "mp4","mkv","avi","mov","webm","3gp",
    "html","htm","css","js","py","java","cpp","c","json","xml","php","kt","h","ts",
    "zip","rar","7z","tar","gz",
    "iso","exe","apk","xapk","ipa","msi","bat","cmd","sh","dll",
    "obb","bin","cue","nsp","xci","sav","dat","srm","pak","vpk","unity3d","asset","bsp","wad","jar",
    "mcpack","mcworld","apks","crdownload","part","download","utpart","met"
)

private fun isKnownExt(file: File) =
    file.isDirectory || file.extension.lowercase(Locale.US) in KNOWN_EXTS


fun typeIconRes(file: File): Int {
    if (file.isDirectory) return R.drawable.ic_type_folder
    val id = file.extension.lowercase(Locale.US)
    val res = when (id) {
        "txt" -> R.drawable.ic_ext_txt
        "rtf" -> R.drawable.ic_ext_rtf
        "odt" -> R.drawable.ic_ext_odt
        "doc" -> R.drawable.ic_ext_doc
        "docx" -> R.drawable.ic_ext_docx
        "pdf" -> R.drawable.ic_ext_pdf
        "xls" -> R.drawable.ic_ext_xls
        "xlsx" -> R.drawable.ic_ext_xlsx
        "ods" -> R.drawable.ic_ext_ods
        "csv" -> R.drawable.ic_ext_csv
        "ppt" -> R.drawable.ic_ext_ppt
        "pptx" -> R.drawable.ic_ext_pptx
        "jpg" -> R.drawable.ic_ext_jpg
        "jpeg" -> R.drawable.ic_ext_jpeg
        "png" -> R.drawable.ic_ext_png
        "gif" -> R.drawable.ic_ext_gif
        "svg" -> R.drawable.ic_ext_svg
        "webp" -> R.drawable.ic_ext_webp
        "psd" -> R.drawable.ic_ext_psd
        "ai" -> R.drawable.ic_ext_ai
        "ico" -> R.drawable.ic_ext_ico
        "bmp" -> R.drawable.ic_ext_bmp
        "heic" -> R.drawable.ic_ext_heic
        "mp3" -> R.drawable.ic_ext_mp3
        "wav" -> R.drawable.ic_ext_wav
        "flac" -> R.drawable.ic_ext_flac
        "aac" -> R.drawable.ic_ext_aac
        "ogg" -> R.drawable.ic_ext_ogg
        "m4a" -> R.drawable.ic_ext_m4a
        "mp4" -> R.drawable.ic_ext_mp4
        "mkv" -> R.drawable.ic_ext_mkv
        "avi" -> R.drawable.ic_ext_avi
        "mov" -> R.drawable.ic_ext_mov
        "webm" -> R.drawable.ic_ext_webm
        "3gp" -> R.drawable.ic_ext_3gp
        "html" -> R.drawable.ic_ext_html
        "htm" -> R.drawable.ic_ext_htm
        "css" -> R.drawable.ic_ext_css
        "js" -> R.drawable.ic_ext_js
        "py" -> R.drawable.ic_ext_py
        "java" -> R.drawable.ic_ext_java
        "cpp" -> R.drawable.ic_ext_cpp
        "c" -> R.drawable.ic_ext_c
        "json" -> R.drawable.ic_ext_json
        "xml" -> R.drawable.ic_ext_xml
        "php" -> R.drawable.ic_ext_php
        "kt" -> R.drawable.ic_ext_kt
        "h" -> R.drawable.ic_ext_h
        "ts" -> R.drawable.ic_ext_ts
        "zip" -> R.drawable.ic_ext_zip
        "rar" -> R.drawable.ic_ext_rar
        "7z" -> R.drawable.ic_ext_7z
        "tar" -> R.drawable.ic_ext_tar
        "gz" -> R.drawable.ic_ext_gz
        "iso" -> R.drawable.ic_ext_iso
        "exe" -> R.drawable.ic_ext_exe
        "msi" -> R.drawable.ic_ext_msi
        "dll" -> R.drawable.ic_ext_dll
        "ipa" -> R.drawable.ic_ext_ipa
        "apk" -> R.drawable.ic_ext_apk
        "xapk" -> R.drawable.ic_ext_xapk
        "apks" -> R.drawable.ic_ext_apks
        "bat" -> R.drawable.ic_ext_bat
        "cmd" -> R.drawable.ic_ext_cmd
        "sh" -> R.drawable.ic_ext_sh
        "obb" -> R.drawable.ic_ext_obb
        "bin" -> R.drawable.ic_ext_bin
        "cue" -> R.drawable.ic_ext_cue
        "nsp" -> R.drawable.ic_ext_nsp
        "xci" -> R.drawable.ic_ext_xci
        "sav" -> R.drawable.ic_ext_sav
        "dat" -> R.drawable.ic_ext_dat
        "srm" -> R.drawable.ic_ext_srm
        "pak" -> R.drawable.ic_ext_pak
        "vpk" -> R.drawable.ic_ext_vpk
        "unity3d" -> R.drawable.ic_ext_unity3d
        "asset" -> R.drawable.ic_ext_asset
        "bsp" -> R.drawable.ic_ext_bsp
        "wad" -> R.drawable.ic_ext_wad
        "jar" -> R.drawable.ic_ext_jar
        "mcpack" -> R.drawable.ic_ext_mcpack
        "mcworld" -> R.drawable.ic_ext_mcworld
        "crdownload" -> R.drawable.ic_ext_crdownload
        "part" -> R.drawable.ic_ext_part
        "download" -> R.drawable.ic_ext_download
        "utpart" -> R.drawable.ic_ext_utpart
        "met" -> R.drawable.ic_ext_partmet
        else -> R.drawable.ic_type_unknown
    }
    return res
}

private fun iconFor(file: File): ImageVector {
    if (file.isDirectory) return Icons.Outlined.Folder
    return when (file.extension.lowercase(Locale.US)) {
        "pdf" -> Icons.Outlined.PictureAsPdf
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> Icons.Outlined.Image
        "mp4", "mkv", "avi", "webm", "mov" -> Icons.Outlined.Movie
        "mp3", "wav", "aac", "ogg", "m4a", "flac" -> Icons.Outlined.AudioFile
        "txt", "md", "log", "json", "xml", "doc", "docx" -> Icons.Outlined.Description
        "apk", "xapk", "apks" -> Icons.Outlined.Android
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private val PKG_NAME_RE = Regex("[a-zA-Z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

fun packageIdOfDir(file: File): String? {
    if (!file.isDirectory) return null
    val n = file.name
    if (!n.contains('.') || n.length < 3) return null
    val p = file.absolutePath.replace('\\','/')
    if (!p.contains("/Android/data") && !p.contains("/Android/obb")) return null
    return if (PKG_NAME_RE.matches(n)) n else null
}

fun isDownloadingFile(file: File): Boolean {
    val n = file.name.lowercase(Locale.US)
    return n.endsWith(".crdownload") || n.endsWith(".part") || n.endsWith(".part.met") ||
        n.endsWith(".download") || n.endsWith(".utpart")
}

private fun subtitleFor(file: File): String {
    val extra = if (isDownloadingFile(file)) "Đang tải · " else ""
    return extra + subtitleForInner(file)
}

private fun subtitleForInner(file: File): String {
    val time = DATE_FMT.format(Date(file.lastModified()))
    return if (file.isDirectory) {
        if (PowerMode.active) "Thư mục  ·  $time" else "${file.listFiles()?.size ?: 0} mục  ·  $time"
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


fun isAndroidRestrictedPath(path: String): Boolean {
    val p = path.replace("\\", "/")
    return p.contains("/Android/data") || p.contains("/Android/obb")
}

data class DirListing(val files: List<File>, val denied: Boolean)

private fun listDirDetailed(dir: File, showHidden: Boolean = false, ctx: Context? = null): DirListing {
    if (ctx != null && RootAccess.shouldListAsRoot(ctx, dir)) {
        val files = RootAccess.list(dir, showHidden)
        if (files.isNotEmpty()) return DirListing(files, false)
        if (RootAccess.lastDenied) return DirListing(emptyList(), true)
    }
    try {
        if (!dir.exists()) {
            return DirListing(emptyList(), isAndroidRestrictedPath(dir.absolutePath))
        }
        if (!dir.isDirectory) return DirListing(emptyList(), false)
        val raw = dir.listFiles()
        if (raw == null) {
            if (ctx != null) {
                val via = RootAccess.list(dir, showHidden)
                if (via.isNotEmpty()) return DirListing(via, false)
            }
            return DirListing(emptyList(), true)
        }
        val files = raw.filter { showHidden || !it.name.startsWith(".") }
        if (files.isEmpty() && isAndroidRestrictedPath(dir.absolutePath) && !dir.canRead()) {
            return DirListing(emptyList(), true)
        }
        return DirListing(files, false)
    } catch (_: SecurityException) {
        return DirListing(emptyList(), true)
    } catch (_: Exception) {
        return DirListing(emptyList(), isAndroidRestrictedPath(dir.absolutePath))
    }
}

private fun listFilesSafe(dir: File, showHidden: Boolean = false, ctx: Context? = null): List<File> {
    return listDirDetailed(dir, showHidden, ctx).files
}

/** Lấy thông tin file (thời gian, kích thước) một lần rồi mới sắp xếp, tránh gọi hệ thống trong mỗi phép so sánh. */
private class SortKey(val f: File, val dir: Boolean, val name: String, val ext: String, val time: Long, val size: Long)

private fun sortFilesFast(list: List<File>, mode: SortMode): List<File> {
    val keys = list.map { f ->
        SortKey(
            f = f,
            dir = f.isDirectory,
            name = f.name.lowercase(Locale.US),
            ext = f.extension.lowercase(Locale.US),
            time = if (mode == SortMode.DATE) f.lastModified() else 0L,
            size = if (mode == SortMode.SIZE && !f.isDirectory) f.length() else 0L
        )
    }
    val cmp: Comparator<SortKey> = when (mode) {
        SortMode.NAME -> compareBy<SortKey> { !it.dir }.thenBy { it.name }
        SortMode.DATE -> compareBy<SortKey> { !it.dir }.thenByDescending { it.time }
        SortMode.SIZE -> compareBy<SortKey> { !it.dir }.thenByDescending { it.size }
        SortMode.TYPE -> compareBy<SortKey> { !it.dir }.thenBy { it.ext }.thenBy { it.name }
    }
    return keys.sortedWith(cmp).map { it.f }
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

fun uniqueCopyName(parent: File, source: File): File {
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
    return try {
        WebShareService.start(context, root, FmSettings.port(context).coerceIn(1024, 65535))
        true
    } catch (_: Exception) { false }
}

private fun stopServer(context: Context? = null) {
    try {
        if (context != null) WebShareService.stop(context)
        else ServerBridge.server?.stop()
    } catch (_: Exception) {
    } finally {
        if (context == null) ServerBridge.server = null
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
    if (SystemGuard.isProtected(parent)) return false
    val cleaned = name.trim()
    if (cleaned.isEmpty() || cleaned.contains("/") || cleaned.contains("\\")) return false
    val dir = File(parent, cleaned)
    return !dir.exists() && dir.mkdirs()
}

private fun openFile(context: Context, file: File) {
    if (ApkInstaller.isInstallable(file)) {
        ApkInstaller.install(context, file)
        return
    }
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

/** Đơn vị thập phân (1 KB = 1000 B), giống cách Android hiển thị dung lượng. */
fun formatSize(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val kb = bytes / 1000.0
    if (kb < 1000) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1000.0
    if (mb < 1000) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1000.0)
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

private fun pasteDestFolder(currentDir: File, selected: Set<String>): File {
    val clipPaths = ClipHolder.clip?.paths.orEmpty().toSet()
    val dirs = selected.map { File(it) }.filter { it.isDirectory && it.absolutePath !in clipPaths }
    return dirs.singleOrNull() ?: currentDir
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


