package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.gson.Gson
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


enum class BotLocale {
    ZH_HK, EN_UK
}

class WeatherBotBuilder {

    private var token: String? = null
    private var username: String? = null
    private var isWebhook = false

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
        return if (isWebhook) {
            PollingWeatherBot(token!!, username!!) // TODO
        } else {
            PollingWeatherBot(token!!, username!!) // TODO
        }
    }
}

open class WeatherBot {

    class WeatherMessageComposer(private val localiser: Localiser) {

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

        private fun formatFlwTime(flw: Flw): String {
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

                "${localiser.get(Localiser.Message.OBSERVATION_TIME)}: ${obsTime}\n" +
                        "${localiser.get(Localiser.Message.TEMPERATURE)}: ${temperature}°C\n" +
                        "${localiser.get(Localiser.Message.MAX_TEMPERATURE)}: ${maxTemperature}°C\n" +
                        "${localiser.get(Localiser.Message.MIN_TEMPERATURE)}: ${minTemperature}°C\n" +
                        "${localiser.get(Localiser.Message.RELATIVE_HUMIDITY)}: ${rh}%\n" +
                        "${localiser.get(Localiser.Message.UV_INDEX)}: ${uvIdx}\n" +
                        "${localiser.get(Localiser.Message.UV_INTENSITY)}: $uvIntensity"
            }
        }

