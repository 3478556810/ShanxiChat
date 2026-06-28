package com.xingchen.shanxichat.viewmodel

import android.app.Application
import com.xingchen.shanxichat.core.network.ModelConfigRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*   // 包含 map

class ConfigManager(private val repo: ModelConfigRepository) {
    data class Config(val type: String, val baseUrl: String, val modelName: String, val apiKey: String? = null)

    val configFlow: Flow<Config> = repo.configFlow.map {
        when (it.type) {
            "local_3b", "local_7b_pc" -> Config(it.type, it.localUrl, it.localModel)
            "ds" -> Config(it.type, it.dsUrl, it.dsModel, it.dsApiKey)
            "cloud_480b" -> Config(it.type, it.cloudUrl, it.cloudModel, it.cloudApiKey)
            else -> Config("local_3b", it.localUrl, it.localModel)
        }
    }

    suspend fun update(route: String, url: String, model: String, key: String) {
        withContext(Dispatchers.IO) {
            repo.updateType(route)
            when (route) {
                "local_3b", "local_7b_pc" -> {
                    repo.updateLocalUrl(url)
                    repo.updateLocalModel(model)
                }
                "ds" -> {
                    repo.updateDsUrl(url)
                    repo.updateDsModel(model)
                    repo.updateDsApiKey(key)
                }
                "cloud_480b" -> {
                    repo.updateCloudUrl(url)
                    repo.updateCloudModel(model)
                    repo.updateCloudApiKey(key)
                }
            }
        }
    }

    fun defaultConfig(route: String): Config {
        return when (route) {
            "local_3b" -> Config("local_3b", "http://10.0.2.2:8080/v1/chat/completions", "qwen2.5-3b-instruct")
            "local_7b_pc" -> Config("local_7b_pc", "http://10.0.2.2:11434/v1/chat/completions", "qwen2.5-coder:7b")
            "ds" -> Config("ds", "https://api.deepseek.com/v1/chat/completions", "deepseek-v4-flash", "")
            "cloud_480b" -> Config("cloud_480b", "http://10.0.2.2:11434/v1/chat/completions", "qwen3-coder:480b-cloud", "")
            else -> defaultConfig("local_3b")
        }
    }
}