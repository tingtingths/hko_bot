package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.stream.Collectors
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class WarningInfo {
    @SerializedName("WCOLDC")
    @Expose
    var wcoldc: Wcoldc? = null

    @SerializedName("WFIREC")
    @Expose
    var wfirec: Wfirec? = null

    @SerializedName("WFNTSAC")
    @Expose
    var wfntsac: Wfntsac? = null

    @SerializedName("WFROSTC")
    @Expose
    var wfrostc: Wfrostc? = null

    @SerializedName("WHOTC")
    @Expose
    var whotc: Whotc? = null

    @SerializedName("WLC")
    @Expose
    var wlc: Wlc? = null

    @SerializedName("WMSGNLC")
    @Expose
    var wmsgnlc: Wmsgnlc? = null

    @SerializedName("WRAINC")
    @Expose
    var wrainc: Wrainc? = null

    @SerializedName("WTCSGNLC")
    @Expose
    var wtcsgnlc: Wtcsgnlc? = null

    @SerializedName("WTMWC")
    @Expose
    var wtmwc: Wtmwc? = null

    @SerializedName("WTSC")
    @Expose
    var wtsc: Wtsc? = null

    fun activeWarnings(): MutableList<WarningBase> {
        return listOf(
            this.wcoldc,
            this.wfirec,
            this.wfntsac,
            this.wfrostc,
            this.whotc,
            this.wlc,
            this.wmsgnlc,
            this.wrainc,
            this.wtcsgnlc,
            this.wtmwc,
            this.wtsc,
        ).stream()
            .filter { it != null }
            .filter { (it?.inForce ?: -1) > 0 }
            .collect(Collectors.toList())
    }
}
