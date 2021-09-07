package me.itdog.hko_bot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.math.abs

data class UserSettings(var botLocale: BotLocale)

interface KeyValuePersistent<K, V> {
    fun get(key: K): V?
    fun set(key: K, value: V)
    fun del(key: K)
    fun close()
}

interface UserSettingsPersistent {
    fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings
    fun getAllUsersSettings(): Map<Long, UserSettings>
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

    override fun getAllUsersSettings(): Map<Long, UserSettings> {
        var ret: Map<Long, UserSettings>
        with(borrowConnection()) {
            // convert to list to maintain order
            val allKeys = listOf(keys(CacheKeyPrefix.USER_SETTINGS.prefix + "??"))
                .stream().toArray<String> { length -> arrayOfNulls(length) }
            val settings = mget(*allKeys)
            ret = allKeys.zip(settings) { k, v ->
                Pair(v.toLong(), if (v != null) gson.fromJson(v, UserSettings::class.java) else null)
            }.filter {
                it.second != null
            }.toMap() as Map<Long, UserSettings>
            this
        }.let { returnConnection(it) }
        return ret
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

class LocalFilePersistent(private val file: File) : KeyValuePersistent<Long, UserSettings>, UserSettingsPersistent {

    private val gson = Gson()
    private var userSettings: MutableMap<Long, UserSettings> = mutableMapOf()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private var outstandingChanges: AtomicInteger = AtomicInteger(0)
    private var lastWrite = Instant.now()

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
            userSettings = try {
                if (file.length() > 0)
                    gson.fromJson(reader, object : TypeToken<Map<Long, UserSettings>>() {}.type)
                else
                    mutableMapOf()
            } catch (e: Exception) {
                logger.warn("Unable to parse persistent file ${file.path}, back up current file...")
                file.copyTo(
                    File(file.parent, "${file.name}.${System.currentTimeMillis()}")
                )
                mutableMapOf()
            }
            reader.close()
        }
    }

    override fun get(key: Long): UserSettings? {
        val value = userSettings[key]
        logger.debug("GET $key=$value")
        return value
    }

    override fun set(key: Long, value: UserSettings) {
        logger.debug("SET $key=$value")
        userSettings[key] = value
        outstandingChanges.incrementAndGet()
        write()
    }

    override fun del(key: Long) {
        logger.debug("DEL $key")
        userSettings.remove(key)
        outstandingChanges.incrementAndGet()
        write()
    }

    override fun close() {
        write(true)
    }

    @Synchronized
    private fun write(force: Boolean = false) {
        if (force) {
            outstandingChanges.set(0)
            lastWrite = Instant.now()
            gson.newJsonWriter(FileWriter(file)).let { writer ->
                gson.toJson(gson.toJsonTree(userSettings), writer)
                writer.flush()
                writer.close()
            }
        } else if (outstandingChanges.get() > 100
            || abs(Duration.between(Instant.now(), lastWrite).toSeconds()) >= 60
        ) {
            write(true)
        }
    }

    override fun getUserSettings(userId: Long, otherwise: UserSettings): UserSettings {
        return get(userId) ?: otherwise
    }

    override fun getAllUsersSettings(): Map<Long, UserSettings> {
        return userSettings
    }

    override fun saveUserSettings(userId: Long, settings: UserSettings) {
        set(userId, settings)
    }

    override fun saveUserSettings(userSettings: Map<Long, UserSettings>) {
        this.userSettings.putAll(userSettings)
        write()
    }
}
