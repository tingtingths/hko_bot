package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import me.itdog.hko_bot.api.HongKongObservatory
import me.itdog.hko_bot.api.model.Flw
import me.itdog.hko_bot.api.model.WeatherInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle
import org.telegram.telegrambots.meta.generics.TelegramBot
import java.text.SimpleDateFormat
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

    class WeatherMessageComposer {

        companion object {
            private fun escapeMarkdown(message: String): String {
                return message.replace("_", "\\_")
                    .replace("*", "\\*")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace("~", "\\~")
                    .replace("`", "\\`")
                    .replace(">", "\\>")
                    .replace("#", "\\#")
                    .replace("+", "\\+")
                    .replace("-", "\\-")
                    .replace("=", "\\=")
                    .replace("|", "\\|")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace(".", "\\.")
                    .replace("!", "\\!")
            }

            fun formatFlwTime(flw: Flw): String {
                var ret = ""

                if (flw.bulletinDate != null) {
                    val inputFormat = SimpleDateFormat("yyyyMMdd")
                    val outputFormat = SimpleDateFormat("yyyy-MM-dd")
                    ret += outputFormat.format(inputFormat.parse(flw.bulletinDate))
                }
                if (flw.bulletinTime != null) {
                    val inputFormat = SimpleDateFormat("HHmm")
                    val outputFormat = SimpleDateFormat("HH:mm")
                    if (ret.isNotEmpty()) ret += " "
                    ret += outputFormat.format(inputFormat.parse(flw.bulletinTime))
                }

                return ret
            }

            fun composeCurrentWeatherMarkdown(info: WeatherInfo): String {
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

            fun composeGeneralWeatherInfoMarkdown(info: WeatherInfo): String {
                return info.let {
                    var ret = if (info.flw != null) "_${escapeMarkdown(formatFlwTime(info.flw!!))}_\n\n" else ""
                    ret += "${escapeMarkdown(it.flw?.generalSituation as String)}\n\n" +
                            "__*${escapeMarkdown(it.flw?.forecastPeriod as String)}*__\n" +
                            "${escapeMarkdown(it.flw?.forecastDesc as String)}\n\n" +
                            "__*${escapeMarkdown(it.flw?.outlookTitle as String)}*__\n" +
                            "${escapeMarkdown(it.flw?.outlookContent as String)}"
                    ret
                }
            }
        }
    }

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
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK,
                    WeatherMessageComposer.composeCurrentWeatherMarkdown(cache.get(Cache.KEY_GENERAL_INFO) as WeatherInfo)
                )
            }
        })
        buttons.add(QueryButton("天氣概況", "general_weather").apply {
            buildMessage = {
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN,
                    WeatherMessageComposer.composeGeneralWeatherInfoMarkdown(cache.get(Cache.KEY_GENERAL_INFO) as WeatherInfo)
                )
            }
        })
        buttons.add(QueryButton("Info", "info").apply {
            buildMessage = {
                Pair(
                    ReplyMode.UPDATE_QUERY,
                    "Version 0.0.1"
                )
            }
        })
        buttons.add(QueryButton("", "landing", "HKO Weather"))

        // setup page flow
        mainPage = QueryPage("landing")
            .addItems(
                QueryPage("current_weather"),
                QueryPage("general_weather"),
                QueryPage("info"),
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
