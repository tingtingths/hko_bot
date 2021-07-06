package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import javax.annotation.Generated

@Generated("jsonschema2pojo")
class Play {
    @SerializedName("title")
    @Expose
    var title: String? = null

    @SerializedName("subtitle")
    @Expose
    var subtitle: String? = null

    @SerializedName("href")
    @Expose
    var href: String? = null

    @SerializedName("updated")
    @Expose
    var updated: String? = null

    @SerializedName("entry_1_title")
    @Expose
    var entry1Title: String? = null

    @SerializedName("entry_1_content")
    @Expose
    var entry1Content: String? = null

    @SerializedName("entry_1_href")
    @Expose
    var entry1Href: String? = null

    @SerializedName("entry_1_thumbnail")
    @Expose
    var entry1Thumbnail: String? = null

    @SerializedName("uploaded")
    @Expose
    var uploaded: String? = null

    @SerializedName("langDesc")
    @Expose
    var langDesc: String? = null

    @SerializedName("hTimeWithin")
    @Expose
    var hTimeWithin: String? = null
}
