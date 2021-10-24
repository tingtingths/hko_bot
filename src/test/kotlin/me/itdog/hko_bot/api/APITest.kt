package me.itdog.hko_bot.api

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.time.Instant
import kotlin.test.Test


internal class APITest {

    private val api = HongKongObservatory()
    private val gson = GsonBuilder().registerTypeAdapter(
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
    ).setPrettyPrinting().create()

    @Test
    fun testGetFLW() {
        val flw = api.requestLocalWeatherForecast(HongKongObservatory.Language.TRADITIONAL_CHINESE)
        println(gson.toJson(flw))
    }

    @Test
    fun testGetFND() {
        val fnd = api.requestNineDayForecast(HongKongObservatory.Language.TRADITIONAL_CHINESE)
        println(gson.toJson(fnd))
    }

    @Test
    fun testGetRHRREAD() {
        val rhrread = api.requestCurrentWeatherReport(HongKongObservatory.Language.TRADITIONAL_CHINESE)
        println(gson.toJson(rhrread))
    }

    @Test
    fun testGetActiveWarnings() {
        val warnings = api.requestWarningSummary(HongKongObservatory.Language.TRADITIONAL_CHINESE)
        println(gson.toJson(warnings))
        println(warnings.activeWarnings().map { it.name + " - " + it.type })
    }

    @Test
    fun testGetSpecialWeatherTips() {
        val swt = api.requestSpecialWeatherTips(HongKongObservatory.Language.TRADITIONAL_CHINESE)
        println(gson.toJson(swt))
        println(swt.tips?.map { it.desc })
    }
}
