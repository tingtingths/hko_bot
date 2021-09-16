package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.generics.LongPollingBot
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook
import redis.clients.jedis.JedisPool
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Global.buildProperties.load(
        Application::class.java.getResourceAsStream("/build.properties")
    )
    Global.app = Application(args).also {
        it.start()
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        run {
            // persist all user settings before shutdown
            println("Saving ${Global.chatSettings.size()} cached user settings..")
            Global.chatSettings.cleanUp()
            Global.persistent.saveChatSettings(Global.chatSettings.asMap())
            Global.persistent.close()
        }
    })
}

class Global {
    companion object {
        lateinit var app: Application
        lateinit var persistent: ChatSettingsPersistent

        // secondary cache for user settings
        val chatSettings: LoadingCache<Long, ChatSettings> = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .build(CacheLoader.from { key ->
                persistent.getChatSettings(key!!, ChatSettings(BotLocale.EN_UK, false))
            })

        val buildProperties = Properties()
    }
}

class Application(args: Array<String>) {

    private var token: String
    private var username: String
    private var webhookBaseURL: String
    private var webhookPath: String
    private var port: Int
    private var httpThread: Int
    private val logger = LoggerFactory.getLogger("")

    init {
        val parser: CommandLineParser = DefaultParser()
        val cmdLn: CommandLine
        val options = Options()

        options.addOption(
            Option.builder("t")
                .longOpt("token")
                .argName("token")
                .type(String::class.java)
                .numberOfArgs(1)
                .desc("Bot token")
                .hasArg()
                .required()
                .build()
        )
        options.addOption(
            Option.builder("u")
                .longOpt("username")
                .argName("username")
                .type(String::class.java)
                .numberOfArgs(1)
                .desc("Bot username")
                .hasArg()
                .required()
                .build()
        )
        options.addOption(
            Option.builder()
                .longOpt("http-thread")
                .argName("Http thread count, >= 1 and <= 16. (default 4)")
                .type(Int::class.java)
                .numberOfArgs(1)
                .desc("Thread count for bot reply http calls")
                .hasArg()
                .build()
        )

        options.addOption(
            Option.builder()
                .longOpt("webhook-base-url")
                .argName("Webhook base url")
                .type(String::class.java)
                .numberOfArgs(1)
                .desc("Webhook base url, provide to start the bot in webhook mode. The final url would be <webhook-url>/callback/<webhook-path>.")
                .hasArg()
                .build()
        )
        options.addOption(
            Option.builder()
                .longOpt("webhook-path")
                .argName("Webhook url path")
                .type(String::class.java)
                .numberOfArgs(1)
                .desc("Webhook path, should be something random and complex to serve as a security measure. (default generated uuid)")
                .hasArg()
                .build()
        )
        options.addOption(
            Option.builder("p")
                .longOpt("port")
                .argName("Webhook port")
                .type(Int::class.java)
                .numberOfArgs(1)
                .desc("Webhook port, port for the webhook http server to listen to. (default 80)")
                .hasArg()
                .build()
        )

        options.addOptionGroup(
            OptionGroup().addOption(
                Option.builder("r")
                    .longOpt("redis")
                    .argName("redis")
                    .type(String::class.java)
                    .numberOfArgs(1)
                    .desc("Redis connection")
                    .hasArg()
                    .required()
                    .build()
            ).addOption(
                Option.builder("f")
                    .longOpt("file")
                    .argName("file")
                    .type(String::class.java)
                    .numberOfArgs(1)
                    .desc("Data file")
                    .hasArg()
                    .required()
                    .build()
            ).apply { isRequired = true }
        )

        // parse arguments
        cmdLn = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
            println("ERROR ${e.message}")
            printHelp(options)
            exitProcess(1)
        }
        argumentsInvalidReason(cmdLn)?.run {
            println(this)
            printHelp(options)
            exitProcess(1)
        }

        token = cmdLn.getOptionValue("token")
        username = cmdLn.getOptionValue("username")
        webhookBaseURL = cmdLn.getOptionValue("webhook-base-url", "")
        webhookPath = cmdLn.getOptionValue("webhook-path", UUID.randomUUID().toString())
        port = cmdLn.getOptionValue("port", "80").toInt()
        httpThread = cmdLn.getOptionValue("http-thread", "4").toInt()
        when {
            cmdLn.hasOption("redis") -> Global.persistent =
                RedisPersistent(JedisPool(URI.create(cmdLn.getOptionValue("redis"))))
            cmdLn.hasOption("file") -> Global.persistent = LocalFilePersistent(File(cmdLn.getOptionValue("file")))
        }
    }

    private fun argumentsInvalidReason(cmdLine: CommandLine): String? {
        return try {
            if (cmdLine.hasOption("redis")) {
                URI.create(cmdLine.getOptionValue("redis"))
            }
            if (cmdLine.hasOption("webhook-base-url")) {
                URI.create(cmdLine.getOptionValue("webhook-base-url"))
            }
            if (cmdLine.hasOption("http-thread")) {
                val count = cmdLine.getOptionValue("http-thread", "4").toInt()
                if (count !in 1..16) {
                    throw IllegalArgumentException("http-thread $count is not between 1 and 16")
                }
            }
            null
        } catch (e: Exception) {
            "Unable to parse command line, ${e.message}"
        }
    }

    private fun printHelp(options: Options) {
        val helpFmt = HelpFormatter()
        val version = Global.buildProperties.getProperty("version", "__VERSION__")
        val timestamp = Global.buildProperties.getProperty("timestamp", "0")
        helpFmt.printHelp("hko_bot $version+$timestamp", options)
    }

    fun start() {
        val builder = WeatherBotBuilder()
            .token(token)
            .username(username)
            .botOptions(DefaultBotOptions().apply {
                maxThreads = httpThread
            })

        if (webhookBaseURL.isNotEmpty()) {
            builder.updateType(WeatherBotBuilder.UpdateType.WEBHOOK).webhookPath(webhookPath)

            val webhook = DefaultWebhook()
            webhook.setInternalUrl("http://0.0.0.0:$port")

            val setWebhook = SetWebhook.builder()
                .url(webhookBaseURL)
                .build()

            val botApi = TelegramBotsApi(DefaultBotSession::class.java, webhook)
            botApi.registerBot(
                builder.build() as WebhookWeatherBot,
                setWebhook
            )
        } else {
            val botApi = TelegramBotsApi(DefaultBotSession::class.java)
            builder.updateType(WeatherBotBuilder.UpdateType.LONG_POLL)
            botApi.registerBot(builder.build() as LongPollingBot)
        }

        logger.info("Bot Started...")
    }
}
