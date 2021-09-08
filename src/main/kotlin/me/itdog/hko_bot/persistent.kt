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

data class ChatSettings(var botLocale: BotLocale, var isNotificationEnabled: Boolean)

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

class RedisPersistent(private val jedisPool: JedisPool) : KeyValuePersistent<String, String>, ChatSettingsPersistent {

    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private enum class CacheKeyPrefix(val prefix: String) {
        USER_SETTINGS("hko_bot_chat_settings_")
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

    override fun getChatSettings(id: Long, otherwise: ChatSettings?): ChatSettings? {
        val key = CacheKeyPrefix.USER_SETTINGS.prefix + id
        val json = get(key)
        return if (json == null) otherwise else try {
            gson.fromJson(json, ChatSettings::class.java)
        } catch (e: Exception) {
            del(key)
            otherwise
        }
    }

    override fun getAllChatsSettings(): Map<Long, ChatSettings> {
        var ret: Map<Long, ChatSettings>
        with(borrowConnection()) {
            // convert to list to maintain order
            val allKeys = listOf(keys(CacheKeyPrefix.USER_SETTINGS.prefix + "??"))
                .stream().toArray<String> { length -> arrayOfNulls(length) }
            val settings = mget(*allKeys)
            ret = allKeys.zip(settings) { _, v ->
                Pair(v.toLong(), if (v != null) gson.fromJson(v, ChatSettings::class.java) else null)
            }.filter {
                it.second != null
            }.toMap() as Map<Long, ChatSettings>
            this
        }.let { returnConnection(it) }
        return ret
    }

    override fun saveChatSettings(id: Long, settings: ChatSettings) {
        val json = gson.toJson(settings)
        set(CacheKeyPrefix.USER_SETTINGS.prefix + id, json)
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

class LocalFilePersistent(private val file: File) : KeyValuePersistent<Long, ChatSettings>, ChatSettingsPersistent {

    private val gson = Gson()
    private var chatSettings: MutableMap<Long, ChatSettings> = mutableMapOf()
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
            chatSettings = try {
                if (file.length() > 0)
                    gson.fromJson(reader, object : TypeToken<Map<Long, ChatSettings>>() {}.type)
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

    override fun get(key: Long): ChatSettings? {
        val value = chatSettings[key]
        logger.debug("GET $key=$value")
        return value
    }

    override fun set(key: Long, value: ChatSettings) {
        logger.debug("SET $key=$value")
        chatSettings[key] = value
        outstandingChanges.incrementAndGet()
        write()
    }

    override fun del(key: Long) {
        logger.debug("DEL $key")
        chatSettings.remove(key)
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
                gson.toJson(gson.toJsonTree(chatSettings), writer)
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
        return get(id) ?: otherwise
    }

    override fun getAllChatsSettings(): Map<Long, ChatSettings> {
        return chatSettings
    }

    override fun saveChatSettings(id: Long, settings: ChatSettings) {
        set(id, settings)
    }

    override fun saveChatSettings(chatSettings: Map<Long, ChatSettings>) {
        this.chatSettings.putAll(chatSettings)
        write()
    }
}
