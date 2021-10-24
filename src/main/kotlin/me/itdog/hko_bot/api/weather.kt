package me.itdog.hko_bot.api

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import me.itdog.hko_bot.api.model.*
import me.itdog.hko_bot.fromJson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.math.BigDecimal
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.stream.Collectors


class HongKongObservatory {

    companion object {
        const val WEATHER_API_URL = "https://data.weather.gov.hk/weatherAPI/opendata/weather.php"
        const val API_URL = "http://www.hko.gov.hk/wxinfo/json/one_json_uc.xml"
        const val API_URL_ENG = "http://www.hko.gov.hk/wxinfo/json/one_json.xml"

        const val WARNING_URL = "http://www.hko.gov.hk/wxinfo/json/warnsumc.xml"
        const val WARNING_URL_ENG = "http://www.hko.gov.hk/wxinfo/json/warnsum.xml"
        const val TROPICAL_CYCLONE_URL = "http://www.hko.gov.hk/wxinfo/json/tcFront.json"
        const val TROPICAL_CYCLONE_DETAILS_URL = "http://www.hko.gov.hk/wxinfo/currwx/tc_posc_%s.json"
        const val TROPICAL_CYCLONE_DETAILS_ENG_URL = "http://www.hko.gov.hk/wxinfo/currwx/tc_pos_%s.json"
    }

    open class ValueUnit {
        var value: BigDecimal? = null
        var unit: String? = null
    }

    open class PlaceValueUnit : ValueUnit() {
        var place: String? = null
    }

    open class PlaceValueUnitTime : PlaceValueUnit() {
        var recordTime: Instant? = null
    }

    class LocalWeatherForecast {
        @SerializedName("tcInfo")
        var tropicalCycloneInfo: String? = null

        @SerializedName("generalSituation")
        var generalSituation: String? = null

        @SerializedName("fireDangerWarning")
        var fireDangerWarning: String? = null

        @SerializedName("forecastPeriod")
        var forecastPeriod: String? = null

        @SerializedName("forecastDesc")
        var forecastDesc: String? = null

        @SerializedName("outlook")
        var outlook: String? = null

        @SerializedName("updateTime")
        var updateTime: Instant? = null
    }

    class NineDaysForecast {
        open class SeaTemperature : PlaceValueUnitTime()

        class SoilTemperature : PlaceValueUnitTime() {
            var depth: ValueUnit? = null
        }

        class WeatherForecast {

            @SerializedName("forecastDate")
            var date: LocalDate? = null

            @SerializedName("forecastWeather")
            var weather: String? = null

            @SerializedName("forecastMaxtemp")
            var maxTemperature: ValueUnit? = null

            @SerializedName("forecastMintemp")
            var minTemperature: ValueUnit? = null

            @SerializedName("week")
            var week: String? = null

            @SerializedName("forecastWind")
            var wind: String? = null

            @SerializedName("forecastMaxrh")
            var maxRH: ValueUnit? = null

            @SerializedName("forecastMinrh")
            var minRH: ValueUnit? = null
        }

        var generalSituation: String? = null
        var soilTemp: List<SoilTemperature>? = null
        var seaTemp: SeaTemperature? = null
        var weatherForecast: List<WeatherForecast>? = null
    }

    class CurrentWeatherReport {
        open class Humidity {
            var data: List<PlaceValueUnit>? = null
            var recordTime: Instant? = null
        }

        class Temperature: Humidity()

        class Rainfall {
            class Data {
                @SerializedName("main")
                var maintenance: String? = null
                var max: BigDecimal? = null
                var min: BigDecimal? = null
                var place: String? = null
                var unit: String? = null
            }
            var data: List<Data>? = null
            var startTime: Instant? = null
            var endTime: Instant? = null
        }

        class Lightning {
            class Data {
                var place: String? = null
                var occur: Boolean = false
            }
            var data: List<Data>? = null
            var startTime: Instant? = null
            var endTime: Instant? = null
        }

        class UVIndex {
            class Data {
                var place: String? = null
                var value: String? = null
                var desc: String? = null
                var message: String? = null
            }
            var data: List<Data>? = null
            var recordDesc: String? = null
        }

        var icon: List<Int>? = null
        var iconUpdateTime: Instant? = null
        var rainfall: Rainfall? = null
        var lightning: List<Lightning>? = null
        var humidity: Humidity? = null
        var temperature: Temperature? = null

        var uvindex: UVIndex? = null
        var updateTime: Instant? = null
        var warningMessage: List<String>? = null
        var rainstormReminder: String? = null
        var specialWXTips: List<String>? = null
        var tcmessage: List<String>? = null
    }

