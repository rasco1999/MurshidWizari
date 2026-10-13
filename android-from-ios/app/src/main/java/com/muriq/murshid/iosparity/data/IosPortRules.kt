package com.muriq.murshid.iosparity.data

/** Five tabs stay visible. Subscription access is checked inside the destination. */
object IosPortRules {
    @Suppress("UNUSED_PARAMETER")
    fun visibleTabs(subscribed: Boolean): List<Int> = listOf(0, 1, 2, 3, 4)
}
