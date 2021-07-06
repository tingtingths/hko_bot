package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class UVindex15min {
    @SerializedName("recordTime")
    @Expose
    var recordTime: String? = null

    @SerializedName("value")
    @Expose
    var value: Int? = null
}