    class WarningSummary {
        class Warning {
            var name: String? = null
            var code: String? = null
            var type: String? = null
            var actionCode: String? = null
            var issueTime: Instant? = null
            var updateTime: Instant? = null
            var expireTime: Instant? = null
        }

        @SerializedName("WFIRE")
        var fireDangerWarning: Warning? = null

        @SerializedName("WFROST")
        var frostWarning: Warning? = null

        @SerializedName("WHOT")
        var hotWeatherWarning: Warning? = null

        @SerializedName("WCOLD")
        var coldWeatherWarning: Warning? = null

        @SerializedName("WMSGNL")
        var strongMonsoonSignal: Warning? = null

        @SerializedName("WRAIN")
        var rainstormWarningSignal: Warning? = null

        @SerializedName("WFNTSA")
        var floodingInNorthernNTAnnouncement: Warning? = null

        @SerializedName("WL")
        var landslipWarning: Warning? = null

        @SerializedName("WTCSGNL")
        var tropicalCycloneWarningSignal: Warning? = null

        @SerializedName("WTMW")
        var tsunamiWarning: Warning? = null

        @SerializedName("WTS")
        var thunderstormWarning: Warning? = null

        fun activeWarnings(): MutableList<Warning> {
            val activeActions = listOf("ISSUE", "REISSUE", "EXTEND", "UPDATE")
            return listOf(
                fireDangerWarning,
                frostWarning,
                hotWeatherWarning,
                coldWeatherWarning,
                strongMonsoonSignal,
                rainstormWarningSignal,
                floodingInNorthernNTAnnouncement,
                landslipWarning,
                tropicalCycloneWarningSignal,
                tsunamiWarning,
                thunderstormWarning
            ).stream()
                .filter { it != null }
                .filter { activeActions.contains(it?.actionCode) }
                .collect(Collectors.toList())
        }
    }

    class SpecialWeatherTips {
        class SpecialWeatherTip {
            var desc: String? = null
            var updateTime: Instant? = null
        }

        @SerializedName("swt")
        var tips: List<SpecialWeatherTip>? = null
    }

    enum class DataType(val value: String) {
        LOCAL_WEATHER_FORECAST("flw"),
        NINE_DAY_FORECAST("fnd"),
        CURRENT_WEATHER_REPORT("rhrread"),
        WARNING_SUMMARY("warnsum"),
        WARNING_INFO("warninginfo"),
        SPECIAL_WEATHER_TIPS("swt");
    }

    enum class Language(val value: String) {
        ENGLISH("en"), TRADITIONAL_CHINESE("tc"), SIMPLIFIED_CHINESE("sc");
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
    private val gson = GsonBuilder()
        .registerTypeAdapter(
            Instant::class.java,
            object : JsonDeserializer<Instant?> {
                override fun deserialize(
                    json: JsonElement?,
                    typeOfT: Type?,
                    context: JsonDeserializationContext?
                ): Instant? {
                    if (json == null) return null
                    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(json.asString).toInstant()
                }
            }
        )
        .registerTypeAdapter(
            LocalDate::class.java,
            object : JsonDeserializer<LocalDate?> {
                override fun deserialize(
                    json: JsonElement?,
                    typeOfT: Type?,
                    context: JsonDeserializationContext?
                ): LocalDate? {
                    if (json == null) return null
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                    return LocalDate.parse(json.asString, formatter)
                }
            }
        )
        .create()

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

    fun requestWeatherApi(dataType: DataType, language: Language): JsonElement {
        var url = URL(WEATHER_API_URL).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Unable to create url $WEATHER_API_URL")
        url = url.newBuilder()
            .addQueryParameter("dataType", dataType.value)
            .addQueryParameter("lang", language.value)
            .build()
        val req = Request.Builder()
            .url(url)
            .build()
        return requestToObject(req, JsonElement::class.java)
    }

    fun requestLocalWeatherForecast(language: Language): LocalWeatherForecast {
        return gson.fromJson(requestWeatherApi(DataType.LOCAL_WEATHER_FORECAST, language))
    }

    fun requestNineDayForecast(language: Language): NineDaysForecast {
        return gson.fromJson(requestWeatherApi(DataType.NINE_DAY_FORECAST, language))
    }

    fun requestCurrentWeatherReport(language: Language): CurrentWeatherReport {
        return gson.fromJson(requestWeatherApi(DataType.CURRENT_WEATHER_REPORT, language))
    }

    fun requestWarningSummary(language: Language): WarningSummary {
        return gson.fromJson(requestWeatherApi(DataType.WARNING_SUMMARY, language))
    }

    fun requestSpecialWeatherTips(language: Language): SpecialWeatherTips {
        return gson.fromJson(requestWeatherApi(DataType.SPECIAL_WEATHER_TIPS, language))
    }
}
