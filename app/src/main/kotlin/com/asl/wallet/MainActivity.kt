package com.asl.wallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.ui.AppViewModel
import com.asl.wallet.ui.AslWalletApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = hiltViewModel()
            AslWalletApp(vm)
        }
    }

    /** Every touch resets the 5-minute idle lock timer. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        appLock.onUserInteraction()
    }
}
