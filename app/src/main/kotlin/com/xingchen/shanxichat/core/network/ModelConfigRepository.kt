package com.xingchen.shanxichat.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "model_config")

class ModelConfigRepository(private val context: Context) {

    companion object {
        private val KEY_TYPE = stringPreferencesKey("type")
        private val KEY_LOCAL_URL = stringPreferencesKey("local_url")
        private val KEY_LOCAL_MODEL = stringPreferencesKey("local_model")
        private val KEY_DS_API_KEY = stringPreferencesKey("ds_api_key")
        private val KEY_DS_MODEL = stringPreferencesKey("ds_model")
        private val KEY_DS_URL = stringPreferencesKey("ds_url")
        private val KEY_CLOUD_API_KEY = stringPreferencesKey("cloud_api_key")
        private val KEY_CLOUD_MODEL = stringPreferencesKey("cloud_model")
        private val KEY_CLOUD_URL = stringPreferencesKey("cloud_url")
    }

    val configFlow: Flow<ModelConfig> = context.dataStore.data.map { prefs ->
        ModelConfig(
            type = prefs[KEY_TYPE] ?: "local_3b",
            localUrl = prefs[KEY_LOCAL_URL] ?: "http://10.0.2.2:11434/v1/chat/completions",
            localModel = prefs[KEY_LOCAL_MODEL] ?: "qwen2.5-coder:7b",
            dsApiKey = prefs[KEY_DS_API_KEY] ?: "",
            dsModel = prefs[KEY_DS_MODEL] ?: "deepseek-v4-flash",
            dsUrl = prefs[KEY_DS_URL] ?: "https://api.deepseek.com/v1/chat/completions",
            cloudApiKey = prefs[KEY_CLOUD_API_KEY] ?: "",
            cloudModel = prefs[KEY_CLOUD_MODEL] ?: "qwen3-coder:480b-cloud",
            cloudUrl = prefs[KEY_CLOUD_URL] ?: "http://10.0.2.2:11434/v1/chat/completions"
        )
    }

    suspend fun updateType(type: String) { context.dataStore.edit { it[KEY_TYPE] = type } }
    suspend fun updateLocalUrl(url: String) { context.dataStore.edit { it[KEY_LOCAL_URL] = url } }
    suspend fun updateLocalModel(model: String) { context.dataStore.edit { it[KEY_LOCAL_MODEL] = model } }
    suspend fun updateDsApiKey(key: String) { context.dataStore.edit { it[KEY_DS_API_KEY] = key } }
    suspend fun updateDsModel(model: String) { context.dataStore.edit { it[KEY_DS_MODEL] = model } }
    suspend fun updateDsUrl(url: String) { context.dataStore.edit { it[KEY_DS_URL] = url } }
    suspend fun updateCloudApiKey(key: String) { context.dataStore.edit { it[KEY_CLOUD_API_KEY] = key } }
    suspend fun updateCloudModel(model: String) { context.dataStore.edit { it[KEY_CLOUD_MODEL] = model } }
    suspend fun updateCloudUrl(url: String) { context.dataStore.edit { it[KEY_CLOUD_URL] = url } }
}

data class ModelConfig(
    val type: String,
    val localUrl: String,      // ← 统一用这个名字
    val localModel: String,    // ← 统一用这个名字
    val dsApiKey: String,
    val dsModel: String,
    val dsUrl: String,
    val cloudApiKey: String,
    val cloudModel: String,
    val cloudUrl: String
)