package me.itdog.hko_bot.api.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

open class WarningBase {
    @SerializedName("Name")
    @Expose
    var name: String? = null

    @SerializedName("Code")
    @Expose
    var code: String? = null

    @SerializedName("Type")
    @Expose
    var type: String? = null

    @SerializedName("ActionCode")
    @Expose
    var actionCode: String? = null

    @SerializedName("InForce")
    @Expose
    var inForce: Int? = null

    @SerializedName("IssueDate")
    @Expose
    var issueDate: String? = null

    @SerializedName("IssueTime")
    @Expose
    var issueTime: String? = null

    @SerializedName("BulletinDate")
    @Expose
    var bulletinDate: String? = null

    @SerializedName("BulletinTime")
    @Expose
    var bulletinTime: String? = null

    @SerializedName("ExpireDate")
    @Expose
    var expireDate: String? = null

    @SerializedName("ExpireTime")
    @Expose
    var expireTime: String? = null

    @SerializedName("Icon")
    @Expose
    var icon: String? = null
}
