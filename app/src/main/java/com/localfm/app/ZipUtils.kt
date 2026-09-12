package com.localfm.app

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun zipTo(sources: List<File>, destZip: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                sources.forEach { addToZip(it, it.name, zos) }
            }
            destZip.exists() && destZip.length() > 0
        } catch (_: Exception) {
            destZip.delete()
            false
        }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val kids = file.listFiles().orEmpty()
            if (kids.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else {
                kids.forEach { addToZip(it, "$entryName/${it.name}", zos) }
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun unzip(zipFile: File, destDir: File): String? {
        if (!zipFile.exists()) return "Không tìm thấy tệp"
        val name = zipFile.name.lowercase()
        if (name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".rar5")) {
            return "Chưa hỗ trợ RAR/7z — hãy dùng ZIP"
        }
        return try {
            destDir.mkdirs()
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(destDir, entry.name).canonicalFile
                    if (!out.path.startsWith(destDir.canonicalPath)) {
                        return "ZIP không hợp lệ"
                    }
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Giải nén thất bại"
        }
    }
}
