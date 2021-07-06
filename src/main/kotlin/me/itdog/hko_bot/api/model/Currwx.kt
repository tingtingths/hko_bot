package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Currwx {
    @SerializedName("btime")
    @Expose
    var btime: String? = null

    @SerializedName("temp")
    @Expose
    var temp: String? = null

    @SerializedName("rh")
    @Expose
    var rh: String? = null
}
