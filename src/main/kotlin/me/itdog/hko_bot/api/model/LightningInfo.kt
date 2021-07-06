package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class LightningInfo {
    @SerializedName("date")
    @Expose
    var date: String? = null

    @SerializedName("time")
    @Expose
    var time: String? = null

    @SerializedName("color")
    @Expose
    var color: String? = null
}
