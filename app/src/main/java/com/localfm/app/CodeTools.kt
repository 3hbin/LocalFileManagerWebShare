package com.localfm.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.io.File

fun isHtml(file: File) =
    file.extension.lowercase() in setOf("html", "htm")

fun isEditable(file: File) =
    file.extension.lowercase() in setOf(
        "txt", "md", "log", "json", "xml", "csv",
        "kt", "kts", "java", "js", "ts", "jsx", "tsx",
        "css", "scss", "html", "htm", "py", "c", "cpp", "h",
        "gradle", "properties", "yml", "yaml", "sh", "php", "bat", "cmd", "rtf"
    )

private val KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "if", "else", "when", "for", "while",
    "return", "import", "package", "true", "false", "null", "this", "super",
    "public", "private", "protected", "override", "data", "sealed", "interface",
    "function", "const", "let", "async", "await", "export", "default", "new",
    "def", "from", "as", "try", "catch", "finally", "throw", "in", "is"
)

class CodeColorTransform(private val ext: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(colorize(text.text, ext), OffsetMapping.Identity)
    }
}

fun colorize(src: String, ext: String): AnnotatedString {
    val kw = Color(0xFF1565C0)
    val str = Color(0xFF2E7D32)
    val com = Color(0xFF9E9E9E)
    val err = Color(0xFFC62828)
    val num = Color(0xFF6A1B9A)
    return buildAnnotatedString {
        append(src)
        addStyle(SpanStyle(fontFamily = FontFamily.Monospace), 0, src.length)
        // comments
        Regex("//.*").findAll(src).forEach {
            addStyle(SpanStyle(color = com), it.range.first, it.range.last + 1)
        }
        Regex("/\\*[\\s\\S]*?\\*/").findAll(src).forEach {
            addStyle(SpanStyle(color = com), it.range.first, it.range.last + 1)
        }
        if (ext in setOf("html", "htm", "xml")) {
            Regex("<!--[\\s\\S]*?-->").findAll(src).forEach {
                addStyle(SpanStyle(color = com), it.range.first, it.range.last + 1)
            }
        }
        // strings
        Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(src).forEach {
            addStyle(SpanStyle(color = str), it.range.first, it.range.last + 1)
        }
        Regex("'([^'\\\\]|\\\\.)*'").findAll(src).forEach {
            addStyle(SpanStyle(color = str), it.range.first, it.range.last + 1)
        }
        // unclosed string → rest of line red
        src.lineSequence().fold(0) { acc, line ->
            val q = line.count { it == '"' } - line.split("\\\"").size + 1
            // simpler: odd number of unescaped quotes
            var i = 0
            var open = false
            while (i < line.length) {
                if (line[i] == '"' && (i == 0 || line[i - 1] != '\\')) open = !open
                i++
            }
            if (open) {
                val start = acc + line.indexOfLast { it == '"' }.coerceAtLeast(0)
                addStyle(SpanStyle(color = err), start, acc + line.length)
            }
            acc + line.length + 1
        }
        // keywords
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(src).forEach {
            if (it.value in KEYWORDS) {
                addStyle(SpanStyle(color = kw), it.range.first, it.range.last + 1)
            }
        }
        Regex("\\b\\d+(\\.\\d+)?\\b").findAll(src).forEach {
            addStyle(SpanStyle(color = num), it.range.first, it.range.last + 1)
        }
        // unmatched brackets
        val stack = ArrayDeque<Pair<Char, Int>>()
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
        src.forEachIndexed { i, c ->
            when (c) {
                '(', '[', '{' -> stack.addLast(c to i)
                ')', ']', '}' -> {
                    if (stack.isEmpty() || stack.last().first != pairs[c]) {
                        addStyle(SpanStyle(color = err), i, i + 1)
                    } else stack.removeLast()
                }
            }
        }
        stack.forEach { (_, i) -> addStyle(SpanStyle(color = err), i, i + 1) }
    }
}
