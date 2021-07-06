package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Flw {
    @SerializedName("BulletinDate")
    @Expose
    var bulletinDate: String? = null

    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("GeneralSituation")
    @Expose
    var generalSituation: Any? = null

    @SerializedName("TCInfo")
    @Expose
    var tCInfo: Any? = null

    @SerializedName("FireDangerWarning")
    @Expose
    var fireDangerWarning: Any? = null

    @SerializedName("ForecastPeriod")
    @Expose
    var forecastPeriod: String? = null

    @SerializedName("ForecastDesc")
    @Expose
    var forecastDesc: String? = null

    @SerializedName("OutlookTitle")
    @Expose
    var outlookTitle: String? = null

    @SerializedName("OutlookContent")
    @Expose
    var outlookContent: String? = null

    @SerializedName("Icon1")
    @Expose
    var icon1: String? = null

    @SerializedName("Icon2")
    @Expose
    var icon2: Any? = null
}
