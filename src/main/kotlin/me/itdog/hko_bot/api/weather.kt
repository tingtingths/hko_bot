package me.itdog.hko_bot.api

import com.google.gson.Gson
import me.itdog.hko_bot.api.model.WarningInfo
import me.itdog.hko_bot.api.model.WeatherInfo
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

class HongKongObservatory {

    companion object {
        const val API_URL = "http://www.hko.gov.hk/wxinfo/json/one_json_uc.xml"
        const val API_URL_ENG = "http://www.hko.gov.hk/wxinfo/json/one_json.xml"
        const val WARNING_URL = "http://www.hko.gov.hk/wxinfo/json/warnsumc.xml"
        const val WARNING_URL_ENG = "http://www.hko.gov.hk/wxinfo/json/warnsum.xml"
    }

    private val l = LoggerFactory.getLogger(javaClass)
    private val client: OkHttpClient = OkHttpClient().newBuilder()
        .addInterceptor(Interceptor {
            val req = it.request()
            val resp = it.proceed(req)
            l.debug("HTTP>> {}\nHTTP<< {}...", req.url, resp.peekBody(80).string())
            resp
        })
        .build()
    private val gson = Gson()

    fun getGeneralInfo(isEnglish: Boolean = false): WeatherInfo {
        val req = Request.Builder()
            .url(if (isEnglish) API_URL_ENG else API_URL)
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body ?: throw Error("Empty body response...")
        return gson.fromJson(body.string(), WeatherInfo::class.java)
    }

    fun getWarningInfo(isEnglish: Boolean = false): WarningInfo {
        val req = Request.Builder()
            .url(if (isEnglish) WARNING_URL_ENG else WARNING_URL)
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body ?: throw Error("Empty response body...")

        val pattern = Pattern.compile(".*?(?<json>\\{.*}).*", Pattern.DOTALL)
        val matcher = pattern.matcher(body.string())
        if (!matcher.find()) throw Error("Unable to parse response body...")
        return gson.fromJson(matcher.group("json"), WarningInfo::class.java)
    }
}
