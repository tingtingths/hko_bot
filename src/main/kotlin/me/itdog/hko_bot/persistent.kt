package me.itdog.hko_bot

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

data class UserSettings(var botLocale: BotLocale)

class Persistent(private val redis: Jedis) {

    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private enum class CacheKeyPrefix(val prefix: String) {
        USER_SETTINGS("hko_bot_user_settings_")
    }

    fun get(key: String): String? {
        val value = redis.get(key)
        logger.debug("GET $key=$value")
        return value
    }

    fun set(key: String, value: String) {
        logger.debug("SET $key=$value")
        redis.set(key, value)
    }

    fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings {
        val key = CacheKeyPrefix.USER_SETTINGS.prefix + userId
        val json = get(key)
        return if (json == null) otherwise else try {
            gson.fromJson(json, UserSettings::class.java)
        } catch (e: Exception) {
            redis.del(key)
            otherwise
        }
    }

    fun setUserSettings(userId: Long, settings: UserSettings) {
        val json = gson.toJson(settings)
        set(CacheKeyPrefix.USER_SETTINGS.prefix + userId, json)
    }
}


