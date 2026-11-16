package com.localfm.app

import org.json.JSONObject
import org.json.JSONArray
import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalWebServer(
    port: Int = DEFAULT_PORT,
    @Volatile var shareRoot: File
) : NanoHTTPD(port) {

    @Volatile var sharePassword: String = ""
    @Volatile var hideSizes: Boolean = false
    @Volatile var receiveOnly: Boolean = false
    @Volatile var onEvent: (String) -> Unit = {}
    /** Tên link đẹp (không có dấu /), rỗng = chỉ dùng địa chỉ IP. */
    @Volatile var alias: String = ""
    private val tempLinks = java.util.concurrent.ConcurrentHashMap<String, TempLink>()

    companion object {
        const val DEFAULT_PORT = 8080
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            touchClient(session)
            val uri = routePath(session)
            if (uri == "/style.css") return cssResponse()
            if (uri == "/app.js") return newFixedLengthResponse(Response.Status.OK, "application/javascript; charset=utf-8", WEB_JS)
            if (uri.startsWith("/s/")) return serveTempLink(uri.removePrefix("/s/"), session.method == Method.GET)
            if (!authorized(session)) {
                return if (session.method == Method.POST && uri == "/login") handleLogin(session)
                else loginPage()
            }
            when (session.method) {
                Method.POST -> handlePost(session)
                Method.GET, Method.HEAD -> handleGet(session)
                Method.OPTIONS -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Không hỗ trợ")
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Lỗi server: ${t.message}")
        }
    }

    /** Tạo link tải tạm: hết hạn sau [minutes] phút; [oneTime] thì chỉ tải được một lần. Trả về mã link. */
    fun createTempLink(file: File, minutes: Int, oneTime: Boolean): String {
        val now = System.currentTimeMillis()
        tempLinks.entries.removeIf { it.value.expiresAt < now || it.value.usesLeft <= 0 }
        val bytes = ByteArray(12)
        java.security.SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        tempLinks[token] = TempLink(file, now + minutes * 60_000L, if (oneTime) 1 else Int.MAX_VALUE)
        return token
    }

    /** Link tạm không cần đăng nhập: mã trong link là thứ xác thực. */
    private fun serveTempLink(token: String, consume: Boolean): Response {
        val link = tempLinks[token]
        val now = System.currentTimeMillis()
        if (link == null || link.expiresAt < now || link.usesLeft <= 0 || !link.file.isFile) {
            tempLinks.remove(token)
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Liên kết không tồn tại hoặc đã hết hạn")
        }
        if (consume) {
            link.usesLeft--
            if (link.usesLeft <= 0) tempLinks.remove(token)
        }
        val fis = FileInputStream(link.file)
        val res = newFixedLengthResponse(Response.Status.OK, guessMime(link.file), fis, link.file.length())
        res.addHeader("Content-Disposition", "attachment; filename=\"${link.file.name}\"")
        onEvent("Tải qua link tạm: ${link.file.name}")
        return res
    }

    /** Bỏ tiền tố tên link: /kho-file/browse -> /browse, /kho-file -> /. */
    private fun routePath(session: IHTTPSession): String {
        val raw = session.uri ?: "/"
        val a = alias
        if (a.isBlank()) return raw
        val p = "/$a"
        return when {
            raw == p -> "/"
            raw.startsWith("$p/") -> raw.removePrefix(p)
            else -> raw
        }
    }

    /** Ghi nhận thiết bị đang dùng Web Share (theo IP, bỏ qua localhost). */
    private val clients = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun touchClient(session: IHTTPSession) {
        val ip = session.remoteIpAddress
        if (!ip.isNullOrBlank() && ip != "127.0.0.1" && ip != "::1") clients[ip] = System.currentTimeMillis()
    }

    /** Số thiết bị có hoạt động trong 2 phút gần đây. */
    fun activeClients(): Int {
        val cut = System.currentTimeMillis() - 120_000L
        clients.entries.removeIf { it.value < cut }
        return clients.size
    }

    private fun token(): String {
        if (sharePassword.isBlank()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(sharePassword.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun authorized(session: IHTTPSession): Boolean {
        if (sharePassword.isBlank()) return true
        val cookie = session.headers["cookie"].orEmpty()
        return cookie.contains("lfm=${token()}")
    }

    private fun loginPage(): Response {
        val html = htmlHead("Đăng nhập") + """
            <body><div class="wrap"><section class="card">
            <h2>Web Share có mật khẩu</h2>
            <form action="/login" method="post">
              <input type="password" name="password" placeholder="Mật khẩu" required />
              <button type="submit">Vào</button>
            </form></section></div></body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/html; charset=utf-8", html)
    }

    private fun handleLogin(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val pass = session.parms["password"].orEmpty()
        if (pass != sharePassword) return loginPage()
        val res = htmlRedirect("/", "Đăng nhập thành công")
        res.addHeader("Set-Cookie", "lfm=${token()}; Path=/; HttpOnly")
        return res
    }

    private fun handleGet(session: IHTTPSession): Response {
        val uri = routePath(session)
        val params = session.parms ?: emptyMap()
        return when {
            uri == "/" || uri == "/browse" || uri.startsWith("/browse") -> serveListing(params["p"].orEmpty())
            uri == "/download" -> serveDownload(params["p"].orEmpty())
            uri == "/zip" -> serveZip(params["p"].orEmpty())
            uri == "/api/list" -> serveApiList(params["p"].orEmpty())
            uri == "/api/search" -> apiSearch(params["p"].orEmpty(), params["q"].orEmpty())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không tìm thấy")
        }
    }

    private fun handlePost(session: IHTTPSession): Response {
        val uri = routePath(session)
        val files = HashMap<String, String>()
        session.parseBody(files)
        val params = session.parms ?: emptyMap()
        return when (uri) {
            "/upload" -> handleUpload(files, params)
            "/zip-selected" -> handleZipSelected(params)
            "/api/rename" -> apiRename(params)
            "/api/delete" -> apiDelete(params)
            "/api/mkdir" -> apiMkdir(params)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không tìm thấy")
        }
    }

    private fun handleUpload(files: Map<String, String>, params: Map<String, String>): Response {
        val relDir = params["dir"].orEmpty()
        val destDir = resolveSafe(relDir) ?: shareRoot
        if (!destDir.exists()) destDir.mkdirs()
        if (!destDir.isDirectory) return htmlRedirect("/", "Thư mục đích không hợp lệ.")
        val tmpPath = files["file"]
        val originalName = sanitizeFileName(params["file"] ?: "upload.bin")
        if (tmpPath.isNullOrBlank()) return htmlRedirect(browseLink(relDir), "Không nhận được tệp tải lên.")
        val tmp = File(tmpPath)
        if (!tmp.exists()) return htmlRedirect(browseLink(relDir), "Tệp tạm không tồn tại.")
        val target = uniqueFile(File(destDir, originalName))
        FileInputStream(tmp).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        tmp.delete()
        onEvent("Đã nhận tệp: ${target.name}")
        return htmlRedirect(browseLink(relDir), "Đã tải lên: ${target.name}")
    }

    private fun handleZipSelected(params: Map<String, String>): Response {
        val raw = params["paths"].orEmpty()
        val rels = raw.split('\n', ',', ';').map { it.trim() }.filter { it.isNotBlank() }
        val srcs = rels.mapNotNull { resolveSafe(it) }.filter { it.exists() }
        if (srcs.isEmpty()) return htmlRedirect("/", "Chưa chọn tệp")
        val tmp = File.createTempFile("share-", ".zip")
        val ok = ZipUtils.zipTo(srcs, tmp)
        if (!ok) return htmlRedirect("/", "Không nén được")
        val fis = DeleteOnCloseInputStream(tmp)
        val res = newFixedLengthResponse(Response.Status.OK, "application/zip", fis, tmp.length())
        res.addHeader("Content-Disposition", "attachment; filename=\"selected.zip\"")
        onEvent("Máy khác tải ${srcs.size} mục dạng ZIP")
        return res
    }

    private fun serveZip(rel: String): Response {
        val file = resolveSafe(rel) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không tìm thấy")
        val tmp = File.createTempFile("folder-", ".zip")
        if (!ZipUtils.zipTo(listOf(file), tmp)) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Không nén được")
        }
        val fis = DeleteOnCloseInputStream(tmp)
        val res = newFixedLengthResponse(Response.Status.OK, "application/zip", fis, tmp.length())
        res.addHeader("Content-Disposition", "attachment; filename=\"${encodeHeaderFileName(file.name)}.zip\"")
        return res
    }

    private fun serveListing(rel: String): Response {
        val dir = resolveSafe(rel) ?: shareRoot
        val current = if (dir.isDirectory) dir else dir.parentFile ?: shareRoot
        val relCurrent = relativePath(current)
        val children = current.listFiles()?.toList().orEmpty()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
        val parentRel = current.parentFile?.takeIf { isInsideRoot(it) }?.let { relativePath(it) }
        if (receiveOnly) {
            val html = htmlHead("Nhận tệp") + """
            <body><div class="wrap"><header><div class="brand">Chỉ nhận tệp</div>
            <div class="sub">${escape(current.absolutePath)}</div></header>
            <section class="card upload drop" id="drop">
            <h2>Tải tệp lên điện thoại</h2>
            <form id="up" action="/upload" method="post" enctype="multipart/form-data">
            <input type="hidden" name="dir" value="${escape(relCurrent)}" />
            <input type="file" name="file" id="file" required />
            <button type="submit">Tải lên</button></form></section>
            <script>
            const d=document.getElementById('drop');const f=document.getElementById('file');
            ['dragenter','dragover'].forEach(ev=>d.addEventListener(ev,e=>{e.preventDefault();}));
            d.addEventListener('drop',e=>{e.preventDefault();if(e.dataTransfer.files.length){f.files=e.dataTransfer.files;document.getElementById('up').submit();}});
            </script></div></body></html>"""
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        }
        val html = buildString {
            append(htmlHead("Quản lý tệp"))
            append("""<body><div class="wrap">""")
            append("""<header><div class="brand">Quản lý tệp</div>""")
            append("""<div class="sub">Đang chia sẻ: ${escape(current.absolutePath)}</div></header>""")
            append("""<nav class="crumb">""")
            append("""<a href="/">Gốc</a>""")
            if (relCurrent.isNotBlank()) {
                var acc = ""
                relCurrent.split('/').filter { it.isNotBlank() }.forEach { part ->
                    acc = if (acc.isBlank()) part else "$acc/$part"
                    append("""<span class="sep">/</span><a href="${browseLink(acc)}">${escape(part)}</a>""")
                }
            }
            append("</nav>")
            append("""<section class="card upload drop" id="drop">""")
            append("""<h2>Tải lên điện thoại</h2>""")
            append("""<form id="up" action="/upload" method="post" enctype="multipart/form-data">""")
            append("""<input type="hidden" name="dir" value="${escape(relCurrent)}" />""")
            append("""<input type="file" name="file" id="file" multiple required />""")
            append("""<button type="submit">Tải lên</button></form></section>""")
            append("""<form action="/zip-selected" method="post"><section class="card"><table>""")
            append("<thead><tr><th></th><th>Tên</th><th>Loại</th>")
            if (!hideSizes) append("<th>Kích thước</th>")
            append("<th class=\"col-date\">Sửa đổi</th><th></th></tr></thead><tbody>")
            if (parentRel != null || relCurrent.isNotBlank()) {
                val href = if (parentRel == null) "/" else browseLink(parentRel)
                append("""<tr class="dir"><td></td><td colspan="5"><a href="$href">.. (Thư mục cha)</a></td></tr>""")
            }
            if (children.isEmpty()) append("""<tr><td colspan="6" class="empty">Thư mục trống</td></tr>""")
            for (file in children) {
                val name = file.name
                val relChild = relativePath(file)
                val type = if (file.isDirectory) "Thư mục" else file.extension.ifBlank { "Tệp" }.uppercase(Locale.US)
                val size = if (hideSizes) "" else if (file.isDirectory) "-" else formatSize(file.length())
                val modified = DATE_FMT.format(Date(file.lastModified()))
                append("<tr>")
                append("""<td><input type="checkbox" name="paths" value="${escape(relChild)}"/></td>""")
                if (file.isDirectory) {
                    append("""<td><a class="name dir" href="${browseLink(relChild)}">${escape(name)}</a></td>""")
                    append("<td>$type</td>")
                    if (!hideSizes) append("<td>$size</td>")
                    append("<td>$modified</td>")
                    append("""<td><a class="dl" href="/zip?p=${urlEncode(relChild)}">Tải</a></td>""")
                } else {
                    append("""<td><span class="name">${escape(name)}</span></td>""")
                    append("<td>$type</td>")
                    if (!hideSizes) append("<td>$size</td>")
                    append("<td>$modified</td>")
                    append("""<td><a class="dl" href="${downloadLink(relChild)}">Tải</a></td>""")
                }
                append("</tr>")
            }
            append("</tbody></table>")
            append("""<p><button type="submit">Tải mục đã chọn</button></p>""")
            append("</section></form>")
            append("""<script>
const d=document.getElementById('drop');
const f=document.getElementById('file');
['dragenter','dragover'].forEach(ev=>d.addEventListener(ev,e=>{e.preventDefault();d.classList.add('on');}));
['dragleave','drop'].forEach(ev=>d.addEventListener(ev,e=>{e.preventDefault();d.classList.remove('on');}));
d.addEventListener('drop',e=>{if(e.dataTransfer.files.length){f.files=e.dataTransfer.files;document.getElementById('up').submit();}});
</script></div></body></html>""")
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveDownload(rel: String): Response {
        val file = resolveSafe(rel) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không tìm thấy")
        if (!file.isFile) return serveZip(rel)
        val fis = FileInputStream(file)
        val res = newFixedLengthResponse(Response.Status.OK, guessMime(file), fis, file.length())
        res.addHeader("Content-Disposition", "attachment; filename=\"${encodeHeaderFileName(file.name)}\"; filename*=UTF-8''${urlEncode(file.name)}")
        return res
    }

    private fun cssResponse(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/css; charset=utf-8", WEB_CSS)

    /**
     * API JSON cho app ngoài (desktop, iOS, HarmonyOS). Cần đăng nhập giống các trang khác.
     * GET /api/list?p=<đường dẫn tương đối> -> {"path": "...", "items": [{name, dir, size, modified, path}]}
     */
    private fun serveApiList(rel: String): Response {
        val dir = resolveSafe(rel)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"not_found\"}")
        if (!dir.isDirectory) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"not_a_directory\"}")
        }
        val items = JSONArray()
        dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
            .forEach { f ->
                items.put(
                    JSONObject()
                        .put("name", f.name)
                        .put("dir", f.isDirectory)
                        .put("size", if (f.isFile) f.length() else 0L)
                        .put("modified", f.lastModified())
                        .put("path", relativePath(f))
                )
            }
        val body = JSONObject().put("path", relativePath(dir)).put("items", items).toString()
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
    }

    private fun jsonOk(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())

    private fun jsonErr(code: Response.IStatus, msg: String): Response =
        newFixedLengthResponse(code, "application/json; charset=utf-8", JSONObject().put("error", msg).toString())

    /** Tên mới phải là một tên thư mục/tệp đơn giản: không có dấu / và không bắt đầu bằng dấu chấm. */
    private fun validName(name: String): Boolean =
        name.isNotBlank() && name.length <= 200 && !name.startsWith(".") &&
            !name.contains('/') && !name.contains('\\')

    private fun entryJson(f: File): JSONObject =
        JSONObject()
            .put("name", f.name)
            .put("dir", f.isDirectory)
            .put("size", if (f.isFile) f.length() else 0L)
            .put("modified", f.lastModified())
            .put("path", relativePath(f))

    /** POST /api/rename: p=<đường dẫn>, name=<tên mới> */
    private fun apiRename(params: Map<String, String>): Response {
        val target = resolveSafe(params["p"].orEmpty()) ?: return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (target == shareRoot.canonicalFile) return jsonErr(Response.Status.FORBIDDEN, "root")
        if (!target.exists()) return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (ReadOnlyLocks.isLocked(target)) return jsonErr(Response.Status.FORBIDDEN, "locked")
        val newName = params["name"].orEmpty().trim()
        if (!validName(newName)) return jsonErr(Response.Status.BAD_REQUEST, "bad_name")
        val dest = File(target.parentFile, newName)
        if (dest.exists()) return jsonErr(Response.Status.CONFLICT, "exists")
        if (!target.renameTo(dest)) return jsonErr(Response.Status.INTERNAL_ERROR, "failed")
        return jsonOk(JSONObject().put("path", relativePath(dest)))
    }

    /** POST /api/delete: p=<đường dẫn>. Chuyển vào .LocalFM_Trash (thư mục ẩn), không xoá hẳn. */
    private fun apiDelete(params: Map<String, String>): Response {
        val target = resolveSafe(params["p"].orEmpty()) ?: return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (target == shareRoot.canonicalFile) return jsonErr(Response.Status.FORBIDDEN, "root")
        if (!target.exists()) return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (ReadOnlyLocks.isLocked(target)) return jsonErr(Response.Status.FORBIDDEN, "locked")
        val bin = File(shareRoot, ".LocalFM_Trash").apply { mkdirs() }
        val dest = File(bin, "${System.currentTimeMillis()}_${target.name}")
        if (!target.renameTo(dest)) return jsonErr(Response.Status.INTERNAL_ERROR, "failed")
        onEvent("Đã xoá từ máy khác: ${target.name}")
        return jsonOk(JSONObject().put("trashed", dest.name))
    }

    /** POST /api/mkdir: p=<thư mục cha>, name=<tên thư mục> */
    private fun apiMkdir(params: Map<String, String>): Response {
        val parent = resolveSafe(params["p"].orEmpty()) ?: return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (!parent.isDirectory) return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (ReadOnlyLocks.isLocked(parent)) return jsonErr(Response.Status.FORBIDDEN, "locked")
        val name = params["name"].orEmpty().trim()
        if (!validName(name)) return jsonErr(Response.Status.BAD_REQUEST, "bad_name")
        val dir = File(parent, name)
        if (dir.exists()) return jsonErr(Response.Status.CONFLICT, "exists")
        if (!dir.mkdir()) return jsonErr(Response.Status.INTERNAL_ERROR, "failed")
        return jsonOk(JSONObject().put("path", relativePath(dir)))
    }

    /** GET /api/search?p=<thư mục>&q=<từ khoá>: tìm theo tên trong thư mục và thư mục con (tối đa 200 kết quả). */
    private fun apiSearch(rel: String, q: String): Response {
        val dir = resolveSafe(rel) ?: return jsonErr(Response.Status.NOT_FOUND, "not_found")
        if (!dir.isDirectory) return jsonErr(Response.Status.BAD_REQUEST, "not_a_directory")
        val needle = q.trim().lowercase(Locale.US)
        if (needle.isEmpty()) return jsonErr(Response.Status.BAD_REQUEST, "empty_query")
        val out = JSONArray()
        fun walk(d: File, depth: Int) {
            if (depth > 8 || out.length() >= 200) return
            d.listFiles().orEmpty().forEach { f ->
                if (out.length() >= 200) return@forEach
                if (f.name.startsWith(".")) return@forEach
                if (f.name.lowercase(Locale.US).contains(needle)) out.put(entryJson(f))
                if (f.isDirectory) walk(f, depth + 1)
            }
        }
        walk(dir, 0)
        return jsonOk(JSONObject().put("path", relativePath(dir)).put("items", out))
    }

    private fun resolveSafe(rel: String): File? {
        val decoded = try {
            URLDecoder.decode(rel, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            rel
        }.trim().trimStart('/')
        if (decoded.contains("..")) return null
        val rootCanon = shareRoot.canonicalFile
        val target = if (decoded.isBlank()) rootCanon else File(rootCanon, decoded).canonicalFile
        if (!isInsideRoot(target, rootCanon)) return null
        return target
    }

    private fun isInsideRoot(file: File, root: File = shareRoot.canonicalFile): Boolean {
        val path = file.canonicalFile.absolutePath
        val rootPath = root.absolutePath
        return path == rootPath || path.startsWith(rootPath + File.separator)
    }

    private fun relativePath(file: File): String {
        val rootPath = shareRoot.canonicalFile.absolutePath
        val filePath = file.canonicalFile.absolutePath
        if (filePath == rootPath) return ""
        return filePath.removePrefix(rootPath).trimStart('/', '\\').replace('\\', '/')
    }

    private fun browseLink(rel: String): String {
        return if (rel.isBlank()) "/" else "/browse?p=${urlEncode(rel)}"
    }

    private fun downloadLink(rel: String): String = "/download?p=${urlEncode(rel)}"

    private fun htmlRedirect(location: String, message: String): Response {
        val html = """
            <!DOCTYPE html><html lang="en"><head>
            <meta charset="utf-8"/>
            <meta http-equiv="refresh" content="1;url=$location"/>
            <link rel="stylesheet" href="/style.css"/>
            <title>Quản lý tệp</title></head>
            <body><div class="wrap"><section class="card"><p>${escape(message)}</p>
            <p><a href="$location">Back to list</a></p></section></div></body></html>
        """.trimIndent()
        val res = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        res.addHeader("Location", location)
        return res
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val name = file.nameWithoutExtension
        val ext = file.extension
        var i = 1
        while (true) {
            val candidate = File(
                file.parentFile,
                if (ext.isBlank()) "${name}_$i" else "${name}_$i.$ext"
            )
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._\\- ()\\[\\]]"), "_")
        return cleaned.ifBlank { "upload.bin" }
    }

    private fun guessMime(file: File): String {
        val ext = file.extension.lowercase(Locale.US)
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return fromMap ?: when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "txt", "log", "md" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1000) return "$bytes B"
        val kb = bytes / 1000.0
        if (kb < 1000) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1000.0
        if (mb < 1000) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1000.0)
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun encodeHeaderFileName(name: String): String = name.replace("\"", "")

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun htmlHead(title: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <title>$title</title>
            <link rel="stylesheet" href="/style.css"/>
            <script src="/app.js" defer></script>
        </head>
    """.trimIndent()
}

/** CSS toi gian, chuyen nghiep — khong dung emoji. */
private const val WEB_CSS = """
:root {
  --bg: #f1f5fb; --card: #ffffff; --text: #111a2b; --muted: #5f6b7e;
  --line: #e1e7f0; --brand: #2f6fed; --brand2: #6aa0ff; --ok: #1f9d55; --soft: #e9f0ff;
  --shadow: 0 10px 30px rgba(17,26,43,.07);
}
@media (prefers-color-scheme: dark) {
  :root { --bg: #0d1420; --card: #162132; --text: #e8eef9; --muted: #98a6bb;
    --line: #26344a; --soft: #1b2b45; --shadow: 0 10px 30px rgba(0,0,0,.35); }
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: var(--bg); color: var(--text);
  font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif; }
body { background: radial-gradient(1200px 500px at 10% -10%, var(--soft), transparent 60%), var(--bg); min-height: 100vh; }
.wrap { max-width: 980px; margin: 28px auto; padding: 0 20px 48px; animation: fadeUp .45s ease-out both; }
header { margin-bottom: 18px; display: flex; flex-direction: column; gap: 6px; }
.brand { font-size: 22px; font-weight: 700; letter-spacing: .2px; display: flex; align-items: center; gap: 10px; }
.brand::before { content: ""; width: 10px; height: 10px; border-radius: 50%; background: var(--ok);
  box-shadow: 0 0 0 0 rgba(31,157,85,.5); animation: pulse 2s infinite; }
.sub { color: var(--muted); font-size: 13px; word-break: break-all; }
.pill { display: inline-flex; align-items: center; gap: 6px; background: var(--soft); color: var(--brand);
  border-radius: 999px; padding: 4px 12px; font-size: 12px; font-weight: 600; width: fit-content; }
.crumb { font-size: 14px; margin: 8px 0 18px; }
.crumb a { color: var(--brand); text-decoration: none; }
.crumb a:hover { text-decoration: underline; }
.crumb .sep { margin: 0 6px; color: var(--muted); }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 18px;
  padding: 18px 20px; margin-bottom: 16px; box-shadow: var(--shadow); animation: fadeUp .5s ease-out both; }
.upload h2 { font-size: 15px; margin: 0 0 12px; font-weight: 650; }
form { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
input[type=file] { font-size: 13px; color: var(--muted); max-width: 100%; }
button, .dl {
  background: linear-gradient(135deg, var(--brand), var(--brand2)); color: #fff; border: 0; border-radius: 12px;
  padding: 9px 16px; font-size: 13px; font-weight: 600; cursor: pointer; text-decoration: none;
  transition: transform .15s ease, box-shadow .2s ease, filter .2s ease; box-shadow: 0 6px 16px rgba(47,111,237,.25);
}
button:hover, .dl:hover { transform: translateY(-1px); box-shadow: 0 10px 22px rgba(47,111,237,.32); }
button:active, .dl:active { transform: scale(.97); }
button:disabled { opacity: .6; cursor: default; transform: none; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; color: var(--muted); font-weight: 600; font-size: 12px; text-transform: uppercase; letter-spacing: .4px;
  padding: 10px 8px; border-bottom: 1px solid var(--line); }
td { padding: 12px 8px; border-bottom: 1px solid var(--line); transition: background .15s ease; }
tr { animation: fadeUp .4s ease-out both; }
tbody tr:hover td { background: var(--soft); }
.name { font-weight: 560; }
a.name.dir { color: var(--brand); text-decoration: none; }
a.name.dir:hover { text-decoration: underline; }
.empty { color: var(--muted); text-align: center; padding: 28px 0; }
.drop { transition: transform .2s ease; }
.drop.on { outline: 2px dashed var(--brand); background: var(--soft); transform: scale(1.01); }
footer { color: var(--muted); font-size: 12px; text-align: center; margin-top: 8px; }
/* Thanh tiến trình tải lên */
#prog { position: fixed; left: 0; right: 0; top: 0; z-index: 10; padding: 10px 20px; background: var(--card);
  border-bottom: 1px solid var(--line); box-shadow: var(--shadow); display: none; transform: translateY(-100%); transition: transform .3s ease; }
#prog.show { display: block; transform: none; }
#prog .txt { font-size: 13px; color: var(--muted); display: flex; justify-content: space-between; margin-bottom: 6px; }
#prog .track { height: 8px; background: var(--line); border-radius: 999px; overflow: hidden; }
#prog .fill { height: 100%; width: 0%; border-radius: 999px; background: linear-gradient(90deg, var(--brand), var(--brand2));
  transition: width .2s linear; background-size: 200% 100%; animation: shine 1.4s linear infinite; }
@keyframes fadeUp { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
@keyframes pulse { 0% { box-shadow: 0 0 0 0 rgba(31,157,85,.5); } 70% { box-shadow: 0 0 0 8px rgba(31,157,85,0); } 100% { box-shadow: 0 0 0 0 rgba(31,157,85,0); } }
@keyframes shine { from { background-position: 0 0; } to { background-position: 200% 0; } }
@media (prefers-reduced-motion: reduce) { * { animation: none !important; transition: none !important; } }
@media (max-width: 720px) {
  .wrap { margin: 12px auto; padding: 0 12px 32px; }
  .brand { font-size: 18px; }
  .sub { font-size: 11px; }
  .card { padding: 14px; border-radius: 14px; }
  table { font-size: 13px; }
  th:nth-child(3), td:nth-child(3), th:nth-child(4), td:nth-child(4), th.col-date, td:nth-child(5) { display: none; }
  button, .dl { padding: 7px 11px; font-size: 12px; border-radius: 10px; white-space: nowrap; }
  td { padding: 10px 4px; vertical-align: middle; }
  .name { max-width: 58vw; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
"""

/** Stream đọc file tạm rồi xoá file đó khi đóng, để zip tải về không tồn tại mãi trong cache. */
private class DeleteOnCloseInputStream(private val target: File) :
    java.io.FilterInputStream(FileInputStream(target)) {
    override fun close() {
        try { super.close() } finally { target.delete() }
    }
}

/** Link tải tạm: file, thời điểm hết hạn, số lần còn được tải. */
private class TempLink(val file: File, val expiresAt: Long, var usesLeft: Int)

private const val WEB_JS = """
(function () {
  function fmt(n) {
    if (n >= 1073741824) return (n / 1073741824).toFixed(2) + " GB";
    if (n >= 1048576) return (n / 1048576).toFixed(1) + " MB";
    if (n >= 1024) return (n / 1024).toFixed(0) + " KB";
    return n + " B";
  }
  function bar() {
    var el = document.getElementById("prog");
    if (!el) {
      el = document.createElement("div");
      el.id = "prog";
      el.innerHTML = '<div class="txt"><span id="progT">Đang tải lên…</span><span id="progS"></span></div>' +
        '<div class="track"><div class="fill" id="progF"></div></div>';
      document.body.appendChild(el);
    }
    return el;
  }
  document.addEventListener("submit", function (e) {
    var f = e.target;
    if (!f || f.enctype !== "multipart/form-data" || !window.FormData) return;
    var input = f.querySelector('input[type=file]');
    if (!input || !input.files || input.files.length === 0) return;
    e.preventDefault();
    var el = bar();
    el.classList.add("show");
    var fill = document.getElementById("progF");
    var sp = document.getElementById("progS");
    var t0 = Date.now();
    var xhr = new XMLHttpRequest();
    xhr.open("POST", f.action || location.href, true);
    xhr.upload.onprogress = function (ev) {
      if (!ev.lengthComputable) return;
      var pct = ev.loaded / ev.total * 100;
      fill.style.width = pct.toFixed(1) + "%";
      var secs = Math.max((Date.now() - t0) / 1000, 0.2);
      sp.textContent = Math.round(pct) + "% · " + fmt(ev.loaded / secs) + "/s";
    };
    xhr.onload = function () { location.reload(); };
    xhr.onerror = function () {
      document.getElementById("progT").textContent = "Tải lên thất bại. Kiểm tra mạng rồi thử lại.";
      fill.style.background = "#d64545";
    };
    xhr.send(new FormData(f));
  });
  document.addEventListener("dragover", function (e) {
    var d = document.getElementById("drop");
    if (d) { e.preventDefault(); d.classList.add("on"); }
  });
  document.addEventListener("dragleave", function () {
    var d = document.getElementById("drop");
    if (d) d.classList.remove("on");
  });
  document.addEventListener("drop", function (e) {
    var d = document.getElementById("drop");
    if (!d) return;
    e.preventDefault();
    d.classList.remove("on");
    var input = document.getElementById("file");
    if (input && e.dataTransfer && e.dataTransfer.files.length) {
      input.files = e.dataTransfer.files;
      var f = document.getElementById("up");
      if (f) f.requestSubmit ? f.requestSubmit() : f.submit();
    }
  });
})();
"""
