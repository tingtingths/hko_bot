package me.itdog.hko_bot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.util.function.Function
import java.util.stream.Collectors

fun getUser(update: Update): User {
    when {
        update.hasMessage() -> return update.message.from
        update.hasEditedMessage() -> return update.editedMessage.from
        update.hasChannelPost() -> return update.channelPost.from
        update.hasEditedChannelPost() -> return update.editedChannelPost.from
        update.hasInlineQuery() -> return update.inlineQuery.from
        update.hasChosenInlineQuery() -> return update.chosenInlineQuery.from
        update.hasCallbackQuery() -> return update.callbackQuery.from
    }
    throw IllegalArgumentException("Unable to get user from update")
}

class QueryGraphTraveller(private val root: QueryPage) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private lateinit var buttons: MutableMap<String, QueryButton>
    private var currentPage = root
    private val BACK_BTN_CALLBACK_DATA_PREFIX = "__back__:"

    constructor(graph: QueryPage, queryButtons: List<QueryButton>) : this(graph) {
        this.buttons = queryButtons.stream()
            .collect(
                Collectors.toMap(
                    { it.callbackData },
                    Function.identity()
                )
            )
    }

    fun goHome(): QueryGraphTraveller {
        currentPage = root
        return this
    }

    fun travelTo(callbackData: String): Boolean {
        //logger.debug("${currentPage.buttonData} -> ${callbackData}...")
        val found = if (callbackData.startsWith(BACK_BTN_CALLBACK_DATA_PREFIX)) {
            // go back
            val actualData = callbackData.substring(BACK_BTN_CALLBACK_DATA_PREFIX.length, callbackData.length)
            val r = findPage(actualData)
            r ?: root
        } else {
            findPage(callbackData) ?: return false
        }
        currentPage = found
        //logger.debug("Arrived at ${currentPage.buttonData}")
        return true
    }

    fun replyNew(update: Update): SendMessage {
        val reply = SendMessage()
        val button = findButton(currentPage.buttonData)
        reply.chatId = update.message.chatId.toString()
        reply.text = button.defaultMessage
        val userId = getUser(update).id

        if (currentPage.layout.isNotEmpty()) {
            val markupButtons = currentPage.layout
                .map { it.buttonData }
                .map { findButton(it) }
                .map { InlineKeyboardButton(it.buttonText.invoke(userId)).apply { callbackData = it.callbackData } }
                .map { listOf(it) }
            reply.replyMarkup = InlineKeyboardMarkup(markupButtons)
        }

        return reply
    }

    private fun buildMarkupButtons(page: QueryPage, userId: Long): List<List<InlineKeyboardButton>> {
        // build buttons
        val markupButtons = mutableListOf<List<InlineKeyboardButton>>()
        // has declared buttons
        if (page.layout.isNotEmpty()) {
            page.layout
                .map { it.buttonData }
                .map { findButton(it) }
                .map { InlineKeyboardButton(it.buttonText.invoke(userId)).apply { callbackData = it.callbackData } }
                .map { listOf(it) }
                .toCollection(markupButtons)
        }
        // add back button if not at home
        val parent = findParent(page.buttonData, root)
        if (parent != null) {
            // append go back button
            val backBtnData = BACK_BTN_CALLBACK_DATA_PREFIX + parent.buttonData
            markupButtons.add(listOf(InlineKeyboardButton("« Back").apply {
                callbackData = backBtnData
            }))
        }
        return markupButtons
    }

    fun react(update: Update): List<BotApiMethod<*>> {
        val messages = mutableListOf<BotApiMethod<*>>()
        val button = findButton(currentPage.buttonData)
        val userId = getUser(update).id

        val replyAction = button.buildMessage.invoke(update)
        val replyMode = replyAction.first
        val replyMessage = replyAction.second
        when {
            ReplyMode.NEW_MESSAGE == replyMode -> {
                messages.addAll(buildNewMessageReplies(currentPage, update, replyMessage))
            }
            ReplyMode.NEW_MESSAGE_MARKDOWN == replyMode -> {
                messages.addAll(
                    buildNewMessageReplies(
                        currentPage,
                        update,
                        replyMessage,
                        parse_mode = "MarkdownV2"
                    )
                )
            }
            ReplyMode.UPDATE_QUERY == replyMode -> {
                val editMessage = EditMessageText()
                editMessage.chatId = update.callbackQuery.message.chatId.toString()
                editMessage.messageId = update.callbackQuery.message.messageId
                editMessage.text = replyMessage

                val markupButtons = buildMarkupButtons(currentPage, userId)
                if (markupButtons.isNotEmpty()) {
                    editMessage.replyMarkup = InlineKeyboardMarkup(markupButtons)
                }
                messages.add(editMessage)
            }
            ReplyMode.RERENDER_QUERY == replyMode -> {
                val page = findParent(currentPage.buttonData) ?: currentPage
                val editMessage = EditMessageText()
                editMessage.chatId = update.callbackQuery.message.chatId.toString()
                editMessage.messageId = update.callbackQuery.message.messageId
                editMessage.text = update.callbackQuery.message.text // use existing message, no update needed

                val markupButtons = buildMarkupButtons(page, userId)
                if (markupButtons.isNotEmpty()) {
                    editMessage.replyMarkup = InlineKeyboardMarkup(markupButtons)
                }
                messages.add(editMessage)
            }
            ReplyMode.NEW_MESSAGE_AND_BACK == replyMode -> {
                val page = findParent(currentPage.buttonData) ?: currentPage
                messages.addAll(buildNewMessageReplies(page, update, replyMessage))
            }
            ReplyMode.NEW_MESSAGE_AND_BACK_MARKDOWN == replyMode -> {
                val page = findParent(currentPage.buttonData) ?: currentPage
                messages.addAll(buildNewMessageReplies(page, update, replyMessage, parse_mode = "MarkdownV2"))
            }
        }

        return messages
    }

    private fun buildNewMessageReplies(
        page: QueryPage,
        update: Update,
        message: String,
        parse_mode: String? = null
    ): List<BotApiMethod<*>> {
        val userId = getUser(update).id
        val messages = mutableListOf<BotApiMethod<*>>()
        val newMessage = SendMessage()
        newMessage.chatId = update.callbackQuery.message.chatId.toString()
        newMessage.text = message
        if (parse_mode != null) newMessage.parseMode = parse_mode
        messages.add(newMessage)

        // update existing callback query with new message
        val editMessage = SendMessage()
        editMessage.chatId = update.callbackQuery.message.chatId.toString()
        editMessage.text = findButton(page.buttonData).defaultMessage

        val markupButtons = buildMarkupButtons(page, userId)
        if (markupButtons.isNotEmpty()) {
            editMessage.replyMarkup = InlineKeyboardMarkup(markupButtons)
        }
        messages.add(editMessage)
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

    var buttonText: (Long) -> String // input - userId
    var callbackData: String
    var defaultMessage: String
    var buildMessage: ((Update) -> Pair<ReplyMode, String>)

    constructor(buttonText: (Long) -> String, callbackData: String, defaultMessage: String? = null) {
        this.buttonText = buttonText
        this.callbackData = callbackData
        this.defaultMessage = defaultMessage ?: "Default message for [${callbackData}]"
        buildMessage = { Pair(ReplyMode.UPDATE_QUERY, this.defaultMessage) }
    }
}

enum class ReplyMode {
    NEW_MESSAGE, NEW_MESSAGE_MARKDOWN, NEW_MESSAGE_AND_BACK, NEW_MESSAGE_AND_BACK_MARKDOWN, UPDATE_QUERY, RERENDER_QUERY
}
