package com.example.btaudiobalancer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.btaudiobalancer.ui.MainScreen
import com.example.btaudiobalancer.ui.MainViewModel
import com.example.btaudiobalancer.ui.theme.BtAudioBalancerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        viewModel.setPermissionGranted(granted)
        if (granted) {
            viewModel.refreshConnectedDevices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            BtAudioBalancerTheme {
                val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
                val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()

                MainScreen(
                    uiState        = uiState,
                    savedDevices   = savedDevices,
                    onSettingsChange = { viewModel.updateAndApply(it) },
                    onDelete       = { viewModel.deleteDevice(it) },
                    onRefresh      = { viewModel.refreshConnectedDevices() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermission()) {
            viewModel.refreshConnectedDevices()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        val perms = requiredPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = requiredPermissions()
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.setPermissionGranted(true)
            viewModel.refreshConnectedDevices()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }



    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
        }
    }
}
