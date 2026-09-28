package com.mhma.smartlearn.ui.navigation

import androidx.lifecycle.ViewModel
import com.mhma.smartlearn.data.network.SessionManager
import com.mhma.smartlearn.data.network.SessionState
import com.mhma.smartlearn.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    session: SessionManager,
    sync: SyncManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = session.state

    init {
        // Cold start with a saved session: bring things up to date in the background.
        if (session.isActive) sync.refreshIfStale()
    }
}
