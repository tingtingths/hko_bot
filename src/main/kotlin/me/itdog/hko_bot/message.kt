package me.itdog.hko_bot

import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.util.stream.Collectors

class QueryOption {

    var keyboardButton: InlineKeyboardButton? = null
    var nextPageFunc: () -> List<List<QueryOption>>
    var responseFunc: ((Update) -> BotApiMethod<Message>) = { defaultNextPageResponse(it) }

    constructor(keyboardButton: InlineKeyboardButton) {
        this.keyboardButton = keyboardButton
        nextPageFunc = {
            emptyList()
        }
    }

    constructor(nextOptionFunc: () -> List<List<QueryOption>>) {
        this.nextPageFunc = nextOptionFunc
    }

    constructor(
        keyboardButton: InlineKeyboardButton,
        nextOptionFunc: () -> List<List<QueryOption>>,
        responseFunc: (Update) -> BotApiMethod<Message>
    ) {
        this.keyboardButton = keyboardButton
        this.nextPageFunc = nextOptionFunc
        this.responseFunc = responseFunc
    }

    fun findPage(callbackData: String): QueryOption? {
        if (keyboardButton?.callbackData == callbackData) return this
        val flattened = nextPageFunc.invoke().flatten()
        var found =
            flattened.find { keyboardButton?.callbackData == callbackData }
        if (found != null) return found
        flattened.forEach {
            found = it.findPage(callbackData)
            if (found != null) return found
        }
        return null
    }

    fun nextPageReplyMarkup(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup().apply {
            keyboard = nextPageFunc.invoke().map { row ->
                row.stream().map {
                    it.keyboardButton
                }.collect(Collectors.toList())
            }
        }
    }

    fun defaultNextPageResponse(update: Update): SendMessage {
        val reply = SendMessage()
        reply.chatId = update.callbackQuery.message.chatId.toString()
        reply.replyMarkup = nextPageReplyMarkup()
        return reply
    }

    companion object {
        class QueryOptionPageBuilder {

            var page: MutableList<MutableList<QueryOption>> = ArrayList()

            fun addRow(): QueryOptionPageBuilder {
                page.add(ArrayList())
                return this
            }

            fun addRow(options: MutableList<QueryOption>): QueryOptionPageBuilder {
                page.add(options)
                return this
            }

            fun addOption(option: QueryOption): QueryOptionPageBuilder {
                page.last().add(option)
                return this
            }

            fun build(): MutableList<MutableList<QueryOption>> {
                return page
            }
        }

    }
}
