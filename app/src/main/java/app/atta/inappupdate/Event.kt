package app.atta.inappupdate

import com.google.android.play.core.appupdate.AppUpdateInfo

sealed class Event {
    data class ErrorEvent(val message: String) : Event()
    data class ToastEvent(val message: String) : Event()
    data class NoUpdate(val message: String) : Event()
    data class StartUpdateEvent(val updateInfo: AppUpdateInfo, val immediate: Boolean) : Event()
}
