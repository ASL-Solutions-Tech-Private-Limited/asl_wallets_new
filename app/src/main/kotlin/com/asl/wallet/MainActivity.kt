package com.asl.wallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.ui.AppViewModel
import com.asl.wallet.ui.AslWalletApp
import com.asl.wallet.update.AppUpdateController
import com.asl.wallet.update.UpdateState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appLock: AppLockManager
    private lateinit var update: AppUpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Forced in-app update: result of Play's immediate update flow. A cancelled update keeps the gate blocking.
        val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            update.onFlowResult(result.resultCode == RESULT_OK)
        }
        update = AppUpdateController(this, updateLauncher)

        setContent {
            val vm: AppViewModel = hiltViewModel()
            val updateState by update.state.collectAsStateWithLifecycle()
            AslWalletApp(
                vm,
                updateRequired = updateState == UpdateState.Required || updateState == UpdateState.InProgress,
                updateInProgress = updateState == UpdateState.InProgress,
                onUpdate = update::promptUpdate,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on every foreground so a newly published mandatory update blocks the app, and an
        // interrupted immediate update is resumed.
        update.check()
    }

    /** Every touch resets the 5-minute idle lock timer. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        appLock.onUserInteraction()
    }
}
