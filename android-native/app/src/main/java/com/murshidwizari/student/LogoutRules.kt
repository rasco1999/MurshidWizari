package com.murshidwizari.student

/** iOS logout keeps the account when the server rejects or loses the request. */
object LogoutRules {
    fun mayClearLocalSession(csrf: String, serverConfirmed: Boolean): Boolean =
        csrf.isBlank() || serverConfirmed
}
