package com.localfm.app

import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Máy chủ HTTP nhúng (NanoHTTPD) chạy trên cổng 8080.
 *
 * Hỗ trợ:
 *  - GET  /            : giao diện HTML liệt kê thư mục đang chia sẻ
 *  - GET  /browse?p=   : duyệt thư mục con (không thoát khỏi root)
 *  - GET  /download?p= : tải tệp về máy tính
 *  - POST /upload      : tải tệp từ máy tính lên Android (multipart)
 *
 * Mọi đường dẫn đều được chuẩn hoá và kiểm tra nằm trong [shareRoot]
 * để tránh path traversal.
 */
class LocalWebServer(
    port: Int = DEFAULT_PORT,
    @Volatile var shareRoot: File
) : NanoHTTPD(port) {

    companion object {
        const val DEFAULT_PORT = 8080
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.POST -> handlePost(session)
                Method.GET, Method.HEAD -> handleGet(session)
                Method.OPTIONS -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                else -> newFixedLengthResponse(
                    Response.Status.METHOD_NOT_ALLOWED,
                    MIME_PLAINTEXT,
                    "Method not allowed"
                )
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server error: ${t.message ?: t.javaClass.simpleName}"
            )
        }
    }

    private fun handleGet(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val params = session.parms ?: emptyMap()

        return when {
            uri == "/" || uri == "/browse" || uri.startsWith("/browse") -> {
                serveListing(params["p"].orEmpty())
            }
            uri == "/download" -> serveDownload(params["p"].orEmpty())
            uri == "/style.css" -> cssResponse()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun handlePost(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        if (uri != "/upload") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        val files = HashMap<String, String>()
        session.parseBody(files)

        val params = session.parms ?: emptyMap()
        val relDir = params["dir"].orEmpty()
        val destDir = resolveSafe(relDir) ?: shareRoot
        if (!destDir.exists()) destDir.mkdirs()
        if (!destDir.isDirectory) {
            return htmlRedirect("/", "Thu muc dich khong hop le.")
        }

        val tmpPath = files["file"]
        val originalName = sanitizeFileName(params["file"] ?: "upload.bin")

        if (tmpPath.isNullOrBlank()) {
            return htmlRedirect(browseLink(relDir), "Khong nhan duoc tep tai len.")
        }

        val tmp = File(tmpPath)
        if (!tmp.exists()) {
            return htmlRedirect(browseLink(relDir), "Tep tam khong ton tai.")
        }

        val target = uniqueFile(File(destDir, originalName))
        FileInputStream(tmp).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        tmp.delete()

        return htmlRedirect(browseLink(relDir), "Da tai len: ${target.name}")
    }

    private fun serveListing(rel: String): Response {
        val dir = resolveSafe(rel) ?: shareRoot
        val current = if (dir.isDirectory) dir else dir.parentFile ?: shareRoot
        val relCurrent = relativePath(current)

        val children = current.listFiles()?.toList().orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })

        val parentRel = current.parentFile
            ?.takeIf { isInsideRoot(it) }
            ?.let { relativePath(it) }

        val html = buildString {
            append(htmlHead("Local File Manager"))
            append("""<body><div class="wrap">""")
            append("""<header><div class="brand">Local File Manager</div>""")
            append("""<div class="sub">Sharing: ${escape(current.absolutePath)}</div></header>""")

            append("""<nav class="crumb">""")
            append("""<a href="/">Root</a>""")
            if (relCurrent.isNotBlank()) {
                val parts = relCurrent.split('/').filter { it.isNotBlank() }
                var acc = ""
                for (part in parts) {
                    acc = if (acc.isEmpty()) part else "$acc/$part"
                    append("""<span class="sep">/</span><a href="${browseLink(acc)}">${escape(part)}</a>""")
                }
            }
            append("</nav>")

            append("""<section class="card upload">""")
            append("""<h2>Upload file to Android</h2>""")
            append("""<form action="/upload" method="post" enctype="multipart/form-data">""")
            append("""<input type="hidden" name="dir" value="${escape(relCurrent)}" />""")
            append("""<input type="file" name="file" required />""")
            append("""<button type="submit">Upload</button>""")
            append("</form></section>")

            append("""<section class="card"><table>""")
            append("<thead><tr><th>Name</th><th>Type</th><th>Size</th><th>Modified</th><th></th></tr></thead><tbody>")

            if (parentRel != null || relCurrent.isNotBlank()) {
                val href = if (parentRel == null) "/" else browseLink(parentRel)
                append("""<tr class="dir"><td colspan="5"><a href="$href">.. (Parent folder)</a></td></tr>""")
            }

            if (children.isEmpty()) {
                append("""<tr><td colspan="5" class="empty">Empty folder</td></tr>""")
            }

            for (file in children) {
                val name = file.name
                val relChild = relativePath(file)
                val type = if (file.isDirectory) "Folder" else (file.extension.ifBlank { "File" }.uppercase(Locale.US))
                val size = if (file.isDirectory) "-" else formatSize(file.length())
                val modified = DATE_FMT.format(Date(file.lastModified()))
                append("<tr>")
                if (file.isDirectory) {
                    append("""<td><a class="name dir" href="${browseLink(relChild)}">${escape(name)}</a></td>""")
                    append("<td>$type</td><td>$size</td><td>$modified</td><td></td>")
                } else {
                    append("""<td><span class="name">${escape(name)}</span></td>""")
                    append("<td>$type</td><td>$size</td><td>$modified</td>")
                    append("""<td><a class="dl" href="${downloadLink(relChild)}">Download</a></td>""")
                }
                append("</tr>")
            }

            append("</tbody></table></section>")
            append("""<footer>NanoHTTPD · Port ${DEFAULT_PORT} · Same Wi-Fi only</footer>""")
            append("</div></body></html>")
        }

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveDownload(rel: String): Response {
        val file = resolveSafe(rel)
        if (file == null || !file.exists() || !file.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }
        val mime = guessMime(file)
        val fis = FileInputStream(file)
        val response = newFixedLengthResponse(Response.Status.OK, mime, fis, file.length())
        response.addHeader(
            "Content-Disposition",
            "attachment; filename=\"${encodeHeaderFileName(file.name)}\"; filename*=UTF-8''${urlEncode(file.name)}"
        )
        return response
    }

    private fun cssResponse(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/css; charset=utf-8", WEB_CSS)
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
            <title>Local File Manager</title></head>
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
footer { color: var(--muted); font-size: 12px; text-align: center; margin-top: 8px; }
@media (max-width: 640px) {
  th:nth-child(3), td:nth-child(3), th:nth-child(4), td:nth-child(4) { display: none; }
}
"""
