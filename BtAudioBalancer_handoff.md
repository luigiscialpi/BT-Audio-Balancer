# Handoff — BtAudioBalancer

Note di sviluppo. Uso, permessi e build: [`README.md`](README.md). Attività aperte e bug noti: [`TODO.md`](TODO.md).

## Architettura
```
app/src/main/java/com/example/btaudiobalancer/
├── BtBalancerApp.kt                  ← Application class
├── MainActivity.kt                   ← entry Compose, chiede il permesso Bluetooth
├── audio/
│   └── AudioEffectApplier.kt         ← CORE: balance tripla strategia (Settings/DSP/setParameters) + LoudnessEnhancer
├── bluetooth/
│   └── BluetoothAudio.kt             ← isAudio(): esclude i device non audio (orologi, mouse...)
├── data/
│   ├── DeviceAudioSettings.kt        ← data class: mac, name, balance(-1..1), boostMb(0..1500), enabled
│   └── DeviceSettingsRepository.kt   ← DataStore (iniettabile nel costruttore): chiavi prefissate per MAC, allDevices Flow
├── receiver/
│   ├── BluetoothReceiver.kt          ← ACL_CONNECTED/DISCONNECTED → passa MAC e avvia AudioEffectService
│   └── BootReceiver.kt               ← vuoto, da eliminare (M6)
├── service/
│   ├── AudioEffectService.kt         ← ForegroundService: coda di comandi APPLY/UPDATE/RESET, applica il profilo dell'ultimo device connesso
│   └── ConnectedDevices.kt           ← elenco dei device connessi in ordine (vale l'ultimo)
└── ui/
    ├── MainViewModel.kt              ← refreshConnectedDevices via reflection isConnected(), delega al Service
    ├── MainScreen.kt                 ← LazyColumn: sezione connessi + sezione profili salvati, visualizer L/R
    └── theme/                        ← Color, Type, Theme (dynamic color Android 12+)

app/src/test/java/com/example/btaudiobalancer/   ← JUnit 4: DeviceSettingsRepositoryTest, ConnectedDevicesTest
```

## Meccanismi audio

| Funzione | Implementazione | Note |
|---|---|---|
| Bilanciamento L/R | **Tripla Strategia con Fallback**: <br> 1. `Settings.System.putFloat(..., "master_balance", value)` <br> 2. `DynamicsProcessing` (DSP sessione 0) <br> 3. `AudioManager.setParameters("master_balance=value")` | **Strategia 1**: Richiede il permesso `WRITE_SECURE_SETTINGS` concesso via ADB. È l'unica via affidabile e testata su MIUI/HyperOS (Redmi Note 9 Pro) per spostare l'audio di sistema. Si prova a scrivere senza `Settings.System.canWrite`, che guarda solo `WRITE_SETTINGS` e dava falso anche con `WRITE_SECURE_SETTINGS` concesso.<br>**Strategia 2**: Fallback DSP in user-space per dispositivi stock Android 9+ (API 28+). Sulla sessione 0 (deprecata) la creazione può riuscire senza che l'audio cambi: per questo viene dopo Settings.<br>Il centro (balance 0) e il reset toccano `master_balance` solo se l'app l'aveva spostato, per non sovrascrivere quello dell'Accessibilità. |
| Amplificazione | `LoudnessEnhancer(audioSessionId = 0)` + `setTargetGain(boostMb)` | Sessione globale → tutto l'audio di sistema. Range 0..1500 mB (0..+15 dB). Non serve root, ma la sessione 0 è deprecata dalla documentazione Android: l'effetto può non sentirsi su alcune versioni/device (da verificare). |

## Comportamento chiave
- **Isolamento dei profili (Anti Cross-Device Leak)**: Il `AudioEffectService` tiene l'elenco (`connected`) dei device audio connessi che hanno un profilo salvato; gli effetti sono globali, quindi vale quello dell'ultimo connesso. Update dalla UI per un altro device vengono solo salvati. Alla disconnessione dell'attivo si riapplica il profilo del precedente; se non ne resta nessuno si azzera e il service si ferma. Un device senza profilo viene ignorato. I comandi (APPLY/RESET/UPDATE) passano da una coda e girano uno alla volta. `BluetoothReceiver` e la lista "connessi" della UI escludono i device non audio (`isAudio()`). Se il sistema uccide il processo e riavvia il service (intent nullo), il service rilegge i device A2DP connessi (`getProfileProxy`) e riapplica i loro profili; l'ordine di connessione originale non si conosce.
- **Service Unico**: Il `MainViewModel` non istanzia un proprio `AudioEffectApplier` per evitare sovrascritture concorrenti; delega tutte le modifiche in tempo reale al `AudioEffectService` via `ACTION_UPDATE` Intent.
- **UI & Performance**: Gli slider salvano e applicano solo al rilascio (`onValueChangeFinished`), mai alla comparsa della card, quindi una scrittura DataStore per gesto e nessun salvataggio spontaneo all'apertura dell'app. Un profilo di un device non connesso viene salvato ma non applicato.
