package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import me.itdog.hko_bot.api.HongKongObservatory
import me.itdog.hko_bot.api.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.bots.TelegramWebhookBot
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
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.concurrent.fixedRateTimer


enum class BotLocale {
    ZH_HK, EN_UK
}

class WeatherBotBuilder {

    val logger = LoggerFactory.getLogger(this::class.java)

    enum class UpdateType {
        LONG_POLL, WEBHOOK
    }

    private var token: String? = null
    private var username: String? = null
    private var webhookPath: String? = null
    private var botOptions = DefaultBotOptions()
    private var updateType = UpdateType.LONG_POLL

    fun token(token: String): WeatherBotBuilder {
        this.token = token
        return this
    }

    fun username(username: String): WeatherBotBuilder {
        this.username = username
        return this
    }

    fun webhookPath(webhookPath: String): WeatherBotBuilder {
        this.webhookPath = webhookPath
        return this
    }

    fun botOptions(botOptions: DefaultBotOptions): WeatherBotBuilder {
        this.botOptions = botOptions
        return this
    }

    fun updateType(updateType: UpdateType): WeatherBotBuilder {
        this.updateType = updateType
        return this
    }

    fun build(): TelegramBot {
        if (token.isNullOrEmpty()) throw IllegalArgumentException("Token must be supplied")
        if (username.isNullOrEmpty()) throw IllegalArgumentException("Username must be supplied")
        if (updateType == UpdateType.WEBHOOK && webhookPath.isNullOrEmpty())
            throw IllegalArgumentException("Webhook path must be supplied")

        // build
        return when (updateType) {
            UpdateType.WEBHOOK -> {
                WebhookWeatherBot(token!!, username!!, webhookPath!!, botOptions)
            }
            UpdateType.LONG_POLL -> PollingWeatherBot(token!!, username!!, botOptions)
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

open class WeatherBot(val telegramBot: AbsSender) {

    private val TROPICAL_CYCLONE_BTN_PREFIX = "tc_"

    enum class RenderMode {
        TEXT, MARKDOWN
    }

    enum class LocaliseComponent {
        CURRENT_WEATHER_BTN,
        GENERAL_WEATHER_FORECAST_BTN,
        ACTIVE_WARN_BTN,
        ABOUT_BTN,
        LANGUAGE_BTN,
        SETTINGS_BTN,
        TROPICAL_CYCLONE_BTN,
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

    companion object {
        val localisations = HashMap<BotLocale, Map<LocaliseComponent, String>>()
    }

    inner class WeatherMessageComposer(private val localiser: Localiser) {

        private fun formatFlwTime(flw: Flw): String {
            return formatBulletinDateTime(flw.bulletinDate, flw.bulletinTime)
        }

        fun composeCurrentWeather(info: WeatherInfo): String {
            return info.let {
                val obsTime = it.rhrread?.formattedObsTime
                val temperature = it.hko?.temperature
                val maxTemperature = it.hko?.homeMaxTemperature
                val minTemperature = it.hko?.homeMinTemperature
                val rh = it.hko?.rh
                val uvIdx = it.rhrread?.uVIndex?.trim('/')
                val uvIntensity = it.rhrread?.intensity

                "${localiser.get(LocaliseComponent.OBSERVATION_TIME)}: ${obsTime}\n" +
                        "${localiser.get(LocaliseComponent.TEMPERATURE)}: ${temperature}°C\n" +
                        "${localiser.get(LocaliseComponent.MAX_TEMPERATURE)}: ${maxTemperature}°C\n" +
                        "${localiser.get(LocaliseComponent.MIN_TEMPERATURE)}: ${minTemperature}°C\n" +
                        "${localiser.get(LocaliseComponent.RELATIVE_HUMIDITY)}: ${rh}%\n" +
                        (if (uvIdx != null && uvIdx.isNotEmpty()) "${localiser.get(LocaliseComponent.UV_INDEX)}: $uvIdx\n" else "") +
                        (if (uvIntensity != null && uvIntensity.isNotEmpty()) "${localiser.get(LocaliseComponent.UV_INTENSITY)}: $uvIntensity" else "")
            }
        }

        fun composeGeneralWeatherInfo(info: WeatherInfo, renderMode: RenderMode = RenderMode.TEXT): String {
            fun String?.pad(prefix: String = "", suffix: String = ""): String {
                if (this != null && this.isNotEmpty()) {
                    return "$prefix$this$suffix"
                }
                return this ?: ""
            }

            fun String?.escapeMarkdown(): String? {
                if (this != null) return escapeMarkdown(this)
                return this
            }

            return info.let {
                var ret = ""
                when (renderMode) {
                    RenderMode.TEXT -> {
                        ret += if (info.flw != null)
                            formatFlwTime(info.flw!!).pad("\n\n")
                        else
                            ""
                        ret += it.flw?.generalSituation.pad(suffix = "\n\n") +
                                it.flw?.forecastPeriod.pad(suffix = "\n") +
                                it.flw?.forecastDesc.pad(suffix = "\n\n") +
                                it.flw?.outlookTitle.pad(suffix = "\n") +
                                (it.flw?.outlookContent ?: "")
                    }
                    RenderMode.MARKDOWN -> {
                        ret += if (info.flw != null)
                            formatFlwTime(info.flw!!).escapeMarkdown().pad("_", "_\n\n")
                        else
                            ""
                        ret += it.flw?.generalSituation.escapeMarkdown().pad(suffix = "\n\n") +
                                it.flw?.forecastPeriod.escapeMarkdown().pad("__*", "*__\n") +
                                it.flw?.forecastDesc.escapeMarkdown().pad(suffix = "\n\n") +
                                it.flw?.outlookTitle.escapeMarkdown().pad("__*", "*__\n") +
                                it.flw?.outlookContent.escapeMarkdown()
                    }
                }
                ret
            }
        }

        fun composeWeatherWarning(warning: WarningInfo, renderMode: RenderMode = RenderMode.TEXT): String {
            return warning.let {
                val content = warning.activeWarnings().stream()
                    .map {
                        val type = if (!it.type.isNullOrEmpty()) " - ${it.type}" else ""
                        val time = formatBulletinDateTime(it.bulletinDate, it.bulletinTime)
                        val warningLine = "${it.name}${type} ($time)"
                        when (renderMode) {
                            RenderMode.TEXT -> warningLine
                            RenderMode.MARKDOWN -> escapeMarkdown(warningLine)
                        }
                    }
                    .collect(Collectors.joining("\n"))
                when (renderMode) {
                    RenderMode.TEXT -> "${localiser.get(LocaliseComponent.CURRENT_ACTIVE_WARNINGS)} (${warning.activeWarnings().size})\n$content"
                    RenderMode.MARKDOWN -> "__*${escapeMarkdown(localiser.get(LocaliseComponent.CURRENT_ACTIVE_WARNINGS))}*__ \\(${warning.activeWarnings().size}\\)\n$content"
                }
            }
        }

        fun composeTropicalCycloneMessage(tc: TropicalCyclone): String {
            val TC_DATATYPE_F4 = "F4";
            val TC_DATATYPE_F3 = "F3";
            val TC_DATATYPE_F3_F4 = "F3_F4";

            return when (tc.datatype) {
                TC_DATATYPE_F3_F4, TC_DATATYPE_F4 -> {
                    "http://www.hko.gov.hk/wxinfo/currwx/fp_${tc.tcId}.png"
                }
                TC_DATATYPE_F3 -> {
                    "http://www.hko.gov.hk/probfcst/${tc.filename}"
                }
                else -> {
                    ""
                }
            }
        }
    }

    inner class Localiser(private val botLocale: BotLocale) {

        init {
            localisations[BotLocale.ZH_HK] = mapOf(
                Pair(LocaliseComponent.ACTIVE_WARN_BTN, "天氣警告"),
                Pair(LocaliseComponent.CURRENT_ACTIVE_WARNINGS, "天氣警告"),
                Pair(LocaliseComponent.CURRENT_WEATHER_BTN, "天氣報告"),
                Pair(LocaliseComponent.GENERAL_WEATHER_FORECAST_BTN, "天氣概況"),
                Pair(LocaliseComponent.ABOUT_BTN, "關於${telegramBot.me.firstName}"),
                Pair(LocaliseComponent.SETTINGS_BTN, "設定"),
                Pair(LocaliseComponent.LANGUAGE_BTN, "中↔ENG"),
                Pair(LocaliseComponent.OBSERVATION_TIME, "觀測時間"),
                Pair(LocaliseComponent.TEMPERATURE, "氣溫"),
                Pair(LocaliseComponent.MAX_TEMPERATURE, "最高氣溫"),
                Pair(LocaliseComponent.MIN_TEMPERATURE, "最低氣溫"),
                Pair(LocaliseComponent.RELATIVE_HUMIDITY, "相對濕度"),
                Pair(LocaliseComponent.UV_INDEX, "紫外線指數"),
                Pair(LocaliseComponent.UV_INTENSITY, "紫外線強度"),
                Pair(LocaliseComponent.LANG_CHI, "中文"),
                Pair(LocaliseComponent.LANG_ENG, "英文"),
                Pair(LocaliseComponent.NOTIFICATION_BTN, "通知"),
                Pair(LocaliseComponent.NINE_DAYS_FORECAST_BTN, "九天天氣預報"),
                Pair(LocaliseComponent.OTHERS_BTN, "其他"),
                Pair(LocaliseComponent.ENABLED, "已啟用"),
                Pair(LocaliseComponent.DISABLED, "已停用"),
                Pair(LocaliseComponent.TROPICAL_CYCLONE_BTN, "熱帶氣旋"),
            )
            localisations[BotLocale.EN_UK] = mapOf(
                Pair(LocaliseComponent.ACTIVE_WARN_BTN, "Weather Warnings"),
                Pair(LocaliseComponent.CURRENT_ACTIVE_WARNINGS, "Warnings in force"),
                Pair(LocaliseComponent.CURRENT_WEATHER_BTN, "Current Weather"),
                Pair(LocaliseComponent.GENERAL_WEATHER_FORECAST_BTN, "General Situation"),
                Pair(LocaliseComponent.ABOUT_BTN, "About ${telegramBot.me.firstName}"),
                Pair(LocaliseComponent.SETTINGS_BTN, "Settings"),
                Pair(LocaliseComponent.LANGUAGE_BTN, "中↔ENG"),
                Pair(LocaliseComponent.OBSERVATION_TIME, "Obs. Time"),
                Pair(LocaliseComponent.TEMPERATURE, "Air temperature"),
                Pair(LocaliseComponent.MAX_TEMPERATURE, "Max temperature"),
                Pair(LocaliseComponent.MIN_TEMPERATURE, "Min temperature"),
                Pair(LocaliseComponent.RELATIVE_HUMIDITY, "Relative humidity"),
                Pair(LocaliseComponent.UV_INDEX, "UV Index"),
                Pair(LocaliseComponent.UV_INTENSITY, "UV Intensity"),
                Pair(LocaliseComponent.LANG_CHI, "Chinese"),
                Pair(LocaliseComponent.LANG_ENG, "English"),
                Pair(LocaliseComponent.NOTIFICATION_BTN, "Notification"),
                Pair(LocaliseComponent.NINE_DAYS_FORECAST_BTN, "9-day Forecast"),
                Pair(LocaliseComponent.OTHERS_BTN, "Others"),
                Pair(LocaliseComponent.ENABLED, "Enabled"),
                Pair(LocaliseComponent.DISABLED, "Disabled"),
                Pair(LocaliseComponent.TROPICAL_CYCLONE_BTN, "Tropical Cyclones"),
            )
        }

        fun get(localiseComponent: LocaliseComponent, botLocale: BotLocale = this.botLocale): String {
            return localisations[botLocale]?.get(localiseComponent) ?: "NO_MESSAGE_FOUND"
        }
    }

    enum class Cache {
        GENERAL_INFO,
        GENERAL_INFO_ENG,
        WARNING_INFO,
        WARNING_INFO_ENG,
        TROPICAL_CYCLONE
    }

    enum class Command(val command: String) {
        START("/start")
    }

    private val api = HongKongObservatory()
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val apiCache: LoadingCache<Cache, Any> = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(CacheLoader.from { key ->
            when (key) {
                Cache.GENERAL_INFO -> {
                    api.getGeneralInfo(false)
                }
                Cache.GENERAL_INFO_ENG -> {
                    api.getGeneralInfo(true)
                }
                Cache.WARNING_INFO -> {
                    api.getWarningInfo(false)
                }
                Cache.WARNING_INFO_ENG -> {
                    api.getWarningInfo(true)
                }
                Cache.TROPICAL_CYCLONE -> {
                    api.getTropicalCyclones()
                }
                else -> {
                    null
                }
            }
        })
    private val queryButtons = ConcurrentHashMap<String, QueryButton>()
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
        queryButtons["current_weather"] = QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.CURRENT_WEATHER_BTN) },
            "current_weather"
        ).apply {
            buildMessage = {
                val locale = getChatLocale(getChat(it).id)
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK,
                    composers[locale]!!.composeCurrentWeather(requestGeneralInfo(locale))
                )
            }
        }
        queryButtons["general_weather"] = QueryButton(
            { chatId ->
                localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.GENERAL_WEATHER_FORECAST_BTN)
            },
            "general_weather"
        ).apply {
            buildMessage = {
                val locale = getChatLocale(getChat(it).id)
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN,
                    composers[locale]!!.composeGeneralWeatherInfo(requestGeneralInfo(locale), RenderMode.MARKDOWN)
                )
            }
        }
        queryButtons["about"] = QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.ABOUT_BTN) },
            "about"
        ).apply {
            buildMessage = {
                val version = Global.buildProperties.getProperty("version", "__VERSION__")
                val timestamp = Global.buildProperties.getProperty("timestamp", "0")
                Pair(ReplyMode.UPDATE_QUERY, "$version+$timestamp")
            }
        }
        queryButtons["settings"] = QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.SETTINGS_BTN) },
            "settings"
        )
        queryButtons["notification"] = QueryButton(
            { chatId ->
                val settings = getChatSettings(chatId)
                "${localisers[settings.botLocale]!!.get(LocaliseComponent.NOTIFICATION_BTN)}: ${
                    localisers[settings.botLocale]!!.get(
                        if (settings.isNotificationEnabled) {
                            LocaliseComponent.ENABLED
                        } else {
                            LocaliseComponent.DISABLED
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
        queryButtons["9_day_forecast"] = QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.NINE_DAYS_FORECAST_BTN) },
            "9_day_forecast"
        )
        queryButtons["others"] = QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.OTHERS_BTN) },
            "others"
        )
        queryButtons["language"] = QueryButton(
            { chatId ->
                "${localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.LANGUAGE_BTN)}: ${
                    localisers[getChatLocale(chatId)]!!.get(
                        if (getChatLocale(chatId) == BotLocale.ZH_HK) {
                            LocaliseComponent.LANG_CHI
                        } else {
                            LocaliseComponent.LANG_ENG
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
        }
        queryButtons["active_warnings"] = QueryButton(
            { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.ACTIVE_WARN_BTN) },
            "active_warnings"
        ).apply {
            buildMessage = {
                val chatId = getChat(it).id
                val locale = getChatLocale(chatId)
                Pair(
                    ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN,
                    composers[locale]!!.composeWeatherWarning(requestWarningInfo(locale), RenderMode.MARKDOWN)
                )
            }
        }
        queryButtons["landing"] = QueryButton({ "" }, "landing", telegramBot.me.firstName)

        // setup page flow
        mainPage = QueryPage("landing").apply {
            renderLayout = { mainPageChatId ->
                val buttons = mutableListOf(
                    QueryPage("current_weather"),
                    QueryPage("general_weather"),
                    //QueryPage("9_day_forecast"), // TODO - implement 9 days
                    QueryPage("active_warnings"),
                )

                val tropicalCyclones = apiCache.get(Cache.TROPICAL_CYCLONE)
                if (tropicalCyclones is TropicalCyclones && tropicalCyclones.values.isNotEmpty()) {
                    val tcBuildMessage = { update: Update, tc: TropicalCyclone ->
                        val chatId = getChat(update).id
                        val locale = getChatLocale(chatId)
                        Pair(
                            ReplyMode.NEW_MESSAGE_AND_BACK,
                            if (tc.tcId == null) "" else composers[locale]!!.composeTropicalCycloneMessage(tc)
                        )
                    }
                    for (tc in tropicalCyclones.values) {
                        val callbackData = "${TROPICAL_CYCLONE_BTN_PREFIX}${tc.tcId}"
                        if (!queryButtons.containsKey(callbackData)) {
                            queryButtons.putIfAbsent(callbackData, QueryButton(
                                {
                                    when (getChatLocale(it)) {
                                        BotLocale.ZH_HK -> {
                                            tc.tcName ?: ""
                                        }
                                        else -> {
                                            tc.enName ?: ""
                                        }
                                    }
                                },
                                "tc_${tc.tcId}"
                            ).apply {
                                buildMessage = { tcBuildMessage(it, tc) }
                            })
                        }
                    }

                    // add tropical cyclone buttons
                    val callbackData = tropicalCyclones.values
                        .map { "${TROPICAL_CYCLONE_BTN_PREFIX}${it.tcId}" }.toTypedArray()
                    buttons.add(QueryPage("tc").addItems(*callbackData))
                    queryButtons["tc"] = QueryButton(
                        { chatId -> localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.TROPICAL_CYCLONE_BTN) },
                        "tc"
                    ).apply {
                        buildMessage = {
                            val chatId = getChat(it).id
                            Pair(
                                ReplyMode.UPDATE_QUERY,
                                localisers[getChatLocale(chatId)]!!.get(LocaliseComponent.TROPICAL_CYCLONE_BTN)
                            )
                        }
                    }
                } else {
                    // remove all tc buttons
                    queryButtons.keys().toList()
                        .filter { it.startsWith(TROPICAL_CYCLONE_BTN_PREFIX) }
                        .forEach {
                            queryButtons.remove(it)
                        }
                }

                buttons.addAll(
                    listOf(
                        QueryPage("others"),
                        QueryPage("settings")
                            .addItems("notification")
                            .addItems("language"),
                        QueryPage("about")
                    )
                )
                buttons.map { listOf(it) }
            }
        }

        // schedule check warning
        fixedRateTimer("weather_warning_check_task", true, period = 20000L) {

        }
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
        return apiCache.get(
            if (botLocale == BotLocale.ZH_HK) Cache.GENERAL_INFO else Cache.GENERAL_INFO_ENG
        ) as WeatherInfo
    }

    private fun requestWarningInfo(botLocale: BotLocale): WarningInfo {
        return apiCache.get(
            if (botLocale == BotLocale.ZH_HK) Cache.WARNING_INFO else Cache.WARNING_INFO_ENG
        ) as WarningInfo
    }

    fun handleInlineQuery(update: Update): AnswerInlineQuery {
        val query = update.inlineQuery
        val reply = AnswerInlineQuery()
        reply.inlineQueryId = query.id
        reply.results = listOf(
            InlineQueryResultArticle().apply {
                id = UUID.randomUUID().toString()
                title = localisers[BotLocale.ZH_HK]!!.get(LocaliseComponent.CURRENT_WEATHER_BTN)
                inputMessageContent = InputTextMessageContent().apply {
                    val locale = BotLocale.ZH_HK
                    messageText = composers[locale]!!.composeCurrentWeather(requestGeneralInfo(locale))
                }
            },
            InlineQueryResultArticle().apply {
                id = UUID.randomUUID().toString()
                title = localisers[BotLocale.ZH_HK]!!.get(LocaliseComponent.GENERAL_WEATHER_FORECAST_BTN)
                inputMessageContent = InputTextMessageContent().apply {
                    val locale = BotLocale.ZH_HK
                    messageText = composers[locale]!!.composeGeneralWeatherInfo(requestGeneralInfo(locale))
                }
            },
            InlineQueryResultArticle().apply {
                id = UUID.randomUUID().toString()
                title = localisers[BotLocale.ZH_HK]!!.get(LocaliseComponent.ACTIVE_WARN_BTN)
                inputMessageContent = InputTextMessageContent().apply {
                    val locale = BotLocale.ZH_HK
                    messageText = composers[locale]!!.composeWeatherWarning(requestWarningInfo(locale))
                }
            },
            InlineQueryResultArticle().apply {
                id = UUID.randomUUID().toString()
                title = localisers[BotLocale.EN_UK]!!.get(LocaliseComponent.CURRENT_WEATHER_BTN)
                inputMessageContent = InputTextMessageContent().apply {
                    val locale = BotLocale.EN_UK
                    messageText = composers[locale]!!.composeCurrentWeather(requestGeneralInfo(locale))
                }
            },
            InlineQueryResultArticle().apply {
                id = UUID.randomUUID().toString()
                title = localisers[BotLocale.EN_UK]!!.get(LocaliseComponent.GENERAL_WEATHER_FORECAST_BTN)
                inputMessageContent = InputTextMessageContent().apply {
                    val locale = BotLocale.EN_UK
                    messageText = composers[locale]!!.composeGeneralWeatherInfo(requestGeneralInfo(locale))
                }
            },
            InlineQueryResultArticle().apply {
                id = UUID.randomUUID().toString()
                title = localisers[BotLocale.EN_UK]!!.get(LocaliseComponent.ACTIVE_WARN_BTN)
                inputMessageContent = InputTextMessageContent().apply {
                    val locale = BotLocale.EN_UK
                    messageText = composers[locale]!!.composeWeatherWarning(requestWarningInfo(locale))
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
            val traveller = QueryGraphTraveller(mainPage, queryButtons)
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
        val traveller = QueryGraphTraveller(mainPage, queryButtons)
        val callbackData = update.callbackQuery.data
        traveller.travelTo(callbackData)
        return traveller.react(update)
    }
}

class UpdateHandler(private val sender: AbsSender, private val bot: WeatherBot) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun handle(update: Update?) {
        if (update == null || !(update.hasInlineQuery() || update.hasMessage() || update.hasCallbackQuery())) return
        val userId = getUser(update).id
        val completeAction = try {
            val chatId = getChat(update).id
            val chatSettingHash = Global.chatSettings.get(chatId).hashCode()
            Runnable {
                if (Global.chatSettings.get(chatId).hashCode() != chatSettingHash) {
                    Global.persistent.saveChatSettings(chatId, Global.chatSettings.get(chatId))
                }
            }
        } catch (e: Exception) {
            Runnable {
                // do nothing
            }
        }

        when {
            update.hasInlineQuery() -> {
                logger.debug("($userId), inline query: ${update.inlineQuery.query}")
                sender.executeAsync(bot.handleInlineQuery(update)).thenRunAsync(completeAction)
            }
            update.hasMessage() -> {
                logger.debug("($userId), message: ${if (update.message.hasText()) update.message.text else "NON_TEXT_MESSAGE"}")
                sender.executeAsync(bot.handleMessage(update)).thenRunAsync(completeAction)
            }
            update.hasCallbackQuery() -> {
                fun execReply(reply: BotApiMethod<*>): CompletableFuture<*> {
                    return when (reply) {
                        is SendMessage -> sender.executeAsync(reply)
                        is SendDocument -> sender.executeAsync(reply)
                        is SendPhoto -> sender.executeAsync(reply)
                        is SendVideo -> sender.executeAsync(reply)
                        is SendVideoNote -> sender.executeAsync(reply)
                        is SendSticker -> sender.executeAsync(reply)
                        is SendAudio -> sender.executeAsync(reply)
                        is SendVoice -> sender.executeAsync(reply)
                        is SendAnimation -> sender.executeAsync(reply)
                        is EditMessageText -> sender.executeAsync(reply)
                        else -> throw Exception("Unsupported reply type $reply")
                    }
                }

                logger.debug("($userId), callback query: ${update.callbackQuery.data}")
                val replies = bot.handleCallbackQuery(update)
                if (replies.isNotEmpty()) {
                    var future: CompletableFuture<*>? = null
                    for (reply in replies) {
                        future = if (future == null) execReply(reply) else future.thenRunAsync {
                            execReply(reply)
                        }
                    }
                    future!!.thenRunAsync(completeAction)
                }
            }
        }
    }
}

class PollingWeatherBot(private val token: String, private val username: String, defaultBotOptions: DefaultBotOptions) :
    TelegramLongPollingBot(defaultBotOptions) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val bot = WeatherBot(this)
    private val updateHandler = UpdateHandler(this, bot)

    override fun getBotToken(): String {
        return token
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun onUpdateReceived(update: Update?) {
        updateHandler.handle(update)
    }
}

class WebhookWeatherBot(
    private val token: String,
    private val username: String,
    private val webhookPath: String,
    defaultBotOptions: DefaultBotOptions
) :
    TelegramWebhookBot(defaultBotOptions) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val bot = WeatherBot(this)
    private val updateHandler = UpdateHandler(this, bot)

    override fun getBotToken(): String {
        return token
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun onWebhookUpdateReceived(update: Update?): BotApiMethod<*>? {
        updateHandler.handle(update)
        return null
    }

    override fun getBotPath(): String {
        return webhookPath
    }
}
