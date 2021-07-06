package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class WeatherInfo {
    @SerializedName("FLW")
    @Expose
    var flw: Flw? = null

    @SerializedName("CMN")
    @Expose
    var cmn: Cmn? = null

    @SerializedName("F9D")
    @Expose
    var f9d: F9d? = null

    @SerializedName("RHRREAD")
    @Expose
    var rhrread: Rhrread? = null

    @SerializedName("SWT")
    @Expose
    var swt: Swt? = null

    @SerializedName("Playlist")
    @Expose
    var playlist: List<Play>? = null

    @SerializedName("FUV")
    @Expose
    var fuv: Fuv? = null

    @SerializedName("lightning_info")
    @Expose
    var lightningInfo: List<LightningInfo>? = null

    @SerializedName("header")
    @Expose
    var header: Header? = null

    @SerializedName("hko")
    @Expose
    var hko: Hko? = null

    @SerializedName("fcartoon")
    @Expose
    var fcartoon: Fcartoon? = null

    @SerializedName("currwx")
    @Expose
    var currwx: Currwx? = null

    @SerializedName("ahko")
    @Expose
    var ahko: Ahko? = null

    @SerializedName("UVindex15min")
    @Expose
    var uVindex15min: UVindex15min? = null

    @SerializedName("s")
    @Expose
    var s: String? = null
}
