package com.example.btaudiobalancer.ui

import android.content.pm.PackageManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.btaudiobalancer.data.DeviceAudioSettings
import kotlin.math.roundToInt

private const val SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: UiState,
    savedDevices: List<DeviceAudioSettings>,
    onSettingsChange: (DeviceAudioSettings) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    // shortcut: letto a ogni ricomposizione, dopo un grant via adb l'avviso sparisce alla prossima apertura.
    val hasSecureSettings = ContextCompat.checkSelfPermission(
        LocalContext.current, SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, contentDescription = null,
                            modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("BT Audio Balancer", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (!hasSecureSettings) {
                item { PermissionHint() }
            }

            // -- Sezione: device connessi ------------------------------------------
            item {
                SectionHeader(
                    icon = Icons.Default.Bluetooth,
                    title = "Dispositivi connessi",
                    count = uiState.connectedDevices.size
                )
            }

            if (uiState.connectedDevices.isEmpty()) {
                item {
                    EmptyCard(
                        icon = Icons.Default.BluetoothDisabled,
                        message = "Nessun dispositivo Bluetooth connesso.\nCollega un auricolare e premi Aggiorna."
                    )
                }
            } else {
                items(
                    items = uiState.connectedDevices,
                    key = { it.deviceAddress }
                ) { device ->
                    val saved = savedDevices.find { it.deviceAddress == device.deviceAddress }
                    DeviceCard(
                        settings = saved ?: device,
                        isConnected = true,
                        onSettingsChange = onSettingsChange,
                        onDelete = { onDelete(device.deviceAddress) }
                    )
                }
            }

            // -- Sezione: device salvati non connessi ------------------------------
            val offlineDevices = savedDevices.filter { saved ->
                uiState.connectedDevices.none { it.deviceAddress == saved.deviceAddress }
            }

            if (offlineDevices.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    SectionHeader(
                        icon = Icons.Default.BluetoothDisabled,
                        title = "Profili salvati",
                        count = offlineDevices.size
                    )
                }
                items(
                    items = offlineDevices,
                    key = { it.deviceAddress }
                ) { device ->
                    DeviceCard(
                        settings = device,
                        isConnected = false,
                        onSettingsChange = onSettingsChange,
                        onDelete = { onDelete(device.deviceAddress) }
                    )
                }
            }
        }
    }
}

// -- Componenti -------------------------------------------------------------------

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Badge { Text("$count") }
    }
}

/** Mostrato una volta in cima alla lista finche' manca il permesso che rende affidabile il balance. */
@Composable
private fun PermissionHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Per bilanciare l'audio di sistema serve un permesso da concedere una volta dal PC. " +
                    "Senza, il bilanciamento può non avere effetto: il comando è nel README.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Card per un singolo device Bluetooth con controlli di
 * bilanciamento L/R e amplificazione.
 *
 * Salva solo su azione dell'utente: al rilascio dello slider, al cambio dello
 * switch o al tocco su "Centra". Mai alla semplice comparsa della card.
 */
@Composable
fun DeviceCard(
    settings: DeviceAudioSettings,
    isConnected: Boolean,
    onSettingsChange: (DeviceAudioSettings) -> Unit,
    onDelete: () -> Unit
) {
    // Valori mostrati durante il trascinamento. Si riallineano a quelli salvati quando
    // cambiano (es. DataStore caricato dopo il primo disegno della card).
    var balance by remember(settings.balance) { mutableFloatStateOf(settings.balance) }
    var boostMb by remember(settings.boostMb) { mutableIntStateOf(settings.boostMb) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il profilo?") },
            text = { Text("Bilanciamento e amplificazione salvati per ${settings.deviceName} andranno persi.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isConnected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // -- Header ---------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                // Nome (fino a 2 righe) e, sotto, stato + MAC su una riga: lo stato resta in testa e
                // il MAC si accorcia per primo. Niente chip, che su schermi stretti toglieva spazio al nome.
                val primary = MaterialTheme.colorScheme.primary
                Column(Modifier.weight(1f)) {
                    Text(
                        text = settings.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildAnnotatedString {
                            if (isConnected) {
                                withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
                                    append("Connesso")
                                }
                                append(" · ")
                            }
                            append(settings.deviceAddress)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Switch abilitazione (dimensione naturale: il target di tocco resta >= 48 dp)
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) }
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }

            if (!settings.enabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Profilo disabilitato -- non verra applicato alla connessione",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // -- Bilanciamento L/R ----------------------------------------------------
            // Il salvataggio legge lo stato al momento del rilascio, non un valore catturato prima.
            BalanceControl(
                balance = balance,
                onBalanceChange = { balance = it },
                onBalanceChangeFinished = { onSettingsChange(settings.copy(balance = balance)) }
            )

            Spacer(Modifier.height(20.dp))

            // -- Amplificazione -------------------------------------------------------
            BoostControl(
                boostMb = boostMb,
                onBoostChange = { boostMb = it },
                onBoostChangeFinished = { onSettingsChange(settings.copy(boostMb = boostMb)) }
            )
        }
    }
}

@Composable
private fun BalanceControl(
    balance: Float,
    onBalanceChange: (Float) -> Unit,
    onBalanceChangeFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bilanciamento L/R", style = MaterialTheme.typography.labelLarge)
            Text(
                text = balanceLabel(balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))

        // Barra visuale L/R
        BalanceVisualizer(balance = balance)

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("L", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = balance,
                onValueChange = onBalanceChange,
                onValueChangeFinished = onBalanceChangeFinished,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("R", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Bottone reset al centro
        if (balance != 0f) {
            TextButton(
                onClick = {
                    onBalanceChange(0f)
                    onBalanceChangeFinished()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Centra", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BalanceVisualizer(balance: Float) {
    // Barra L/R con gradiente
    val leftAlpha  = if (balance <= 0) 1f else 1f - balance
    val rightAlpha = if (balance >= 0) 1f else 1f + balance

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = leftAlpha * 0.8f)
                )
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.secondary.copy(alpha = rightAlpha * 0.8f)
                )
        )
    }
}

@Composable
private fun BoostControl(
    boostMb: Int,
    onBoostChange: (Int) -> Unit,
    onBoostChangeFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Amplificazione", style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (boostMb == 0) "Off" else "+${boostMb / 100f} dB",
                style = MaterialTheme.typography.labelMedium,
                color = if (boostMb > 0) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        if (boostMb > 900) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Attenzione: livelli alti possono distorcere l'audio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = boostMb.toFloat(),
                onValueChange = { onBoostChange(it.roundToInt()) },
                onValueChangeFinished = onBoostChangeFinished,
                valueRange = 0f..1500f,
                steps = 14, // step da 100 mB = 1 dB
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary
                )
            )
            Text("+15dB", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// -- Helpers ----------------------------------------------------------------------

private fun balanceLabel(balance: Float): String = when {
    balance < -0.05f -> "L ${((-balance) * 100).roundToInt()}%"
    balance > 0.05f  -> "R ${(balance * 100).roundToInt()}%"
    else             -> "Centro"
}
