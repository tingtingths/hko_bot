package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Cmn {
    @SerializedName("SolarTerm")
    @Expose
    var solarTerm: String? = null

    @SerializedName("PublicHoliday")
    @Expose
    var publicHoliday: Any? = null

    @SerializedName("GregorianDate")
    @Expose
    var gregorianDate: String? = null

    @SerializedName("LunarDate")
    @Expose
    var lunarDate: String? = null

    @SerializedName("sunriseTime")
    @Expose
    var sunriseTime: String? = null

    @SerializedName("sunsetTime")
    @Expose
    var sunsetTime: String? = null

    @SerializedName("moonriseTime")
    @Expose
    var moonriseTime: String? = null

    @SerializedName("moonsetTime")
    @Expose
    var moonsetTime: String? = null

    @SerializedName("forecastDate")
    @Expose
    var forecastDate: String? = null

    @SerializedName("tide")
    @Expose
    var tide: List<Tide>? = null
}
