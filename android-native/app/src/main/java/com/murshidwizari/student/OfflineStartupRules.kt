package com.murshidwizari.student

/** Network-independent startup without granting offline server privileges. */
object OfflineStartupRules {
    enum class Destination { OFFLINE_COACH, RESTORE_SERVER_SESSION, GUEST }

    fun destination(
        internetAvailable: Boolean,
        rememberedServerSession: Boolean,
        locallyVerifiedStudentId: Int
    ): Destination = when {
        !internetAvailable && locallyVerifiedStudentId > 0 -> Destination.OFFLINE_COACH
        !internetAvailable -> Destination.GUEST
        rememberedServerSession -> Destination.RESTORE_SERVER_SESSION
        else -> Destination.GUEST
    }
}
