package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class WeatherForecast {
    @SerializedName("ForecastDate")
    @Expose
    var forecastDate: String? = null

    @SerializedName("ForecastWind")
    @Expose
    var forecastWind: String? = null

    @SerializedName("ForecastWeather")
    @Expose
    var forecastWeather: String? = null

    @SerializedName("ForecastMaxtemp")
    @Expose
    var forecastMaxtemp: String? = null

    @SerializedName("ForecastMintemp")
    @Expose
    var forecastMintemp: String? = null

    @SerializedName("ForecastMaxrh")
    @Expose
    var forecastMaxrh: String? = null

    @SerializedName("ForecastMinrh")
    @Expose
    var forecastMinrh: String? = null

    @SerializedName("ForecastIcon")
    @Expose
    var forecastIcon: String? = null

    @SerializedName("PSR")
    @Expose
    var psr: String? = null

    @SerializedName("IconDesc")
    @Expose
    var iconDesc: String? = null

    @SerializedName("WeekDay")
    @Expose
    var weekDay: String? = null
}
