package me.itdog.hko_bot.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import me.itdog.hko_bot.api.model.TropicalCycloneDetails
import me.itdog.hko_bot.api.model.TropicalCyclones
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

        //const val WARNING_URL = "http://www.hko.gov.hk/wxinfo/json/warnsumc.xml"
        const val WARNING_URL = "http://localhost:58080/warnsumc.json"
        const val WARNING_URL_ENG = "http://www.hko.gov.hk/wxinfo/json/warnsum.xml"
        const val TROPICAL_CYCLONE_URL = "http://www.hko.gov.hk/wxinfo/json/tcFront.json"
        const val TROPICAL_CYCLONE_DETAILS_URL = "http://www.hko.gov.hk/wxinfo/currwx/tc_posc_%s.json"
        const val TROPICAL_CYCLONE_DETAILS_ENG_URL = "http://www.hko.gov.hk/wxinfo/currwx/tc_pos_%s.json"
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

    private fun <T> requestToObject(request: Request, clazz: Class<T>): T {
        val resp = client.newCall(request).execute()
        val body = resp.body ?: throw Error("Empty body response...")
        return gson.fromJson(body.string(), clazz)
    }

    fun getTropicalCyclones(isEnglish: Boolean = false): TropicalCyclones {
        val req = Request.Builder().url(TROPICAL_CYCLONE_URL).build()
        val tropicalCyclones = requestToObject(req, TropicalCyclones::class.java)

        for (tc in tropicalCyclones.values) {
            if (tc.datatype == "F4" || tc.datatype == "F4_F3") {
                // get details
                var fieldName = if (isEnglish) "tc_fixarea_e_htm" else "tc_fixarea_c_htm"
                var detailUrl = if (isEnglish) TROPICAL_CYCLONE_DETAILS_ENG_URL else TROPICAL_CYCLONE_DETAILS_URL
                detailUrl = detailUrl.format(tc.tcId)
                try {
                    val detailResp = Request.Builder().url(detailUrl).build().run {
                        client.newCall(this).execute()
                    }
                    val detailBody = detailResp.body ?: throw Error("Empty body response...")
                    val jObj = gson.fromJson(detailBody.string(), JsonObject::class.java)
                    tc.details = gson.fromJson(jObj.get(fieldName), TropicalCycloneDetails::class.java)
                } catch (e: Exception) {
                    l.warn("Unable to get tc details", e)
                }
            }
        }

        return tropicalCyclones
    }

    fun getGeneralInfo(isEnglish: Boolean = false): WeatherInfo {
        val req = Request.Builder()
            .url(if (isEnglish) API_URL_ENG else API_URL)
            .build()
        return requestToObject(req, WeatherInfo::class.java)
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
