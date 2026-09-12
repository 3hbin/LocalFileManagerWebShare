package com.localfm.app

import android.content.Context
import org.json.JSONArray

object FmSettings {
    private const val P = "local_fm_prefs"

    fun darkMode(ctx: Context): Boolean =
        ctx.getSharedPreferences(P, 0).getBoolean("dark_mode", false)

    fun setDarkMode(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("dark_mode", on).apply()
    }

    fun webPassword(ctx: Context): String =
        ctx.getSharedPreferences(P, 0).getString("web_pass", "") ?: ""

    fun setWebPassword(ctx: Context, pass: String) {
        ctx.getSharedPreferences(P, 0).edit().putString("web_pass", pass).apply()
    }

    fun hideSizes(ctx: Context): Boolean =
        ctx.getSharedPreferences(P, 0).getBoolean("hide_sizes", false)

    fun setHideSizes(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("hide_sizes", on).apply()
    }

    fun favorites(ctx: Context): MutableList<String> {
        val raw = ctx.getSharedPreferences(P, 0).getString("favs", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { arr.getString(it) }
    }

    fun toggleFavorite(ctx: Context, path: String): Boolean {
        val list = favorites(ctx)
        val on = if (list.contains(path)) {
            list.remove(path); false
        } else {
            list.add(0, path); true
        }
        saveList(ctx, "favs", list.take(40))
        return on
    }

    fun isFavorite(ctx: Context, path: String) = favorites(ctx).contains(path)

    fun recents(ctx: Context): MutableList<String> {
        val raw = ctx.getSharedPreferences(P, 0).getString("recents", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { arr.getString(it) }
    }

    fun addRecent(ctx: Context, path: String) {
        val list = recents(ctx)
        list.remove(path)
        list.add(0, path)
        saveList(ctx, "recents", list.take(30))
    }

    private fun saveList(ctx: Context, key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        ctx.getSharedPreferences(P, 0).edit().putString(key, arr.toString()).apply()
    }
}

data class ClipBoard(val paths: List<String>, val cut: Boolean)

object ClipHolder {
    var clip: ClipBoard? = null
}
