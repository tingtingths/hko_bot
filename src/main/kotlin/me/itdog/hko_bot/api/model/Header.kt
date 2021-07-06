package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Header {
    @SerializedName("festival_code")
    @Expose
    var festivalCode: String? = null

    @SerializedName("solar_term_code")
    @Expose
    var solarTermCode: String? = null

    @SerializedName("dateTimeDisplay_uc")
    @Expose
    var dateTimeDisplayUc: String? = null

    @SerializedName("dateTimeDisplay_en")
    @Expose
    var dateTimeDisplayEn: String? = null

    @SerializedName("lunar_date_uc")
    @Expose
    var lunarDateUc: String? = null

    @SerializedName("solar_term_uc")
    @Expose
    var solarTermUc: String? = null

    @SerializedName("solar_term_en")
    @Expose
    var solarTermEn: String? = null

    @SerializedName("publicholiday_uc")
    @Expose
    var publicholidayUc: String? = null

    @SerializedName("publicholiday_en")
    @Expose
    var publicholidayEn: String? = null
}
