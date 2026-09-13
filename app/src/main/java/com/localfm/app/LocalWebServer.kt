package com.localfm.app

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

    companion object {
        const val DEFAULT_PORT = 8080
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri ?: "/"
            if (uri == "/style.css") return cssResponse()
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
        val uri = session.uri ?: "/"
        val params = session.parms ?: emptyMap()
        return when {
            uri == "/" || uri == "/browse" || uri.startsWith("/browse") -> serveListing(params["p"].orEmpty())
            uri == "/download" -> serveDownload(params["p"].orEmpty())
            uri == "/zip" -> serveZip(params["p"].orEmpty())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không tìm thấy")
        }
    }

    private fun handlePost(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val files = HashMap<String, String>()
        session.parseBody(files)
        val params = session.parms ?: emptyMap()
        return when (uri) {
            "/upload" -> handleUpload(files, params)
            "/zip-selected" -> handleZipSelected(params)
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
        val fis = FileInputStream(tmp)
        val res = newFixedLengthResponse(Response.Status.OK, "application/zip", fis, tmp.length())
        res.addHeader("Content-Disposition", "attachment; filename=\"selected.zip\"")
        return res
    }

    private fun serveZip(rel: String): Response {
        val file = resolveSafe(rel) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Không tìm thấy")
        val tmp = File.createTempFile("folder-", ".zip")
        if (!ZipUtils.zipTo(listOf(file), tmp)) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Không nén được")
        }
        val fis = FileInputStream(tmp)
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
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
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
        </head>
    """.trimIndent()
}

/** CSS toi gian, chuyen nghiep — khong dung emoji. */
private const val WEB_CSS = """
:root {
  --bg: #eef3f9;
  --card: #ffffff;
  --text: #152033;
  --muted: #5b6578;
  --line: #d7deea;
  --brand: #2f6fed;
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: var(--bg); color: var(--text);
  font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif; }
.wrap { max-width: 980px; margin: 32px auto; padding: 0 20px 48px; }
header { margin-bottom: 20px; }
.brand { font-size: 22px; font-weight: 650; letter-spacing: .2px; }
.sub { color: var(--muted); font-size: 13px; margin-top: 6px; word-break: break-all; }
.crumb { font-size: 14px; margin: 8px 0 18px; }
.crumb a { color: var(--brand); text-decoration: none; }
.crumb .sep { margin: 0 6px; color: var(--muted); }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 16px;
  padding: 16px 18px; margin-bottom: 16px; box-shadow: 0 8px 24px rgba(21,32,51,.04); }
.upload h2 { font-size: 15px; margin: 0 0 12px; font-weight: 600; }
form { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
button, .dl {
  background: var(--brand); color: #fff; border: 0; border-radius: 10px;
  padding: 8px 14px; font-size: 13px; cursor: pointer; text-decoration: none;
}
button:hover, .dl:hover { filter: brightness(1.05); }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; color: var(--muted); font-weight: 600; font-size: 12px;
  padding: 8px 6px; border-bottom: 1px solid var(--line); }
td { padding: 10px 6px; border-bottom: 1px solid var(--line); }
.name { font-weight: 550; }
a.name.dir { color: var(--brand); text-decoration: none; }
.empty { color: var(--muted); text-align: center; padding: 24px 0; }
.drop.on { outline: 2px dashed var(--brand); background: #e8f0ff; }
footer { color: var(--muted); font-size: 12px; text-align: center; margin-top: 8px; }
@media (max-width: 720px) {
  .wrap { margin: 12px auto; padding: 0 10px 32px; }
  .brand { font-size: 18px; }
  .sub { font-size: 11px; }
  .card { padding: 12px; border-radius: 12px; }
  table { font-size: 13px; }
  th:nth-child(3), td:nth-child(3),
  th:nth-child(4), td:nth-child(4),
  th.col-date, td:nth-child(5) { display: none; }
  button, .dl { padding: 6px 10px; font-size: 12px; border-radius: 8px; white-space: nowrap; }
  td { padding: 8px 4px; vertical-align: middle; }
  .name { max-width: 58vw; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
"""
