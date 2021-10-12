package me.itdog.hko_bot

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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

data class ChatSettings(var botLocale: BotLocale, var isNotificationEnabled: Boolean)

data class ApplicationSettings(var lastNotifiedWarnings: MutableMap<String, Instant> = mutableMapOf())

interface KeyValuePersistent<K, V> {
    fun get(key: K): V?
    fun set(key: K, value: V)
    fun del(key: K)
    fun close()
}

interface ChatSettingsPersistent {
    fun getChatSettings(id: Long, otherwise: ChatSettings? = null): ChatSettings?
    fun getAllChatsSettings(): Map<Long, ChatSettings>
    fun saveChatSettings(id: Long, settings: ChatSettings)
    fun saveChatSettings(chatSettings: Map<Long, ChatSettings>)
    fun close()
}

interface ApplicationSettingsPersistent {
    fun getApplicationSettings(): ApplicationSettings
    fun saveApplicationSettings(applicationSettings: ApplicationSettings)
    fun close()
}

inline fun <reified T> Gson.fromJson(json: JsonElement) = fromJson<T>(json, object : TypeToken<T>() {}.type)
inline fun <reified T> Gson.fromJson(json: String) = fromJson<T>(json, object : TypeToken<T>() {}.type)

class RedisPersistent(private val jedisPool: JedisPool) : KeyValuePersistent<String, String>, ChatSettingsPersistent,
    ApplicationSettingsPersistent {

    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private enum class CacheKeyPrefix(val prefix: String) {
        CHAT("hko_bot_chat_settings_"),
        APPLICATION("hko_bot_app_settings_")
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

    override fun getApplicationSettings(): ApplicationSettings {
        val key = CacheKeyPrefix.APPLICATION.prefix + "master"
        var json = get(key)
        if (json == null) {
            set(key, gson.toJson(ApplicationSettings()))
            return ApplicationSettings()
        }
        return try {
            gson.fromJson(json)
        } catch (e: Exception) {
            set(key, gson.toJson(ApplicationSettings()))
            ApplicationSettings()
        }
    }

    override fun saveApplicationSettings(applicationSettings: ApplicationSettings) {
        val json = gson.toJson(applicationSettings)
        set(CacheKeyPrefix.APPLICATION.prefix + "master", json)
    }

    override fun close() {
        jedisPool.close()
    }

    override fun getChatSettings(id: Long, otherwise: ChatSettings?): ChatSettings? {
        val key = CacheKeyPrefix.CHAT.prefix + id
        val json = get(key)
        return if (json == null) otherwise else try {
            gson.fromJson(json)
        } catch (e: Exception) {
            del(key)
            otherwise
        }
    }

    override fun getAllChatsSettings(): Map<Long, ChatSettings> {
        var ret: Map<Long, ChatSettings>
        with(borrowConnection()) {
            // convert to list to maintain order
            val keys = ArrayList(keys(CacheKeyPrefix.CHAT.prefix + "*"))
            keys.sort()
            val allKeys = keys.toTypedArray()
            ret = if (allKeys.isNotEmpty()) {
                val settings = mget(*allKeys)
                allKeys.zip(settings) { id, setting ->
                    Pair(
                        id.replaceFirst(CacheKeyPrefix.CHAT.prefix, "").toLong(),
                        if (setting != null) gson.fromJson(setting, ChatSettings::class.java) else null
                    )
                }.filter {
                    it.second != null
                }.toMap() as Map<Long, ChatSettings>
            } else {
                hashMapOf()
            }
            this
        }.let { returnConnection(it) }
        return ret
    }

    override fun saveChatSettings(id: Long, settings: ChatSettings) {
        val json = gson.toJson(settings)
        set(CacheKeyPrefix.CHAT.prefix + id, json)
    }

    override fun saveChatSettings(chatSettings: Map<Long, ChatSettings>) {
        with(borrowConnection()) {
            val lst = chatSettings.entries.stream()
                .flatMap { entry ->
                    Stream.of(entry.key.toString(), gson.toJson(entry.value))
                }
                .toArray<String> { length -> arrayOfNulls(length) }
            mset(*lst)
            this
        }.let { returnConnection(it) }
    }
}

class LocalFilePersistent(private val file: File) : KeyValuePersistent<String, JsonElement>, ChatSettingsPersistent,
    ApplicationSettingsPersistent {

    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private var outstandingChanges: AtomicInteger = AtomicInteger(0)
    private var lastWrite = Instant.now()

    // cached images
    private var image: JsonObject
    private var chatSettingsImage: MutableMap<Long, ChatSettings>
    private var applicationSettingsImage: ApplicationSettings

    private enum class Namespace(val key: String) {
        CHAT("chat_settings"), APPLICATION("application")
    }

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
            image = try {
                if (file.length() > 0) {
                    gson.fromJson(reader, JsonObject::class.java) ?: JsonObject()
                } else {
                    JsonObject()
                }
            } catch (e: Exception) {
                logger.warn("Unable to parse persistent file ${file.path}, back up current file...")
                file.copyTo(
                    File(file.parent, "${file.name}.${System.currentTimeMillis()}")
                )
                JsonObject()
            }
            reader.close()
        }

        chatSettingsImage = if (image.has(Namespace.CHAT.key)) {
            gson.fromJson(image.get(Namespace.CHAT.key))
        } else {
            mutableMapOf()
        }
        applicationSettingsImage = if (image.has(Namespace.APPLICATION.key)) {
            gson.fromJson(image.get(Namespace.APPLICATION.key))
        } else {
            ApplicationSettings()
        }
    }

    override fun get(key: String): JsonElement? {
        val value = image.get(key) ?: null
        logger.debug("GET $key=$value")
        return value
    }

    override fun set(key: String, value: JsonElement) {
        logger.debug("SET $key=$value")
        image.add(key, value)
        outstandingChanges.incrementAndGet()
        write()
    }

    override fun del(key: String) {
        logger.debug("DEL $key")
        image.remove(key)
        outstandingChanges.incrementAndGet()
        write()
    }

    override fun getApplicationSettings(): ApplicationSettings {
        return applicationSettingsImage
    }

    override fun saveApplicationSettings(applicationSettings: ApplicationSettings) {
        applicationSettingsImage = applicationSettings
        outstandingChanges.incrementAndGet()
    }

    override fun close() {
        write(true)
    }

    @Synchronized
    private fun write(force: Boolean = false) {
        if (force) {
            // consolidate images
            image.add(Namespace.CHAT.key, gson.toJsonTree(chatSettingsImage))
            image.add(Namespace.APPLICATION.key, gson.toJsonTree(applicationSettingsImage))

            outstandingChanges.set(0)
            lastWrite = Instant.now()
            gson.newJsonWriter(FileWriter(file)).let { writer ->
                gson.toJson(image, writer)
                writer.flush()
                writer.close()
            }
        } else if (outstandingChanges.get() > 100
            || abs(Duration.between(Instant.now(), lastWrite).toSeconds()) >= 60
        ) {
            write(true)
        }
    }

    override fun getChatSettings(id: Long, otherwise: ChatSettings?): ChatSettings? {
        return chatSettingsImage[id] ?: otherwise
    }

    override fun getAllChatsSettings(): Map<Long, ChatSettings> {
        return chatSettingsImage
    }

    override fun saveChatSettings(id: Long, settings: ChatSettings) {
        chatSettingsImage[id] = settings
        outstandingChanges.incrementAndGet()
    }

    override fun saveChatSettings(chatSettings: Map<Long, ChatSettings>) {
        chatSettingsImage.putAll(chatSettings)
        write()
    }
}
