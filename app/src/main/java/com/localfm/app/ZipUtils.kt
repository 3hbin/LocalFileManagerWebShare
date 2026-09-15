package com.localfm.app

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {
    data class ZipItem(val name: String, val size: Long, val isDir: Boolean)

    fun isEncrypted(zipFile: File): Boolean {
        return try { net.lingala.zip4j.ZipFile(zipFile).isEncrypted } catch (_: Exception) { false }
    }

    fun listEntries(zipFile: File, password: String? = null, limit: Int = 400): List<ZipItem> {
        if (!zipFile.exists()) return emptyList()
        return try {
            val z = net.lingala.zip4j.ZipFile(zipFile)
            if (z.isEncrypted) {
                if (password.isNullOrBlank()) return emptyList()
                z.setPassword(password.toCharArray())
            }
            z.fileHeaders.take(limit).map {
                ZipItem(it.fileName ?: "", it.uncompressedSize, it.isDirectory)
            }
        } catch (_: Exception) { emptyList() }
    }


    fun emptyZip(destZip: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                zos.putNextEntry(ZipEntry("README.txt"))
                zos.write("ZIP tao bang Quan ly tep\n".toByteArray())
                zos.closeEntry()
            }
            destZip.exists()
        } catch (_: Exception) {
            destZip.delete()
            false
        }
    }

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

    fun unzip(zipFile: File, destDir: File, password: String? = null): String? {
        if (!zipFile.exists()) return "Không tìm thấy tệp"
        val name = zipFile.name.lowercase()
        if (name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".rar5")) {
            return "Chưa hỗ trợ RAR/7z — hãy dùng ZIP"
        }
        return try {
            destDir.mkdirs()
            val z = net.lingala.zip4j.ZipFile(zipFile)
            if (z.isEncrypted) {
                if (password.isNullOrBlank()) return "ZIP_PASSWORD"
                z.setPassword(password.toCharArray())
            }
            z.extractAll(destDir.absolutePath)
            null
        } catch (e: Exception) {
            val m = e.message ?: "Giải nén thất bại"
            if (m.contains("password", true) || m.contains("encrypted", true)) "ZIP_PASSWORD" else m
        }
    }

    fun zipPassword(sources: List<File>, destZip: File, password: String): Boolean {
        return try {
            val zp = net.lingala.zip4j.ZipFile(destZip, password.toCharArray())
            val params = net.lingala.zip4j.model.ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
            }
            sources.forEach { f ->
                if (f.isDirectory) zp.addFolder(f, params) else zp.addFile(f, params)
            }
            destZip.exists()
        } catch (_: Exception) { false }
    }
}
