package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import redis.clients.jedis.JedisPool
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main(args: Array<String>) {
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
    }
}

class Application(args: Array<String>) {

    private var token: String
    private var username: String
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

        val persistentGroup = OptionGroup().addOption(
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
        options.addOptionGroup(persistentGroup)

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
            null
        } catch (e: Exception) {
            "Unable to parse redis uri, ${e.cause}"
        }
    }

    private fun printHelp(options: Options) {
        val helpFmt = HelpFormatter()
        helpFmt.printHelp("hko_bot", options)
    }

    fun start() {
        val botApi = TelegramBotsApi(DefaultBotSession::class.java)
        botApi.registerBot(
            WeatherBotBuilder()
                .token(token)
                .username(username)
                .buildPollingBot()
        )
        logger.info("Bot Started...")
    }
}
