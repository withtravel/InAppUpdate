package app.atta.inappupdate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE
import com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
import com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
import com.google.android.play.core.ktx.AppUpdateResult
import kotlinx.android.synthetic.main.activity_in_app_update.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class InAppActivity : AppCompatActivity() {

    private lateinit var appUpdateManager: AppUpdateManager
    private val updateViewModel: UpdateViewModel by viewModels {
        UpdateViewModelFactory(
                this,
                AppUpdateManagerFactory.create(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_update)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        // update immediately if update in progress
        if (appUpdateInfo?.updateAvailability() == DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            appUpdateManager.startUpdateFlowForResult(
                    requireNotNull(appUpdateInfo),
                    IMMEDIATE,
                    this@InAppActivity,
                    UPDATE_CONFIRMATION_REQ_CODE
            )
        } else {
            addUpdateViewModelObservers()
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e(
                InAppActivity::class.simpleName,
                "onActivityResult. req code -> $requestCode result code -> $resultCode"
        )
        if (requestCode == UPDATE_CONFIRMATION_REQ_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    // The user accepted the request to update
                }
                RESULT_IN_APP_UPDATE_FAILED,
                Activity.RESULT_CANCELED -> {
                    Log.e(
                            InAppActivity::class.simpleName,
                            "failed to update the app, about to finish. result code -> $resultCode"
                    )
                    setResult(resultCode)
                    finish()
                }
            }
        }
    }

    private fun pendingUpdate(updateResult: AppUpdateResult) {
        Log.e(InAppActivity::class.simpleName, "pendingUpdate() -> $updateResult")
        when (updateResult) {
            is AppUpdateResult.NotAvailable -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            is AppUpdateResult.Available -> {
                text_message.text = getString(R.string.update_message_available)
                button_positive.setOnClickListener {
                    updateViewModel.invokeUpdate()
                }
                button_negative.setOnClickListener {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
            is AppUpdateResult.InProgress -> {
                val updateProgress: Int =
                        if (updateResult.installState.totalBytesToDownload() == 0L) {
                            0
                        } else {
                            (updateResult.installState.bytesDownloaded() * 100 /
                                    updateResult.installState.totalBytesToDownload()).toInt()
                        }
                progressBar.progress = updateProgress
                text_message.text = getString(R.string.update_message_downloading, updateProgress)
                if (button_positive.visibility != View.GONE) button_positive.visibility = View.GONE
                if (button_negative.visibility != View.GONE) button_negative.visibility = View.GONE
            }
            is AppUpdateResult.Downloaded -> {
                text_message.text = getString(R.string.update_message_downloaded)
                updateViewModel.invokeUpdate()
            }
        }
    }

    private fun addUpdateViewModelObservers() {
        with(updateViewModel) {
            updateStatus.observe(
                    this@InAppActivity, Observer { updateResult: AppUpdateResult ->
                pendingUpdate(updateResult)
            })
            events.onEach { event ->
                Log.e(InAppActivity::class.simpleName, "addUpdateViewModelObservers() -> $event")
                when (event) {
                    is Event.ErrorEvent,
                    is Event.NoUpdate -> {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                    is Event.ToastEvent -> {
                        showToast(event.message)
                    }
                    is Event.StartUpdateEvent -> {
                        val updateType =
                                if (event.immediate) {
                                    IMMEDIATE
                                } else {
                                    FLEXIBLE
                                }
                        appUpdateManager.startUpdateFlowForResult(
                                event.updateInfo,
                                updateType,
                                this@InAppActivity,
                                UPDATE_CONFIRMATION_REQ_CODE
                        )
                    }
                    else -> throw IllegalStateException("Event type not handled: $event")
                }
            }.launchIn(lifecycleScope)
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private var appUpdateInfo: AppUpdateInfo? = null
        private const val UPDATE_CONFIRMATION_REQ_CODE = 1
        private val contract = ActivityResultContracts.StartActivityForResult()

        fun getIntent(context: Context) = Intent(context, InAppActivity::class.java)

        fun registerCallback(
                component: ComponentActivity,
                callback: (res: ActivityResult) -> Unit
        ): ActivityResultLauncher<Intent> {
            return component.registerForActivityResult(contract, callback)
        }

        @ExperimentalCoroutinesApi
        suspend fun checkUpdateAvailable(context: Context) =
                suspendCancellableCoroutine<Boolean> { continuation ->
                    // Checks that the platform will allow the specified type of update.
                    AppUpdateManagerFactory.create(context).appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                        this.appUpdateInfo = appUpdateInfo
                        appUpdateInfo.updateAvailability()
                        val res = when (appUpdateInfo.updateAvailability()) {
                            DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                            UPDATE_AVAILABLE -> {
                                true
                            }
                            else -> false
                        }
                        continuation.resume(res)
                    }
                }
    }
}