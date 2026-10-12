package com.murshidwizari.student

/** Push wakes the app; account details always come from the authenticated inbox. */
object PushRules {
    fun validBinding(value: String): Boolean = value.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
    fun accepts(activeUser: Int, activeBinding: String, incomingBinding: String,
                notificationId: Long, lastSeen: Long, permission: Boolean): Boolean =
        activeUser > 0 && validBinding(activeBinding) && activeBinding == incomingBinding &&
            notificationId > 0 && notificationId > lastSeen && permission
}
