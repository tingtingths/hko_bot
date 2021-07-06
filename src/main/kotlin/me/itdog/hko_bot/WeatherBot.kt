package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import me.itdog.hko_bot.api.HongKongObservatory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.generics.TelegramBot
import java.util.concurrent.TimeUnit

class WeatherBotBuilder {

    private var token: String? = null
    private var username: String? = null
    private var isWebhook = false
    private var webhookUrl: String = ""

    fun token(token: String): WeatherBotBuilder {
        this.token = token
        return this
    }

    fun username(username: String): WeatherBotBuilder {
        this.username = username
        return this
    }

    fun buildWebhookBot(url: String): WebhookWeatherBot {
        isWebhook = true
        webhookUrl = url
        return build() as WebhookWeatherBot
    }

    fun buildPollingBot(): PollingWeatherBot {
        isWebhook = false
        return build() as PollingWeatherBot
    }

    private fun build(): TelegramBot {
        if (token == null) throw IllegalArgumentException("Token must be supplied")
        if (username == null) throw IllegalArgumentException("Token must be supplied")

        // build
        if (isWebhook) {
            return PollingWeatherBot(token!!, username!!) // TODO
        } else {
            return PollingWeatherBot(token!!, username!!) // TODO
        }
    }
}

open class WeatherBot {

    enum class Cache {
        KEY_GENERAL_INFO,
        KEY_GENERAL_INFO_ENG,
        KEY_WARNING_INFO,
        KEY_WARNING_INFO_ENG,
    }

    private val api = HongKongObservatory()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val cache: LoadingCache<Cache, Any> = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(CacheLoader.from { key ->
            if (Cache.KEY_GENERAL_INFO == key || Cache.KEY_GENERAL_INFO_ENG == key) {
                api.getGeneralInfo(isEnglish = Cache.KEY_GENERAL_INFO_ENG == key)
            } else {
                api.getWarningInfo(isEnglish = Cache.KEY_WARNING_INFO_ENG == key)
            }
        })

    private val callbackQueryPage: QueryOption

    init {
        val p2o1 = QueryOption(InlineKeyboardButton("p2o1").apply {callbackData = "p2o1" }).apply {

        }
        val p2o2 = QueryOption(InlineKeyboardButton("p2o2").apply { callbackData = "p2o2" })
        val p2o3 = QueryOption(InlineKeyboardButton("p2o3").apply { callbackData = "p2o3" })

        val p1o1 = QueryOption(InlineKeyboardButton("p1o1").apply { callbackData = "p1o1" }).apply {
            this.nextPageFunc = {
                QueryOption.Companion.QueryOptionPageBuilder().apply {
                    addRow().addOption(p2o1).addOption(p2o2)
                    addRow().addOption(p2o3)
                }.build()
            }
        }

        callbackQueryPage = QueryOption { listOf(listOf(p1o1)) }
    }

    fun handleInlineQuery(update: Update): AnswerInlineQuery {
        val query = update.inlineQuery
        val reply = AnswerInlineQuery()
        reply.inlineQueryId = query.id
        reply.results = listOf(
            InlineQueryResultArticle().apply {
                id = "1"
                title = if (query.query == "") "EMPTY" else query.query
                inputMessageContent = InputTextMessageContent().apply {
                    messageText = "Echo ${query.query}"
                }
            }
        )
        return reply
    }

    fun handleMessage(update: Update): BotApiMethod<Message> {
        val message = update.message
        val text = message.text
        logger.debug("[@${message.chat.userName}] Message: ${text}")

        if (text == "/more") {
            val reply = SendMessage()
            reply.chatId = message.chatId.toString()
            reply.replyToMessageId = message.messageId
            reply.text = "What do you want to know?"
            reply.replyMarkup = callbackQueryPage.nextPageReplyMarkup()
            return reply
        } else {
            val reply = SendMessage()
            reply.chatId = message.chatId.toString()
            reply.replyToMessageId = message.messageId
            reply.text = "Echo \"${text}\""
            return reply
        }
    }

    fun handleCallbackQuery(update: Update): BotApiMethod<Message> {
        val callbackData = update.callbackQuery.data
        val found = callbackQueryPage.findPage(callbackData)
        logger.debug("handleCallbackQuery(); found=${found?.keyboardButton?.text}")
        return if (found != null) {
            found.responseFunc.invoke(update)
        } else {
            logger.warn("Cannot find page with callback data, $callbackData")
            val reply = SendMessage()
            reply.chatId = update.callbackQuery.message.chatId.toString()
            reply.text = "Cannot find page with callback data, $callbackData"
            reply
        }
    }
}

class PollingWeatherBot(val token: String, val username: String) : TelegramLongPollingBot() {

    private val bot = WeatherBot()

    override fun getBotToken(): String {
        return token
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun onUpdateReceived(update: Update?) {
        if (update == null) return

        if (update.hasInlineQuery()) {
            execute(bot.handleInlineQuery(update))
        } else if (update.hasMessage()) {
            execute(bot.handleMessage(update))
        } else if (update.hasCallbackQuery()) {
            execute(bot.handleCallbackQuery(update))
        }
    }
}

class WebhookWeatherBot(val token: String, val username: String, val url: String) : TelegramWebhookBot() {

    private val bot = WeatherBot()

    override fun getBotToken(): String {
        return token
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun onWebhookUpdateReceived(update: Update?): BotApiMethod<*> {
        if (update == null) throw NotImplementedError("Null update not handled...")

        if (update.hasInlineQuery()) {
            return bot.handleInlineQuery(update)
        } else if (update.hasMessage()) {
            return bot.handleMessage(update)
        } else if (update.hasCallbackQuery()) {
            return bot.handleCallbackQuery(update)
        }

        throw NotImplementedError("Unsupported update...")
    }

    override fun getBotPath(): String {
        return url
    }
}
