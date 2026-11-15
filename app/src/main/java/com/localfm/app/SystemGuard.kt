package com.localfm.app

import java.io.File

/**
 * Thư mục hệ thống: chỉ cho xem, không cho xoá, đổi tên, cắt, dán, tạo mới hoặc giải nén/nén vào.
 * Dùng absolutePath (không dùng canonicalPath) vì /storage/XXXX là symlink tới /mnt/media_rw,
 * nếu dùng canonicalPath sẽ chặn nhầm thẻ nhớ.
 */
object SystemGuard {
    /** Chỉ khớp đúng thư mục này (không chặn các thư mục con trong /storage). */
    private val EXACT = setOf("/", "/storage", "/mnt", "/data")

    /** Khớp chính thư mục này và mọi thư mục con. */
    private val PREFIX = listOf(
        "/system", "/vendor", "/product", "/odm", "/oem", "/apex",
        "/dev", "/proc", "/sys", "/sbin", "/etc", "/root", "/cache", "/config"
    )

    fun isProtected(f: File): Boolean {
        val p = f.absolutePath.trimEnd('/').ifEmpty { "/" }
        if (p in EXACT) return true
        return PREFIX.any { p == it || p.startsWith("$it/") }
    }
}
