package com.scanx.qrscanner

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class HistoryRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "scan_history",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val gson = Gson()

    suspend fun saveResult(result: BarcodeResult) = withContext(Dispatchers.IO) {
        val history = getHistory().toMutableList()
        // Avoid exact duplicates at the top
        if (history.isNotEmpty() && history.first().rawValue == result.rawValue) {
            return@withContext
        }
        history.add(0, result)
        // Limit history size to 50
        if (history.size > 50) {
            history.removeAt(history.lastIndex)
        }
        saveHistoryList(history)
    }

    suspend fun getHistory(): List<BarcodeResult> = withContext(Dispatchers.IO) {
        val json = prefs.getString("history_list", null) ?: return@withContext emptyList()
        val type = object : TypeToken<List<BarcodeResult>>() {}.type
        try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteResult(result: BarcodeResult) = withContext(Dispatchers.IO) {
        val history = getHistory().toMutableList()
        val iterator = history.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.rawValue == result.rawValue && item.timestamp == result.timestamp) {
                iterator.remove()
                break
            }
        }
        saveHistoryList(history)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        prefs.edit().remove("history_list").apply()
    }

    private suspend fun saveHistoryList(list: List<BarcodeResult>) = withContext(Dispatchers.IO) {
        val json = gson.toJson(list)
        prefs.edit().putString("history_list", json).apply()
    }
}
