package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import me.itdog.hko_bot.api.HongKongObservatory
import me.itdog.hko_bot.api.model.Flw
import me.itdog.hko_bot.api.model.WarningBase
import me.itdog.hko_bot.api.model.WarningInfo
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
import org.telegram.telegrambots.meta.bots.AbsSender
import org.telegram.telegrambots.meta.generics.TelegramBot
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.concurrent.fixedRateTimer


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

fun escapeMarkdown(message: String): String {
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

fun formatBulletinDateTime(date: String?, time: String?): String {
    var ret = ""

    if (date != null) {
        val inputFormat = SimpleDateFormat("yyyyMMdd")
        val outputFormat = SimpleDateFormat("yyyy-MM-dd")
        ret += outputFormat.format(inputFormat.parse(date))
    }
    if (time != null) {
        val inputFormat = SimpleDateFormat("HHmm")
        val outputFormat = SimpleDateFormat("HH:mm")
        if (ret.isNotEmpty()) ret += " "
        ret += outputFormat.format(inputFormat.parse(time))
    }

    return ret
}

open class WeatherBot(private val notiferBot: AbsSender) {

    class WeatherMessageComposer(private val localiser: Localiser) {

        private fun formatFlwTime(flw: Flw): String {
            return formatBulletinDateTime(flw.bulletinDate, flw.bulletinTime)
        }

        fun composeCurrentWeatherMarkdown(info: WeatherInfo): String {
            return info.let {
                val obsTime = it.rhrread?.formattedObsTime
                val temperature = it.hko?.temperature
                val maxTemperature = it.hko?.homeMaxTemperature
                val minTemperature = it.hko?.homeMinTemperature
                val rh = it.hko?.rh
                val uvIdx = it.rhrread?.uVIndex?.trim('/')
                val uvIntensity = it.rhrread?.intensity

                "${localiser.get(Localiser.Message.OBSERVATION_TIME)}: ${obsTime}\n" +
                        "${localiser.get(Localiser.Message.TEMPERATURE)}: ${temperature}°C\n" +
                        "${localiser.get(Localiser.Message.MAX_TEMPERATURE)}: ${maxTemperature}°C\n" +
                        "${localiser.get(Localiser.Message.MIN_TEMPERATURE)}: ${minTemperature}°C\n" +
                        "${localiser.get(Localiser.Message.RELATIVE_HUMIDITY)}: ${rh}%\n" +
                        (if (uvIdx != null && uvIdx.isNotEmpty()) "${localiser.get(Localiser.Message.UV_INDEX)}: $uvIdx\n" else "") +
                        (if (uvIntensity != null && uvIntensity.isNotEmpty()) "${localiser.get(Localiser.Message.UV_INTENSITY)}: $uvIntensity" else "")
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

        fun composeWeatherWarningMarkdown(warning: WarningInfo): String {
            return warning.let {
                val content = warning.activeWarnings().stream()
                    .map {
                        val type = if (!it.type.isNullOrEmpty()) " - ${it.type}" else ""
                        val time = formatBulletinDateTime(it.bulletinDate, it.bulletinTime)
                        escapeMarkdown("${it.name}${type} ($time)")
                    }
                    .collect(Collectors.joining("\n"))
                "__*${escapeMarkdown(localiser.get(Localiser.Message.CURRENT_ACTIVE_WARNINGS))}*__ \\(${warning.activeWarnings().size}\\)\n$content"
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
            ACTIVE_WARN_BTN,
            ABOUT_BTN,
            LANGUAGE_BTN,
            SETTINGS_BTN,
            NINE_DAYS_FORECAST_BTN,
            OTHERS_BTN,
            NOTIFICATION_BTN,
            ENABLED,
            DISABLED,
            OBSERVATION_TIME,
            TEMPERATURE,
            MAX_TEMPERATURE,
            MIN_TEMPERATURE,
            RELATIVE_HUMIDITY,
            UV_INDEX,
            UV_INTENSITY,
            LANG_CHI,
            LANG_ENG,
            CURRENT_ACTIVE_WARNINGS
        }

        init {
            localisations[BotLocale.ZH_HK] = mapOf(
                Pair(Message.ACTIVE_WARN_BTN, "天氣警告"),
                Pair(Message.CURRENT_ACTIVE_WARNINGS, "天氣警告"),
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
                Pair(Message.ENABLED, "已啟用"),
                Pair(Message.DISABLED, "已停用"),
            )
            localisations[BotLocale.EN_UK] = mapOf(
                Pair(Message.ACTIVE_WARN_BTN, "Weather Warnings"),
                Pair(Message.CURRENT_ACTIVE_WARNINGS, "Warnings in force"),
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
                Pair(Message.ENABLED, "Enabled"),
                Pair(Message.DISABLED, "Disabled"),
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
    private val buildProperties: Properties = Properties()
    private var lastWarnCheckTime: Instant? = null
    private var receivedWarnings = hashSetOf<Pair<String, Instant>>()

    init {
        buildProperties.load(
            this::class.java.getResourceAsStream("/build.properties")
        )

        // build localised assets
        for (locale in BotLocale.values()) {
            val localiser = Localiser(locale)
            localisers[locale] = localiser
            composers[locale] = WeatherMessageComposer(localiser)
        }

        // build buttons
        buttons.add(QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(Localiser.Message.CURRENT_WEATHER_BTN) },
            "current_weather"
        ).apply {
            buildMessage = {
                val locale = getChatLocale(getChat(it).id)
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK,
                    composers[locale]!!.composeCurrentWeatherMarkdown(requestGeneralInfo(locale))
                )
            }
        })
        buttons.add(
            QueryButton(
                { chatId ->
                    localisers[getChatLocale(chatId)]!!.get(Localiser.Message.GENERAL_WEATHER_FORECAST_BTN)
                },
                "general_weather"
            ).apply {
                buildMessage = {
                    val locale = getChatLocale(getChat(it).id)
                    Pair(
                        ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN,
                        composers[locale]!!.composeGeneralWeatherInfoMarkdown(requestGeneralInfo(locale))
                    )
                }
            })
        buttons.add(
            QueryButton(
                { chatId -> localisers[getChatLocale(chatId)]!!.get(Localiser.Message.ABOUT_BTN) },
                "about"
            ).apply {
                buildMessage = {
                    val version = buildProperties.getProperty("version", "__VERSION__")
                    val timestamp = buildProperties.getProperty("timestamp", "0")
                    Pair(ReplyMode.UPDATE_QUERY, "$version+$timestamp")
                }
            })
        buttons.add(
            QueryButton(
                { chatId -> localisers[getChatLocale(chatId)]!!.get(Localiser.Message.SETTINGS_BTN) },
                "settings"
            )
        )
        buttons.add(
            QueryButton(
                { chatId ->
                    val settings = getChatSettings(chatId)
                    "${localisers[settings.botLocale]!!.get(Localiser.Message.NOTIFICATION_BTN)}: ${
                        localisers[settings.botLocale]!!.get(
                            if (settings.isNotificationEnabled) {
                                Localiser.Message.ENABLED
                            } else {
                                Localiser.Message.DISABLED
                            }
                        )
                    }"
                },
                "notification"
            ).apply {
                buildMessage = {
                    val chatId = getChat(it).id
                    val settings = getChatSettings(chatId)
                    settings.isNotificationEnabled = !settings.isNotificationEnabled
                    Pair(ReplyMode.RERENDER_QUERY, "")
                }
            }
        )
        buttons.add(
            QueryButton(
                { chatId -> localisers[getChatLocale(chatId)]!!.get(Localiser.Message.NINE_DAYS_FORECAST_BTN) },
                "9_day_forecast"
            )
        )
        buttons.add(
            QueryButton(
                { chatId -> localisers[getChatLocale(chatId)]!!.get(Localiser.Message.OTHERS_BTN) },
                "others"
            )
        )
        buttons.add(
            QueryButton(
                { chatId ->
                    "${localisers[getChatLocale(chatId)]!!.get(Localiser.Message.LANGUAGE_BTN)}: ${
                        localisers[getChatLocale(chatId)]!!.get(
                            if (getChatLocale(chatId) == BotLocale.ZH_HK) {
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
                    val chatId = getChat(it).id
                    if (getChatLocale(chatId) == BotLocale.ZH_HK) setChatLocale(chatId, BotLocale.EN_UK)
                    else setChatLocale(chatId, BotLocale.ZH_HK)
                    Pair(ReplyMode.RERENDER_QUERY, "")
                }
            })
        buttons.add(QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(Localiser.Message.ACTIVE_WARN_BTN) },
            "active_warnings"
        ).apply {
            buildMessage = {
                val chatId = getChat(it).id
                val locale = getChatLocale(chatId)
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN,
                    composers[locale]!!.composeWeatherWarningMarkdown(requestWarningInfo(locale))
                )
            }
        })
        buttons.add(QueryButton({ "" }, "landing", "<BOT NAME>"))

        // setup page flow
        mainPage = QueryPage("landing")
            .addItems(
                QueryPage("current_weather"),
                QueryPage("general_weather"),
                QueryPage("9_day_forecast"),
                QueryPage("active_warnings"),
                QueryPage("others"),
                QueryPage("settings")
                    .addItems("notification")
                    .addItems("language"),
                QueryPage("about"),
            )

        // schedule check warning
        fixedRateTimer("weather_warning_check_task", true, period = 20000L) {
            if (lastWarnCheckTime == null) {
                lastWarnCheckTime = Instant.now()
            } else {
                val fmt = SimpleDateFormat("yyyyMMddHHmm")
                val newWarnings = warningsCheck(BotLocale.ZH_HK)
                val newWarningsEng = warningsCheck(BotLocale.EN_UK)
                val checkTime = Instant.now()

                receivedWarnings.addAll(newWarnings.map {
                    val bulletinTime = fmt.parse(it.bulletinDate + it.bulletinTime).toInstant()
                    Pair(it.name!!, bulletinTime)
                })
                receivedWarnings.addAll(newWarningsEng.map {
                    val bulletinTime = fmt.parse(it.bulletinDate + it.bulletinTime).toInstant()
                    Pair(it.name!!, bulletinTime)
                })
                // clean up old cache
                receivedWarnings.removeIf {
                    it.second.isBefore(lastWarnCheckTime)
                }

                lastWarnCheckTime = checkTime
                var allChatSettings: Map<Long, ChatSettings>? = null
                if (newWarnings.isNotEmpty()) {
                    allChatSettings = Global.persistent.getAllChatsSettings()
                    logger.debug("Found ${newWarnings.size} new warnings (${BotLocale.ZH_HK}), ${newWarnings.map { it.name }}")
                    val text = "__*新天氣警告!!!*__\n" +
                            newWarnings.joinToString("\n") { warning ->
                                val type = if (!warning.type.isNullOrEmpty()) " - ${warning.type}" else ""
                                val time = formatBulletinDateTime(warning.bulletinDate, warning.bulletinTime)
                                escapeMarkdown("${warning.name}${type} ($time)")
                            }

                    allChatSettings.filter {
                        val settings = it.value
                        settings.isNotificationEnabled && settings.botLocale == BotLocale.ZH_HK
                    }.map {
                        val msg = SendMessage()
                        msg.chatId = it.key.toString()
                        msg.parseMode = "MarkdownV2"
                        msg.text = text
                        notiferBot.executeAsync(msg)
                    }
                }
                if (newWarningsEng.isNotEmpty()) {
                    if (allChatSettings == null) allChatSettings = Global.persistent.getAllChatsSettings()
                    logger.debug("Found ${newWarningsEng.size} new warnings (${BotLocale.EN_UK}), ${newWarningsEng.map { it.name }}")
                    val text = "__*New Weather Warning!!!*__\n" +
                            newWarningsEng.joinToString("\n") { warning ->
                                val type = if (!warning.type.isNullOrEmpty()) " - ${warning.type}" else ""
                                val time = formatBulletinDateTime(warning.bulletinDate, warning.bulletinTime)
                                escapeMarkdown("${warning.name}${type} ($time)")
                            }

                    allChatSettings.filter {
                        val settings = it.value
                        settings.isNotificationEnabled && settings.botLocale == BotLocale.EN_UK
                    }.map {
                        val msg = SendMessage()
                        msg.chatId = it.key.toString()
                        msg.parseMode = "MarkdownV2"
                        msg.text = text
                        notiferBot.executeAsync(msg)
                    }
                }
            }
        }
    }

    private fun warningsCheck(locale: BotLocale): List<WarningBase> {
        val warningInfo = api.getWarningInfo(locale == BotLocale.EN_UK)
        val fmt = SimpleDateFormat("yyyyMMddHHmm")
        return warningInfo.activeWarnings().stream()
            .filter {
                !(it.name.isNullOrEmpty() || it.bulletinDate.isNullOrEmpty() || it.bulletinTime.isNullOrEmpty())
            }
            .filter {
                val bulletinTime = fmt.parse(it.bulletinDate + it.bulletinTime).toInstant()
                !bulletinTime.isBefore(lastWarnCheckTime)
                        && !receivedWarnings.contains(Pair(it.name, bulletinTime))
            }
            .collect(Collectors.toList())
    }

    private fun getChatSettings(id: Long): ChatSettings {
        return Global.chatSettings.get(id)
    }

    private fun getChatLocale(id: Long): BotLocale {
        return getChatSettings(id).botLocale
    }

    private fun setChatLocale(id: Long, locale: BotLocale) {
        Global.chatSettings.get(id).apply { botLocale = locale }
    }

    private fun requestGeneralInfo(botLocale: BotLocale): WeatherInfo {
        return cache.get(
            if (botLocale == BotLocale.ZH_HK) Cache.GENERAL_INFO else Cache.GENERAL_INFO_ENG
        ) as WeatherInfo
    }

    private fun requestWarningInfo(botLocale: BotLocale): WarningInfo {
        return cache.get(
            if (botLocale == BotLocale.ZH_HK) Cache.WARNING_INFO else Cache.WARNING_INFO_ENG
        ) as WarningInfo
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
    private val bot = WeatherBot(this)

    override fun getBotToken(): String {
        return token
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun onUpdateReceived(update: Update?) {
        if (update == null || !(update.hasInlineQuery() || update.hasMessage() || update.hasCallbackQuery())) return
        val chatId = getChat(update).id
        val userId = getUser(update).id
        val chatSettingHash = Global.chatSettings.get(chatId).hashCode()
        val completeAction = Runnable {
            if (Global.chatSettings.get(chatId).hashCode() != chatSettingHash) {
                Global.persistent.saveChatSettings(chatId, Global.chatSettings.get(chatId))
            }
        }

        when {
            update.hasInlineQuery() -> {
                logger.debug("($userId), inline query: ${update.inlineQuery.query}")
                executeAsync(bot.handleInlineQuery(update)).thenRun(completeAction)
            }
            update.hasMessage() -> {
                logger.debug("($userId), message: ${if (update.message.hasText()) update.message.text else "NON_TEXT_MESSAGE"}")
                executeAsync(bot.handleMessage(update)).thenRun(completeAction)
            }
            update.hasCallbackQuery() -> {
                logger.debug("($userId), callback query: ${update.callbackQuery.data}")
                val replies = bot.handleCallbackQuery(update)
                for (reply in replies) {
                    when (reply) {
                        is SendMessage -> executeAsync(reply).thenRun(completeAction)
                        is SendDocument -> executeAsync(reply).thenRun(completeAction)
                        is SendPhoto -> executeAsync(reply).thenRun(completeAction)
                        is SendVideo -> executeAsync(reply).thenRun(completeAction)
                        is SendVideoNote -> executeAsync(reply).thenRun(completeAction)
                        is SendSticker -> executeAsync(reply).thenRun(completeAction)
                        is SendAudio -> executeAsync(reply).thenRun(completeAction)
                        is SendVoice -> executeAsync(reply).thenRun(completeAction)
                        is SendAnimation -> executeAsync(reply).thenRun(completeAction)
                        is EditMessageText -> executeAsync(reply).thenRun(completeAction)
                        else -> throw Exception("Unsupported reply type $reply")
                    }
                }
            }
        }
    }
}
