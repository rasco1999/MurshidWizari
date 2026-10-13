package com.muriq.murshid.iosparity.data

import java.security.MessageDigest
import org.json.JSONObject

/** Matches iOS V498SchoolAd.revision for stable, per-account unread markers. */
object SchoolAdReadRules {
    fun revision(ad: JSONObject): String {
        val content = ad.optInt("id").toString() + "\u0000" +
            ad.optString("school").trim() + "\u0000" + ad.optString("text").trim()
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
