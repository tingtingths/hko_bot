package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.stream.Collectors
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class WarningInfo {
    @SerializedName("WCOLDC", alternate = ["WCOLD"])
    @Expose
    var wcoldc: Wcoldc? = null

    @SerializedName("WFIREC", alternate = ["WFIRE"])
    @Expose
    var wfirec: Wfirec? = null

    @SerializedName("WFNTSAC", alternate = ["WFNTSA"])
    @Expose
    var wfntsac: Wfntsac? = null

    @SerializedName("WFROSTC", alternate = ["WFROST"])
    @Expose
    var wfrostc: Wfrostc? = null

    @SerializedName("WHOTC", alternate = ["WHOT"])
    @Expose
    var whotc: Whotc? = null

    @SerializedName("WLC", alternate = ["WL"])
    @Expose
    var wlc: Wlc? = null

    @SerializedName("WMSGNLC", alternate = ["WMSGNL"])
    @Expose
    var wmsgnlc: Wmsgnlc? = null

    @SerializedName("WRAINC", alternate = ["WRAIN"])
    @Expose
    var wrainc: Wrainc? = null

    @SerializedName("WTCSGNLC", alternate = ["WTCSGNL"])
    @Expose
    var wtcsgnlc: Wtcsgnlc? = null

    @SerializedName("WTMWC", alternate = ["WTMW"])
    @Expose
    var wtmwc: Wtmwc? = null

    @SerializedName("WTSC", alternate = ["WTS"])
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
