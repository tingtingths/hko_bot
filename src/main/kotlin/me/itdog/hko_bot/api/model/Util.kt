package me.itdog.hko_bot.api.model

import org.jsoup.Jsoup

class Util {
    companion object {
        fun htmlExtractText(html: String): String {
            return Jsoup.parse(html).text()
        }
    }
}
