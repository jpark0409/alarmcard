package com.jpark.alarmcard.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun JsonObject.strOrNull(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.let {
        runCatching { it.jsonPrimitive.content.replace(",", "").toDouble() }.getOrNull()
    }

fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.let {
        runCatching { it.jsonPrimitive.content.replace(",", "").toInt() }.getOrNull()
    }

fun JsonObject.boolOrNull(key: String): Boolean? =
    this[key]?.let {
        runCatching {
            val s = it.jsonPrimitive.content.lowercase()
            when (s) { "true", "1", "y" -> true; "false", "0", "n" -> false; else -> null }
        }.getOrNull()
    }

fun JsonElement?.arrayOrEmpty(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())
