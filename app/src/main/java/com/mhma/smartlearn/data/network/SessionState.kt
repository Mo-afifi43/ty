package com.mhma.smartlearn.data.network

enum class SessionState {
    /** No cookie stored. Show the sign-in page. */
    SIGNED_OUT,

    /** We have a cookie and nothing has rejected it yet. */
    ACTIVE,

    /** Moodle bounced us to the login page. Keep the cached data, ask to sign in again. */
    EXPIRED,
}
