package com.mhma.smartlearn.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "are we signed in?".
 *
 * The network layer calls [markExpired] when Moodle redirects to the login page;
 * the UI watches [state] and shows the sign-in screen when needed.
 */
@Singleton
class SessionManager @Inject constructor(
    private val store: SessionStore,
) {
    private val _state = MutableStateFlow(
        if (store.hasCookie()) SessionState.ACTIVE else SessionState.SIGNED_OUT
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value == SessionState.ACTIVE

    fun cookieHeader(): String? = store.cookieHeader()

    fun signIn(cookieName: String, cookieValue: String) {
        store.save(cookieName, cookieValue)
        _state.value = SessionState.ACTIVE
    }

    fun markExpired() {
        store.clear()
        _state.update { current ->
            if (current == SessionState.SIGNED_OUT) current else SessionState.EXPIRED
        }
    }

    fun signOut() {
        store.clear()
        _state.value = SessionState.SIGNED_OUT
    }
}
