package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Hko {
    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("Temperature")
    @Expose
    var temperature: String? = null

    @SerializedName("RH")
    @Expose
    var rh: String? = null

    @SerializedName("HomeMaxTemperature")
    @Expose
    var homeMaxTemperature: String? = null

    @SerializedName("HomeMinTemperature")
    @Expose
    var homeMinTemperature: String? = null
}
