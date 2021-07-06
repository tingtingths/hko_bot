package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Fuv {
    @SerializedName("BulletinDate")
    @Expose
    var bulletinDate: String? = null

    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("ForecastTimeInfoMaxUV")
    @Expose
    var forecastTimeInfoMaxUV: String? = null

    @SerializedName("ForecastTimeInfoMaxUvCategory")
    @Expose
    var forecastTimeInfoMaxUvCategory: String? = null

    @SerializedName("ForecastTimeInfoDate")
    @Expose
    var forecastTimeInfoDate: String? = null

    @SerializedName("Message")
    @Expose
    var message: String? = null
}
