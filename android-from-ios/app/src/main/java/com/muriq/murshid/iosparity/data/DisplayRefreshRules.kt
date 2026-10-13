package com.muriq.murshid.iosparity.data

/** Best-effort iOS ProMotion-equivalent refresh cap; not a guarantee of 120 FPS. */
object DisplayRefreshRules {
    fun priority(rateHz: Float): Float = if (rateHz in 30f..120.5f) rateHz else -1f
}
