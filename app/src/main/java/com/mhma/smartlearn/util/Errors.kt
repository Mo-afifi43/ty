package com.mhma.smartlearn.util

import com.mhma.smartlearn.data.network.SessionExpiredException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns exceptions into a sentence a student would actually understand. */
fun Throwable.friendlyMessage(): String = when (this) {
    is SessionExpiredException -> "Your session ended. Sign in again to refresh."
    is UnknownHostException, is ConnectException -> "You're offline. Showing what was saved earlier."
    is SocketTimeoutException -> "Smart Learning is taking too long to answer. Try again in a bit."
    is SSLException -> "Couldn't open a secure connection to Smart Learning."
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong while refreshing."
}
