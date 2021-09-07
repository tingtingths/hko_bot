package me.itdog.hko_bot

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.stream.Stream

data class UserSettings(var botLocale: BotLocale)

interface KeyValuePersistent<K, V> {
    fun get(key: K): V?
    fun set(key: K, value: V)
    fun del(key: K)
    fun close()
}

interface UserSettingsPersistent {
    fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings
    fun saveUserSettings(userId: Long, settings: UserSettings)
    fun saveUserSettings(userSettings: Map<Long, UserSettings>)
    fun close()
}

class RedisPersistent(private val jedisPool: JedisPool) : KeyValuePersistent<String, String>, UserSettingsPersistent {

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

    override fun get(key: String): String? {
        var value: String?
        with(borrowConnection()) {
            value = get(key)
            logger.debug("GET $key=$value")
            this
        }.let { returnConnection(it) }
        return value
    }

    override fun set(key: String, value: String) {
        with(borrowConnection()) {
            logger.debug("SET $key=$value")
            set(key, value)
            this
        }.let { returnConnection(it) }
    }

    override fun del(key: String) {
        with(borrowConnection()) {
            logger.debug("DEL $key")
            del(key)
            this
        }.let { returnConnection(it) }
    }

    override fun close() {
        jedisPool.close()
    }

    override fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings {
        val key = CacheKeyPrefix.USER_SETTINGS.prefix + userId
        val json = get(key)
        return if (json == null) otherwise else try {
            gson.fromJson(json, UserSettings::class.java)
        } catch (e: Exception) {
            del(key)
            otherwise
        }
    }

    override fun saveUserSettings(userId: Long, settings: UserSettings) {
        val json = gson.toJson(settings)
        set(CacheKeyPrefix.USER_SETTINGS.prefix + userId, json)
    }

    override fun saveUserSettings(userSettings: Map<Long, UserSettings>) {
        with(borrowConnection()) {
            val lst = userSettings.entries.stream()
                .flatMap { entry ->
                    Stream.of(entry.key.toString(), gson.toJson(entry.value))
                }
                .toArray<String> { length -> arrayOfNulls(length) }
            mset(*lst)
            this
        }.let { returnConnection(it) }
    }
}

class LocalFilePersistent(private val file: File) : KeyValuePersistent<String, UserSettings>, UserSettingsPersistent {

    private val gson = Gson()
    private var jsonObject: JsonObject
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    init {
        if (!file.exists()) {
            file.createNewFile()
        } else {
            if (!file.isFile) {
                throw IllegalArgumentException("Persistent file ${file.path} is not a file...")
            }
        }
        // read from file
        gson.newJsonReader(FileReader(file)).let { reader ->
            jsonObject = try {
                if (file.length() > 0) JsonParser.parseReader(reader).asJsonObject else JsonObject()
            } catch (e: Exception) {
                logger.warn("Unable to parse persistent file ${file.path}, back up current file...")
                file.copyTo(
                    File(file.parent, "${file.name}.${System.currentTimeMillis()}")
                )
                JsonObject()
            }
            reader.close()
        }
    }

    override fun get(key: String): UserSettings? {
        val value = jsonObject.get(key)
        logger.debug("GET $key=$value")
        return if (value == null || value.isJsonNull) null else gson.fromJson(value, UserSettings::class.java)
    }

    override fun set(key: String, value: UserSettings) {
        logger.debug("SET $key=$value")
        jsonObject.add(key, gson.toJsonTree(value))
        write()
    }

    override fun del(key: String) {
        logger.debug("DEL $key")
        jsonObject.remove(key)
        write()
    }

    override fun close() {
    }

    private fun write() {
        gson.newJsonWriter(FileWriter(file)).let { writer ->
            gson.toJson(jsonObject, writer)
            writer.flush()
            writer.close()
        }
    }

    override fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings {
        return get(userId.toString()) ?: otherwise
    }

    override fun saveUserSettings(userId: Long, settings: UserSettings) {
        set(userId.toString(), settings)
    }

    override fun saveUserSettings(userSettings: Map<Long, UserSettings>) {
        userSettings.forEach { jsonObject.add(it.key.toString(), gson.toJsonTree(it.value)) }
        write()
    }
}
