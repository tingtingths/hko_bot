package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Swt {
    @SerializedName("headLine1")
    @Expose
    var headLine1: String? = null

    @SerializedName("headLine2")
    @Expose
    var headLine2: String? = null

    @SerializedName("headLine3")
    @Expose
    var headLine3: String? = null

    @SerializedName("headLine4")
    @Expose
    var headLine4: String? = null

    @SerializedName("headLine5")
    @Expose
    var headLine5: String? = null

    @SerializedName("smsSwt")
    @Expose
    var smsSwt: String? = null

    @SerializedName("tornadoReport")
    @Expose
    var tornadoReport: String? = null

    @SerializedName("waterspoutReport")
    @Expose
    var waterspoutReport: String? = null

    @SerializedName("gustForecast")
    @Expose
    var gustForecast: String? = null

    @SerializedName("hotAdvisory")
    @Expose
    var hotAdvisory: String? = null
}