        fun composeGeneralWeatherInfoMarkdown(info: WeatherInfo): String {
            return info.let {
                var ret = if (info.flw != null) "_${escapeMarkdown(formatFlwTime(info.flw!!))}_\n\n" else ""
                ret += "${escapeMarkdown(it.flw?.generalSituation as String)}\n\n" +
                        "__*${escapeMarkdown(it.flw?.forecastPeriod as String)}*__\n" +
                        "${escapeMarkdown(it.flw?.forecastDesc as String)}\n\n" +
                        "__*${escapeMarkdown(it.flw?.outlookTitle as String)}*__\n" +
                        escapeMarkdown(it.flw?.outlookContent as String)
                ret
            }
        }
    }

    class Localiser(private val botLocale: BotLocale) {

        companion object {
            val localisations = HashMap<BotLocale, Map<Message, String>>()
        }

        enum class Message {
            CURRENT_WEATHER_BTN,
            GENERAL_WEATHER_FORECAST_BTN,
            ABOUT_BTN,
            LANGUAGE_BTN,
            SETTINGS_BTN,
            NINE_DAYS_FORECAST_BTN,
            OTHERS_BTN,
            NOTIFICATION_BTN,
            OBSERVATION_TIME,
            TEMPERATURE,
            MAX_TEMPERATURE,
            MIN_TEMPERATURE,
            RELATIVE_HUMIDITY,
            UV_INDEX,
            UV_INTENSITY,
            LANG_CHI,
            LANG_ENG,
        }

        init {
            localisations[BotLocale.ZH_HK] = mapOf(
                Pair(Message.CURRENT_WEATHER_BTN, "天氣報告"),
                Pair(Message.GENERAL_WEATHER_FORECAST_BTN, "天氣概況"),
                Pair(Message.ABOUT_BTN, "關於<BOT NAME>"),
                Pair(Message.SETTINGS_BTN, "設定"),
                Pair(Message.LANGUAGE_BTN, "中↔ENG"),
                Pair(Message.OBSERVATION_TIME, "觀測時間"),
                Pair(Message.TEMPERATURE, "氣溫"),
                Pair(Message.MAX_TEMPERATURE, "最高氣溫"),
                Pair(Message.MIN_TEMPERATURE, "最低氣溫"),
                Pair(Message.RELATIVE_HUMIDITY, "相對濕度"),
                Pair(Message.UV_INDEX, "紫外線指數"),
                Pair(Message.UV_INTENSITY, "紫外線強度"),
                Pair(Message.LANG_CHI, "中文"),
                Pair(Message.LANG_ENG, "英文"),
                Pair(Message.NOTIFICATION_BTN, "通知"),
                Pair(Message.NINE_DAYS_FORECAST_BTN, "九天天氣預報"),
                Pair(Message.OTHERS_BTN, "其他"),
            )
            localisations[BotLocale.EN_UK] = mapOf(
                Pair(Message.CURRENT_WEATHER_BTN, "Current Weather"),
                Pair(Message.GENERAL_WEATHER_FORECAST_BTN, "General Situation"),
                Pair(Message.ABOUT_BTN, "About <BOT NAME>"),
                Pair(Message.SETTINGS_BTN, "Settings"),
                Pair(Message.LANGUAGE_BTN, "中↔ENG"),
                Pair(Message.OBSERVATION_TIME, "Obs. Time"),
                Pair(Message.TEMPERATURE, "Air temperature"),
                Pair(Message.MAX_TEMPERATURE, "Max temperature"),
                Pair(Message.MIN_TEMPERATURE, "Min temperature"),
                Pair(Message.RELATIVE_HUMIDITY, "Relative humidity"),
                Pair(Message.UV_INDEX, "UV Index"),
                Pair(Message.UV_INTENSITY, "UV Intensity"),
                Pair(Message.LANG_CHI, "Chinese"),
                Pair(Message.LANG_ENG, "English"),
                Pair(Message.NOTIFICATION_BTN, "Notification"),
                Pair(Message.NINE_DAYS_FORECAST_BTN, "9-day Forecast"),
                Pair(Message.OTHERS_BTN, "Others"),
            )
        }

        fun get(message: Message, botLocale: BotLocale = this.botLocale): String {
            return localisations[botLocale]?.get(message) ?: "NO_MESSAGE_FOUND"
        }
    }

    enum class Cache {
        GENERAL_INFO,
        GENERAL_INFO_ENG,
        WARNING_INFO,
        WARNING_INFO_ENG,
    }

    enum class Command(val command: String) {
        START("/start")
    }

    private val api = HongKongObservatory()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val cache: LoadingCache<Cache, Any> = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(CacheLoader.from { key ->
            if (Cache.GENERAL_INFO == key || Cache.GENERAL_INFO_ENG == key) {
                api.getGeneralInfo(isEnglish = Cache.GENERAL_INFO_ENG == key)
            } else {
                api.getWarningInfo(isEnglish = Cache.WARNING_INFO_ENG == key)
            }
        })
    private val buttons = mutableListOf<QueryButton>()
    private val mainPage: QueryPage
    private val localisers = HashMap<BotLocale, Localiser>()
    private val composers = HashMap<BotLocale, WeatherMessageComposer>()

    init {
        // build localised assets
        for (locale in BotLocale.values()) {
            val localiser = Localiser(locale)
            localisers[locale] = localiser
            composers[locale] = WeatherMessageComposer(localiser)
        }

        // build buttons
        buttons.add(QueryButton(
            { userId -> localisers[getUserLocale(userId)]!!.get(Localiser.Message.CURRENT_WEATHER_BTN) },
            "current_weather"
        ).apply {
            buildMessage = {
                val locale = getUserLocale(getUser(it).id)
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK,
                    composers[locale]!!.composeCurrentWeatherMarkdown(requestGeneralInfo(locale))
                )
            }
        })
        buttons.add(
            QueryButton(
                { userId ->
                    localisers[getUserLocale(userId)]!!.get(Localiser.Message.GENERAL_WEATHER_FORECAST_BTN)
                },
                "general_weather"
            ).apply {
                buildMessage = {
                    val locale = getUserLocale(getUser(it).id)
                    Pair(
                        ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN,
                        composers[locale]!!.composeGeneralWeatherInfoMarkdown(requestGeneralInfo(locale))
                    )
                }
            })
        buttons.add(
            QueryButton(
                { userId -> localisers[getUserLocale(userId)]!!.get(Localiser.Message.ABOUT_BTN) },
                "about"
            ).apply {
                buildMessage = {
                    Pair(
                        ReplyMode.UPDATE_QUERY,
                        "Version 0.0.1"
                    )
                }
            })
        buttons.add(
            QueryButton(
                { userId -> localisers[getUserLocale(userId)]!!.get(Localiser.Message.SETTINGS_BTN) },
                "settings"
            )
        )
        buttons.add(
            QueryButton(
                { userId -> localisers[getUserLocale(userId)]!!.get(Localiser.Message.NOTIFICATION_BTN) },
                "notification"
            )
        )
        buttons.add(
            QueryButton(
                { userId -> localisers[getUserLocale(userId)]!!.get(Localiser.Message.NINE_DAYS_FORECAST_BTN) },
                "9_day_forecast"
            )
        )
        buttons.add(
            QueryButton(
                { userId -> localisers[getUserLocale(userId)]!!.get(Localiser.Message.OTHERS_BTN) },
                "others"
            )
        )
        buttons.add(
            QueryButton(
                { userId ->
                    "${localisers[getUserLocale(userId)]!!.get(Localiser.Message.LANGUAGE_BTN)}: ${
                        localisers[getUserLocale(userId)]!!.get(
                            if (getUserLocale(userId) == BotLocale.ZH_HK) {
                                Localiser.Message.LANG_CHI
                            } else {
                                Localiser.Message.LANG_ENG
                            }
                        )
                    }"
                },
                "language"
            ).apply {
                buildMessage = {
                    val userId = getUser(it).id
                    if (getUserLocale(userId) == BotLocale.ZH_HK) setUserLocale(userId, BotLocale.EN_UK)
                    else setUserLocale(userId, BotLocale.ZH_HK)
                    Pair(ReplyMode.RERENDER_QUERY, "")
                }
            })
        buttons.add(QueryButton({ "" }, "landing", "<BOT NAME>"))

        // setup page flow
        mainPage = QueryPage("landing")
            .addItems(
                QueryPage("current_weather"),
                QueryPage("general_weather"),
                QueryPage("9_day_forecast"),
                QueryPage("others"),
                QueryPage("settings")
                    .addItems("notification")
                    .addItems("language"),
                QueryPage("about"),
            )
    }

    private fun getUserSettings(userId: Long): UserSettings {
        return Global.userSettings.get(userId)
    }

    private fun getUserLocale(userId: Long): BotLocale {
        return getUserSettings(userId).botLocale
    }

    private fun setUserLocale(userId: Long, locale: BotLocale) {
        Global.userSettings.get(userId).apply { botLocale = locale }
    }

    private fun requestGeneralInfo(botLocale: BotLocale): WeatherInfo {
        return cache.get(
            if (botLocale == BotLocale.ZH_HK) Cache.GENERAL_INFO else Cache.GENERAL_INFO_ENG
        ) as WeatherInfo
    }

    private fun requestWarningInfo(botLocale: BotLocale): WeatherInfo {
        return cache.get(
            if (botLocale == BotLocale.ZH_HK) Cache.WARNING_INFO else Cache.WARNING_INFO_ENG
        ) as WeatherInfo
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
        logger.debug("[@${message.chat.userName}] Message: $text")

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

class PollingWeatherBot(private val token: String, private val username: String) : TelegramLongPollingBot() {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val bot = WeatherBot()
    private val gson = Gson()

    override fun getBotToken(): String {
        return token
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun onUpdateReceived(update: Update?) {
        if (update == null) return

        val userId = getUser(update).id
        val userSettingsSnapshot = gson.toJson(Global.userSettings.get(userId))

        when {
            update.hasInlineQuery() -> {
                logger.debug("($userId), inline query: ${update.inlineQuery.query}")
                execute(bot.handleInlineQuery(update))
            }
            update.hasMessage() -> {
                logger.debug("($userId), message: ${if (update.message.hasText()) update.message.text else "NON_TEXT_MESSAGE"}")
                execute(bot.handleMessage(update))
            }
            update.hasCallbackQuery() -> {
                logger.debug("($userId), callback query: ${update.callbackQuery.data}")
                val replies = bot.handleCallbackQuery(update)
                for (reply in replies) {
                    when (reply) {
                        is SendMessage -> execute(reply)
                        is SendDocument -> execute(reply)
                        is SendPhoto -> execute(reply)
                        is SendVideo -> execute(reply)
                        is SendVideoNote -> execute(reply)
                        is SendSticker -> execute(reply)
                        is SendAudio -> execute(reply)
                        is SendVoice -> execute(reply)
                        is SendAnimation -> execute(reply)
                        is EditMessageText -> execute(reply)
                        else -> throw Exception("Unsupported reply type $reply")
                    }
                }
            }
        }

        if (gson.toJson(Global.userSettings.get(userId)) != userSettingsSnapshot)
            Global.persistent.setUserSettings(userId, Global.userSettings.get(userId))
    }
}
