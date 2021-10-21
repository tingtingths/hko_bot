package me.itdog.hko_bot.api

import com.google.gson.Gson
import me.itdog.hko_bot.BotLocale
import me.itdog.hko_bot.ChatSettings
import me.itdog.hko_bot.RedisPersistent
import me.itdog.hko_bot.api.model.WarningInfo
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import redis.clients.jedis.JedisPool
import java.util.regex.Pattern
import kotlin.test.Test

internal class APITest {

    private val api = HongKongObservatory()

    @Test
    fun testRedisPersistent() {
        val persistent = RedisPersistent(JedisPool("localhost", 50300))
        persistent.saveChatSettings(1000L, ChatSettings(BotLocale.ZH_HK, false))
        persistent.saveChatSettings(1001L, ChatSettings(BotLocale.ZH_HK, false))
        persistent.saveChatSettings(1002L, ChatSettings(BotLocale.ZH_HK, false))
        persistent.saveChatSettings(1003L, ChatSettings(BotLocale.ZH_HK, false))
        println(persistent.getAllChatsSettings())
    }

    @Test
    fun testGetTCInfo() {
        val info = api.getTropicalCyclones()
        info.values.forEach {
            println("${it.tcId} - ${it.tcName}")
            if (it.details != null) {
                println("${it.details?.desc}")
                println("${it.details?.position}")
            } else {
                println("NONE")
            }
        }
    }

    @Test
    fun testRequestGeneralWeatherInfo() {
        val info = api.getGeneralInfo()
        println(
            "Temp.: ${info.currwx!!.temp}" +
                    "\nRelative Humidity: ${info.currwx!!.rh}" +
                    "\nFLW Date: ${info.flw!!.bulletinDate}" +
                    "\nFLW Time: ${info.flw!!.bulletinTime}"
        )
    }

    @Test
    fun testRequestWarningInfo() {
        api.getWarningInfo(true).let { info ->
            val activeWarnings = info.activeWarnings()
            println("Active warnings ${activeWarnings.size}")
            activeWarnings.forEach { base ->
                println("${base.name} - ${base.type}")
            }
        }
    }

    @Test
    fun parseTest() {
        val text =
            "var weather_warning_summary = {\"WCOLD\":{\"Name\":\"Cold Weather Warning\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210119\",\"BulletinTime\":\"0945\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WFIRE\":{\"Name\":\"Fire Danger Warning\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210418\",\"BulletinTime\":\"1800\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WFNTSA\":{\"Name\":\"Special Announcement On Flooding In Northern New Territories\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210819\",\"BulletinTime\":\"1540\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WFROST\":{\"Name\":\"Frost Warning\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210114\",\"BulletinTime\":\"0900\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WHOT\":{\"Name\":\"Very Hot Weather Warning\",\"Code\":\"WHOT\",\"Type\":null,\"ActionCode\":\"REISSUE\",\"InForce\":1,\"IssueDate\":\"20210907\",\"IssueTime\":\"1145\",\"BulletinDate\":\"20210908\",\"BulletinTime\":\"0645\",\"Icon\":\"vhot.gif\",\"ExpireDate\":null,\"ExpireTime\":null},\"WL\":{\"Name\":\"Landslip Warning\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210628\",\"BulletinTime\":\"1730\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WMSGNL\":{\"Name\":\"Strong Monsoon Signal\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210420\",\"BulletinTime\":\"0540\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WRAIN\":{\"Name\":\"Rainstorm Warning Signal\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210829\",\"BulletinTime\":\"1215\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WTCSGNL\":{\"Name\":\"Tropical Cyclone Warning Signal\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210804\",\"BulletinTime\":\"2120\",\"ExpireDate\":null,\"ExpireTime\":null,\"Icon\":null},\"WTMW\":{\"Name\":\"Tsunami Warning\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20190906\",\"BulletinTime\":\"0902\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null},\"WTS\":{\"Name\":\"Thunderstorm Warning\",\"Code\":null,\"Type\":null,\"ActionCode\":null,\"InForce\":0,\"IssueDate\":null,\"IssueTime\":null,\"BulletinDate\":\"20210907\",\"BulletinTime\":\"1430\",\"Icon\":null,\"ExpireDate\":null,\"ExpireTime\":null}};"
        val pattern = Pattern.compile(".*?(?<json>\\{.*}).*", Pattern.DOTALL)
        val matcher = pattern.matcher(text)
        if (!matcher.find()) throw Error("Unable to parse response body...")
        val obj = matcher.group("json")
        Gson().fromJson(obj, WarningInfo::class.java).let { info ->
            val activeWarnings = info.activeWarnings()
            println("Active warnings ${activeWarnings.size}")
            activeWarnings.forEach { base ->
                println("${base.name} - ${base.type}")
            }
        }
    }

    @Test
    fun sendTest() {
        val chatId = 267110164
        val text = "abcdefg"
        val token = ""

        val reqBody = FormBody.Builder()
            .add("chat_id", chatId.toString(10))
            .add("text", text)
            .build()
        var req = Request.Builder()
            .url("https://api.telegram.org/bot${token}/sendMessage")
            .post(reqBody)
            .build()
        OkHttpClient().newCall(req).execute()
    }
}
