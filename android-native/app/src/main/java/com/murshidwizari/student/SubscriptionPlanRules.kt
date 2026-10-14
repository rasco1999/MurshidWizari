package com.murshidwizari.student

/** Server response compatibility for subscription plans.
 * Existing PHP mobile/bootstrap uses 'key'; older mobile clients used 'id'.
 * Keep all plan amounts server-authored; never inject client-side prices.
 */
internal object SubscriptionPlanRules {
    fun identifier(id: String?, key: String?): String =
        key.orEmpty().trim().ifBlank { id.orEmpty().trim() }

    fun selectable(id: String, amount: Long): Boolean = id.isNotBlank() && amount > 0

    fun selected(validIds: List<String>, current: String): String =
        if (current in validIds) current else validIds.firstOrNull().orEmpty()
}
