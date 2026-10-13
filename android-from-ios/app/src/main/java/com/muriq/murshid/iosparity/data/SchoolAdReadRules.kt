package com.muriq.murshid.iosparity.data

import java.security.MessageDigest
import org.json.JSONObject

/** Matches iOS V498SchoolAd.revision for stable, per-account unread markers. */
object SchoolAdReadRules {
    /** Mirror V498SchoolAd.published: ignore disabled/invalid rows and repeated IDs. */
    fun published(rows: List<JSONObject>): List<JSONObject> {
        val seen = HashSet<Int>()
        return rows.filter { ad ->
            val id = ad.optInt("id")
            id > 0 && ad.optString("school").trim().isNotEmpty() &&
                ad.optString("text").trim().isNotEmpty() &&
                ad.optBoolean("active", true) && ad.optBoolean("is_enabled", true) &&
                seen.add(id)
        }
    }
    fun revision(ad: JSONObject): String {
        val content = ad.optInt("id").toString() + "\u0000" +
            ad.optString("school").trim() + "\u0000" + ad.optString("text").trim()
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
