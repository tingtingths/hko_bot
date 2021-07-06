package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Fcartoon {
    @SerializedName("BulletinDate")
    @Expose
    var bulletinDate: String? = null

    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("Icon1")
    @Expose
    var icon1: String? = null

    @SerializedName("Icon2")
    @Expose
    var icon2: Any? = null
}
