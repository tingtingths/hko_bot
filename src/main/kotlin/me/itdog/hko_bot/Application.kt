package me.itdog.hko_bot

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import redis.clients.jedis.Jedis
import java.lang.Exception
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Global.app = Application(args).also {
        it.start()
    }
}

class Global {
    companion object {
        lateinit var app: Application
        lateinit var persistent: Persistent

        // secondary cache for user settings
        val userSettings: LoadingCache<Long, UserSettings> = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .build(CacheLoader.from { userId ->
                persistent.getUserSettings(userId!!, UserSettings(BotLocale.EN_UK))
            })
    }
}

class Application(args: Array<String>) {

    private var token: String
    private var username: String
    private var redis: Jedis
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
            Option.builder("r")
                .longOpt("redis")
                .argName("redis")
                .type(String::class.java)
                .numberOfArgs(1)
                .desc("Redis connection")
                .hasArg()
                .required()
                .build()
        )

        // parse arguments
        cmdLn = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
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
        redis = Jedis(URI.create(cmdLn.getOptionValue("redis")))
        Global.persistent = Persistent(redis)
    }

    private fun argumentsInvalidReason(cmdLine: CommandLine): String? {
        return try {
            URI.create(cmdLine.getOptionValue("redis"))
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
