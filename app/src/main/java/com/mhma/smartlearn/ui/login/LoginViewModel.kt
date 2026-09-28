package com.mhma.smartlearn.ui.login

import androidx.lifecycle.ViewModel
import com.mhma.smartlearn.data.network.SessionManager
import com.mhma.smartlearn.data.network.SessionState
import com.mhma.smartlearn.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: SessionManager,
    private val sync: SyncManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = session.state

    /** Called by the WebView the moment it sees a signed-in page. */
    fun onSessionCookie(name: String, value: String) {
        val wasActive = session.isActive
        session.signIn(name, value)
        if (!wasActive) sync.refreshAll()
    }
}
