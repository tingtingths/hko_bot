package me.itdog.hko_bot

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

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

fun getChat(update: Update): Chat {
    when {
        update.hasMessage() -> return update.message.chat
        update.hasEditedMessage() -> return update.editedMessage.chat
        update.hasChannelPost() -> return update.channelPost.chat
        update.hasEditedChannelPost() -> return update.editedChannelPost.chat
        update.hasCallbackQuery() -> return update.callbackQuery.message.chat
    }
    throw IllegalArgumentException("Unable to get chat from update")
}

class QueryGraphTraveller(private val root: QueryPage) {

    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var buttons: MutableMap<String, QueryButton>
    private var currentPage = root
    private val BACK_BTN_CALLBACK_DATA_PREFIX = "__back__:"

    private fun QueryPage.toButton(chatId: Long): InlineKeyboardButton {
        val btn = findButton(buttonData)
        return InlineKeyboardButton(btn.buttonText.invoke(chatId)).apply { callbackData = btn.callbackData }
    }

    constructor(graph: QueryPage, buttons: MutableMap<String, QueryButton>) : this(graph) {
        this.buttons = buttons
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
        val chatId = getChat(update).id

        val layout = currentPage.renderLayout(chatId)
        if (layout.isNotEmpty()) {
            reply.replyMarkup = InlineKeyboardMarkup(layout.map { row ->
                row.map { it.toButton(chatId) }
            })
        }

        return reply
    }

    private fun buildMarkupButtons(page: QueryPage, chatId: Long): List<List<InlineKeyboardButton>> {
        // build buttons
        val markupButtons = mutableListOf<List<InlineKeyboardButton>>()
        // has declared buttons
        val layout = page.renderLayout(chatId)
        if (layout.isNotEmpty()) {
            layout.map { row ->
                row.map { it.toButton(chatId) }
            }.toCollection(markupButtons)
        }

        // add back button if not at home
        val parent = findParent(page.buttonData, root)
        if (parent != null) {
            // append go back button
            val backBtnData = BACK_BTN_CALLBACK_DATA_PREFIX + parent.buttonData
            markupButtons.add(listOf(InlineKeyboardButton("«").apply {
                callbackData = backBtnData
            }))
        }
        return markupButtons
    }

    fun react(update: Update): List<BotApiMethod<*>> {
        val messages = mutableListOf<BotApiMethod<*>>()
        val button = findButton(currentPage.buttonData)
        val chatId = getChat(update).id

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

                val markupButtons = buildMarkupButtons(currentPage, chatId)
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

                val markupButtons = buildMarkupButtons(page, chatId)
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
        val chatId = getChat(update).id
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

        val markupButtons = buildMarkupButtons(page, chatId)
        if (markupButtons.isNotEmpty()) {
            editMessage.replyMarkup = InlineKeyboardMarkup(markupButtons)
        }
        messages.add(editMessage)
        return messages
    }

    private fun findParent(callbackData: String, page: QueryPage = root): QueryPage? {
        if (page.buttonData == callbackData) return null
        val layout = page.renderLayout(null)
        if (layout.isEmpty()) return null
        for (row in layout) {
            for (child in row) {
                if (child.buttonData == callbackData) {
                    return page
                }
            }
        }
        for (row in layout) {
            for (child in row) {
                val found = findParent(callbackData, child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findPage(callbackData: String, page: QueryPage = root): QueryPage? {
        if (page.buttonData == callbackData) return page
        val layout = page.renderLayout(null)
        if (layout.isEmpty()) return null
        for (row in layout) {
            for (child in row) {
                val found = findPage(callbackData, child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findButton(callbackData: String): QueryButton {
        return buttons[callbackData] ?: throw Exception("Unable to find button [${currentPage.buttonData}]???")
    }
}

class QueryPage(val buttonData: String) {
    private val layout: MutableList<QueryPage> = mutableListOf()
    var renderLayout: (Long?) -> List<List<QueryPage>> = {
        layout.map {
            listOf(it)
        }
    }

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
