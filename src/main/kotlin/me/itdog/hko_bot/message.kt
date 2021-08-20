package me.itdog.hko_bot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.util.function.Function
import java.util.stream.Collectors

class QueryGraphTraveller(private val root: QueryPage) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private lateinit var buttons: MutableMap<String, QueryButton>
    private var currentPage = root
    private val BACK_BTN_CALLBACK_DATA_PREFIX = "__back__:"

    constructor(graph: QueryPage, buttons: List<QueryButton>) : this(graph) {
        this.buttons = buttons.stream()
            .collect(
                Collectors.toMap(
                    { it.keyboardButton.callbackData },
                    Function.identity()
                )
            )
    }

    fun goHome(): QueryGraphTraveller {
        currentPage = root
        return this
    }

    fun travelTo(callbackData: String): Boolean {
        logger.debug("${currentPage.buttonData} -> ${callbackData}...")
        val found = if (callbackData.startsWith(BACK_BTN_CALLBACK_DATA_PREFIX)) {
            // go back
            val actualData = callbackData.substring(BACK_BTN_CALLBACK_DATA_PREFIX.length, callbackData.length)
            val r = findPage(actualData)
            r ?: root
        } else {
            findPage(callbackData) ?: return false
        }
        currentPage = found
        logger.debug("Arrived at ${currentPage.buttonData}")
        return true
    }

    fun replyNew(update: Update): SendMessage {
        val reply = SendMessage()
        val button = findButton(currentPage.buttonData)
        reply.chatId = update.message.chatId.toString()
        reply.text = button.defaultMessage

        if (currentPage.layout.isNotEmpty()) {
            val markupButtons = currentPage.layout
                .map { it.buttonData }
                .map { findButton(it) }
                .map { listOf(it.keyboardButton) }
            reply.replyMarkup = InlineKeyboardMarkup(markupButtons)
        }

        return reply
    }

    fun react(update: Update): List<BotApiMethod<*>> {
        val messages = mutableListOf<BotApiMethod<*>>()
        val button = findButton(currentPage.buttonData)

        // build buttons
        val markupButtons = mutableListOf<List<InlineKeyboardButton>>()
        // has declared buttons
        if (currentPage.layout.isNotEmpty()) {
            currentPage.layout
                .map { it.buttonData }
                .map { findButton(it) }
                .map { listOf(it.keyboardButton) }
                .toCollection(markupButtons)
        }
        // add back button if not at home
        val parent = findParent(currentPage.buttonData, root)
        if (parent != null) {
            // append go back button
            val backBtnData = BACK_BTN_CALLBACK_DATA_PREFIX + parent.buttonData
            markupButtons.add(listOf(InlineKeyboardButton("\u00AB Back").apply {
                callbackData = backBtnData
            }))
        }

        val replyAction = button.buildMessage.invoke(update)
        if (ReplyMode.NEW_MESSAGE == replyAction.first) {
            val newMessage = SendMessage()
            newMessage.chatId = update.callbackQuery.message.chatId.toString()
            newMessage.text = replyAction.second
            messages.add(newMessage)

            // update existing callback query with new message
            val editMessage = SendMessage()
            editMessage.chatId = update.callbackQuery.message.chatId.toString()
            editMessage.text = button.defaultMessage

            if (markupButtons.isNotEmpty()) {
                editMessage.replyMarkup = InlineKeyboardMarkup(markupButtons)
            }
            messages.add(editMessage)
        } else {
            val editMessage = EditMessageText()
            editMessage.chatId = update.callbackQuery.message.chatId.toString()
            editMessage.messageId = update.callbackQuery.message.messageId
            editMessage.text = replyAction.second

            if (markupButtons.isNotEmpty()) {
                editMessage.replyMarkup = InlineKeyboardMarkup(markupButtons)
            }
            messages.add(editMessage)
        }

        return messages
    }

    private fun findParent(callbackData: String, page: QueryPage = root): QueryPage? {
        if (page.buttonData == callbackData) return null
        if (page.layout.isEmpty()) return null
        var found = page.layout.find { it.buttonData == callbackData }
        if (found != null) {
            return page
        }
        for (child in page.layout) {
            found = findParent(callbackData, child)
            if (found != null) return found
        }
        return null
    }

    private fun findPage(callbackData: String, page: QueryPage = root): QueryPage? {
        if (page.buttonData == callbackData) return page
        if (page.layout.isEmpty()) return null
        for (child in page.layout) {
            val found = findPage(callbackData, child)
            if (found != null) return found
        }
        return null
    }

    private fun findButton(callbackData: String): QueryButton {
        return buttons[callbackData] ?: throw Exception("Unable to find button [${currentPage.buttonData}]???")
    }
}

class QueryPage(val buttonData: String) {
    val layout: MutableList<QueryPage> = mutableListOf()

    fun addItems(vararg options: QueryPage): QueryPage {
        layout.addAll(options)
        return this
    }

    fun addItems(vararg buttonData: String): QueryPage {
        layout.addAll(buttonData.map { QueryPage(it) })
        return this
    }
}

class QueryButton {

    var keyboardButton: InlineKeyboardButton
    var defaultMessage: String
    var buildMessage: ((Update) -> Pair<ReplyMode, String>)

    constructor(keyboardButton: InlineKeyboardButton, defaultMessage: String? = null) {
        this.keyboardButton = keyboardButton
        this.defaultMessage = defaultMessage ?: "Default message for [${this.keyboardButton.callbackData}]"
        buildMessage = { Pair(ReplyMode.UPDATE_QUERY, this.defaultMessage) }
    }

    constructor(buttonText: String, callbackData: String, defaultMessage: String? = null) {
        this.keyboardButton = InlineKeyboardButton(buttonText).apply { this.callbackData = callbackData }
        this.defaultMessage = defaultMessage ?: "Default message for [${this.keyboardButton.callbackData}]"
        buildMessage = { Pair(ReplyMode.UPDATE_QUERY, this.defaultMessage) }
    }
}

enum class ReplyMode {
    NEW_MESSAGE, UPDATE_QUERY
}
