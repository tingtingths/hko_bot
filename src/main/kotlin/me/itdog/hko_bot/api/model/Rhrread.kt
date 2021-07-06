package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Rhrread {
    @SerializedName("BulletinDate")
    @Expose
    var bulletinDate: String? = null

    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("UVIndex")
    @Expose
    var uVIndex: String? = null

    @SerializedName("Intensity")
    @Expose
    var intensity: String? = null

    @SerializedName("hkotemp")
    @Expose
    var hkotemp: String? = null

    @SerializedName("hkorh")
    @Expose
    var hkorh: String? = null

    @SerializedName("FormattedObsTime")
    @Expose
    var formattedObsTime: String? = null
}
