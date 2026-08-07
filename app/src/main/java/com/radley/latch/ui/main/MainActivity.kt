package com.radley.latch.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.radley.latch.lock.LatchPermissions
import com.radley.latch.lock.LockWatchService
import com.radley.latch.ui.theme.LatchTheme

/**
 * FragmentActivity rather than ComponentActivity: androidx.biometric's BiometricPrompt is
 * fragment-hosted.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LatchTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Started from here rather than at app start: before the grants exist there is nothing
        // for the watcher to do, and a foreground notification with no protection behind it
        // would be actively misleading.
        if (LatchPermissions.canLock(this)) {
            LockWatchService.start(this)
        }
    }
}
