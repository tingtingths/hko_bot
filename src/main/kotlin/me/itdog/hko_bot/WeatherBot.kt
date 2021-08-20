package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import me.itdog.hko_bot.api.HongKongObservatory
import me.itdog.hko_bot.api.model.WeatherInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.api.interfaces.BotApiObject
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
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

    enum class Command(val command: String) {
        START("/start")
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
        buttons.add(QueryButton("天氣報告", "current_weather").apply {
            buildMessage = {
                Pair(ReplyMode.NEW_MESSAGE_AND_BACK, buildCurrentWeather(cache.get(Cache.KEY_GENERAL_INFO) as WeatherInfo))
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
                        "current_weather",
                        "p3o2",
                    ),
                QueryPage("p2o2"),
                QueryPage("p2o3"),
            )
    }

    private fun buildCurrentWeather(info: WeatherInfo): String {
        return info.let {
            val obsTime = it.rhrread?.formattedObsTime
            val temperature = it.hko?.temperature
            val maxTemperature = it.hko?.homeMaxTemperature
            val minTemperature = it.hko?.homeMinTemperature
            val rh = it.hko?.rh
            val uvIdx = it.rhrread?.uVIndex
            val uvIntensity = it.rhrread?.intensity

            "觀測時間: ${obsTime}\n" +
            "氣溫: ${temperature}°C\n" +
            "最高氣溫: ${maxTemperature}°C\n" +
            "最低氣溫: ${minTemperature}°C\n" +
            "相對濕度: ${rh}%\n" +
            "紫外線指數: ${uvIdx}\n" +
            "紫外線強度: ${uvIntensity}"
        }
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

        return if (Command.START.command.equals(text, ignoreCase = true)) {
            // reset user state
            val traveller = QueryGraphTraveller(mainPage, buttons)
            val reply = traveller
                .goHome()
                .replyNew(update)
            reply.replyToMessageId = message.messageId
            reply
        } else {
            val reply = SendMessage()
            reply.chatId = chatId
            reply.replyToMessageId = message.messageId
            reply.text = "Echo \"${text}\""
            reply
        }
    }

    fun handleCallbackQuery(update: Update): List<BotApiMethod<*>> {
        val traveller = QueryGraphTraveller(mainPage, buttons)
        val callbackData = update.callbackQuery.data
        traveller.travelTo(callbackData)
        return traveller.react(update)
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
            val replies = bot.handleCallbackQuery(update)
            for (reply in replies) {
                if (reply is SendMessage) execute(reply)
                else if (reply is SendDocument) execute(reply)
                else if (reply is SendPhoto) execute(reply)
                else if (reply is SendVideo) execute(reply)
                else if (reply is SendVideoNote) execute(reply)
                else if (reply is SendSticker) execute(reply)
                else if (reply is SendAudio) execute(reply)
                else if (reply is SendVoice) execute(reply)
                else if (reply is SendAnimation) execute(reply)
                else if (reply is EditMessageText) execute(reply)
                else throw Exception("Unsupported reply type ${reply}")
            }
        }
    }
}
