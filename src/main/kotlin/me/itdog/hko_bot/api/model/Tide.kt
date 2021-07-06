package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Tide {
    @SerializedName("type")
    @Expose
    var type: String? = null

    @SerializedName("time")
    @Expose
    var time: String? = null

    @SerializedName("height")
    @Expose
    var height: String? = null
}
