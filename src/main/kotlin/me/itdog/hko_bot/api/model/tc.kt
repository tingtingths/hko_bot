package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class TropicalCyclones {

    @SerializedName("TC")
    @Expose
    var tropicalCyclones: List<TropicalCyclone>? = null
}

@Generated("jsonschema2pojo")
class TropicalCyclone {
    @SerializedName("intensity")
    @Expose
    var intensity: String? = null

    @SerializedName("displayOrder")
    @Expose
    var displayOrder: String? = null

    @SerializedName("lastPublishTime")
    @Expose
    var lastPublishTime: String? = null

    @SerializedName("datatype")
    @Expose
    var datatype: String? = null

    @SerializedName("tcName")
    @Expose
    var tcName: String? = null

    @SerializedName("filenameApp")
    @Expose
    var filenameApp: String? = null

    @SerializedName("tcId")
    @Expose
    var tcId: String? = null

    @SerializedName("enName")
    @Expose
    var enName: String? = null

    @SerializedName("filename")
    @Expose
    var filename: String? = null
}
