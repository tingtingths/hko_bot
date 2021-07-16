package me.itdog.hko_bot

import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    Application(args).start()
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
        // TODO add webhook options

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
    }

    private fun argumentsInvalidReason(cmdLine: CommandLine): String? {
        return null
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
