package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Ahko {
    @SerializedName("date")
    @Expose
    var date: String? = null

    @SerializedName("time")
    @Expose
    var time: String? = null

    @SerializedName("content")
    @Expose
    var content: String? = null

    @SerializedName("heading")
    @Expose
    var heading: String? = null

    @SerializedName("refimg")
    @Expose
    var refimg: String? = null
}
