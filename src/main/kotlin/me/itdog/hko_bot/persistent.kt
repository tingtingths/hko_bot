package me.itdog.hko_bot

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

data class UserSettings(var botLocale: BotLocale)

class Persistent(private val jedisPool: JedisPool) {

    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private enum class CacheKeyPrefix(val prefix: String) {
        USER_SETTINGS("hko_bot_user_settings_")
    }

    private fun borrowConnection(): Jedis {
        return jedisPool.resource
    }

    private fun returnConnection(jedis: Jedis) {
        jedisPool.returnResource(jedis)
    }

    fun get(key: String): String? {
        var value: String?
        with(borrowConnection()) {
            value = get(key)
            logger.debug("GET $key=$value")
            this
        }.let { returnConnection(it) }
        return value
    }

    fun set(key: String, value: String) {
        with(borrowConnection()) {
            logger.debug("SET $key=$value")
            set(key, value)
            this
        }.let { returnConnection(it) }
    }

    fun del(key: String) {
        with(borrowConnection()) {
            del(key)
            this
        }.let { returnConnection(it) }
    }

    fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings {
        val key = CacheKeyPrefix.USER_SETTINGS.prefix + userId
        val json = get(key)
        return if (json == null) otherwise else try {
            gson.fromJson(json, UserSettings::class.java)
        } catch (e: Exception) {
            del(key)
            otherwise
        }
    }

    fun setUserSettings(userId: Long, settings: UserSettings) {
        val json = gson.toJson(settings)
        set(CacheKeyPrefix.USER_SETTINGS.prefix + userId, json)
    }
}


