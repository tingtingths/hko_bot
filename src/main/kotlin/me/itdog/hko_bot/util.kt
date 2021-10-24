package me.itdog.hko_bot

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken

inline fun <reified T> Gson.fromJson(json: JsonElement) = fromJson<T>(json, object : TypeToken<T>() {}.type)
inline fun <reified T> Gson.fromJson(json: String) = fromJson<T>(json, object : TypeToken<T>() {}.type)
