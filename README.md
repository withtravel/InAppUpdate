# InAppUpdate
This module provides the UI to manage update with support-in-update.

### Example
```
val startForResult = InAppActivity.registerCallback(this) { result: ActivityResult ->
    when (result.resultCode) {
        Activity.RESULT_OK,
        Activity.RESULT_CANCELED -> {
          // put code you want
        }
    }
}
lifecycleScope.launch {
    if (InAppActivity.checkUpdateAvailable(this@SplashActivity)) {
        // launch InAppActivity, you will get result within regiterCallback
        startForResult.launch(InAppActivity.getIntent(this@SplashActivity))
    } else {
        // put code you want
    }
}
```

### Reference
[Support-in-update](https://developer.android.com/guide/playcore/in-app-updates)
