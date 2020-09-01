package app.atta.inappupdate

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.ktx.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for InAppUpdates.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class UpdateViewModel(
    manager: AppUpdateManager
) : ViewModel() {

    val updateStatus = manager.requestUpdateFlow().catch {
        Log.e("UpdateViewModel", "something went wrong. error -> ${it.message}")
        _events.send(Event.ErrorEvent("something went wrong. error -> ${it.message}"))
    }.asLiveData()

    private val _events = BroadcastChannel<Event>(Channel.BUFFERED)
    val events = _events.asFlow()

    fun invokeUpdate() {
        Log.e("UpdateViewModel", "invokeUpdate. status -> ${updateStatus.value}")
        when (val updateResult = updateStatus.value) {
            is AppUpdateResult.NotAvailable -> viewModelScope.launch {
                _events.send(Event.NoUpdate("No update available"))
            }
            is AppUpdateResult.Available -> {
                with(updateResult.updateInfo) {
                    if (isImmediateUpdateAllowed && (clientVersionStalenessDays ?: 0 > 7 ||
                                updatePriority > 4)
                    ) {
                        viewModelScope.launch {
                            _events.send(Event.StartUpdateEvent(updateResult.updateInfo, true))
                        }
                    } else if (isFlexibleUpdateAllowed) {
                        viewModelScope.launch {
                            _events.send(Event.StartUpdateEvent(updateResult.updateInfo, false))
                        }
                    } else {
                        throw IllegalStateException("Not implemented: Handling for $this")
                    }
                }
            }
            is AppUpdateResult.InProgress -> viewModelScope.launch {
                _events.send(Event.ToastEvent("Update already in progress"))
            }
            is AppUpdateResult.Downloaded -> viewModelScope.launch {
                updateResult.completeUpdate()
            }
        }
    }
}


class UpdateViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val updateManager: AppUpdateManager,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
        key: String, modelClass: Class<T>, handle: SavedStateHandle
    ): T {
        return UpdateViewModel(updateManager) as T
    }
}