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
import org.telegram.telegrambots.meta.generics.TelegramBot
import java.io.Serializable
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
    private val buttons = mutableListOf<QueryButton>()
    private val mainPage: QueryPage

    init {
        // build buttons
        buttons.add(QueryButton("p3o1", "p3o1").apply {
            buildMessage = {
                "This is a custom message for ${keyboardButton.callbackData}"
            }
        })
        buttons.add(QueryButton("p3o2", "p3o2"))
        buttons.add(QueryButton("p2o1", "p2o1"))
        buttons.add(QueryButton("p2o2", "p2o2"))
        buttons.add(QueryButton("p2o3", "p2o3"))
        buttons.add(QueryButton("p1o1", "p1o1"))

        // setup page flow
        mainPage = QueryPage("p1o1")
            .addItems(
                QueryPage("p2o1")
                    .addItems(
                        "p3o1",
                        "p3o2",
                    ),
                QueryPage("p2o2"),
                QueryPage("p2o3"),
            )
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
        val chatId = message.chatId.toString()
        val text = message.text
        logger.debug("[@${message.chat.userName}] Message: ${text}")

        if (text == "/more") {
            // reset user state
            val traveller = QueryGraphTraveller(mainPage, buttons)
            val reply = traveller
                .goHome()
                .replyNew(update)
            reply.replyToMessageId = message.messageId
            return reply
        } else {
            val reply = SendMessage()
            reply.chatId = chatId
            reply.replyToMessageId = message.messageId
            reply.text = "Echo \"${text}\""
            return reply
        }
    }

    fun handleCallbackQuery(update: Update): BotApiMethod<Serializable> {
        val traveller = QueryGraphTraveller(mainPage, buttons)
        val callbackData = update.callbackQuery.data
        traveller.travelTo(callbackData)
        return traveller.refresh(update)
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
