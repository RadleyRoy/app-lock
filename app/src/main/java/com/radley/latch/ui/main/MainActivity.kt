package com.radley.latch.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.radley.latch.ui.theme.LatchTheme

/**
 * FragmentActivity rather than ComponentActivity: androidx.biometric's BiometricPrompt is
 * fragment-hosted, and the settings screen needs to be able to re-verify the user.
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
}
