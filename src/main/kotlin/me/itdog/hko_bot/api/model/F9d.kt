package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class F9d {
    @SerializedName("BulletinDate")
    @Expose
    var bulletinDate: String? = null

    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("NPTemp")
    @Expose
    var nPTemp: String? = null

    @SerializedName("GeneralSituation")
    @Expose
    var generalSituation: String? = null

    @SerializedName("WeatherForecast")
    @Expose
    var weatherForecast: List<WeatherForecast>? = null
}
