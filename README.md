# BT Audio Balancer

App Android che ricorda bilanciamento L/R e amplificazione per ogni dispositivo Bluetooth audio e li applica da sola quando il dispositivo si connette.

## Come funziona

- Per ogni dispositivo (riconosciuto dal MAC address) si salvano: bilanciamento L/R (da -1 a +1), amplificazione (da 0 a +15 dB, a passi di 1 dB) e un interruttore per disabilitare il profilo.
- Il profilo si crea dall'app: con il dispositivo connesso, muovi uno slider (si salva al rilascio) o l'interruttore. Dalla sezione "Profili salvati" si modifica o si elimina.
- Quando un dispositivo audio con un profilo si connette, un servizio in primo piano applica gli effetti; quando si disconnette li azzera. I dispositivi senza profilo e quelli non audio (orologi, mouse...) vengono ignorati.
- Gli effetti valgono per tutto l'audio del telefono, non per un solo dispositivo. Con più dispositivi connessi vale il profilo dell'ultimo connesso; se si disconnette, torna quello del precedente.
- Dopo che il sistema uccide il processo, il servizio riparte e riapplica i profili dei dispositivi A2DP ancora connessi.

## Requisiti e permessi

- Android 8.0 o successivo (`minSdk 26`).
- Permesso Bluetooth: `BLUETOOTH_CONNECT` su Android 12+, chiesto all'avvio.
- Per il bilanciamento di sistema serve concedere una volta, dal PC con l'app installata:

  ```bash
  adb shell pm grant com.example.btaudiobalancer android.permission.WRITE_SECURE_SETTINGS
  ```

  Senza il permesso l'app ripiega su altre strategie (DynamicsProcessing, `setParameters`) che dipendono dal telefono e possono non avere effetto.

## Meccanismi audio

**Bilanciamento L/R**: tripla strategia, vale la prima che riesce.

1. `Settings.System.putFloat(..., "master_balance", value)`. Richiede `WRITE_SECURE_SETTINGS`. È l'unica via verificata dall'autore, su un Redmi Note 9 Pro (MIUI/HyperOS). Si prova a scrivere senza passare da `Settings.System.canWrite`, che guarda solo `WRITE_SETTINGS` e darebbe falso anche con `WRITE_SECURE_SETTINGS` concesso.
2. `DynamicsProcessing` sulla sessione 0 (Android 9+). Sulla sessione 0 la creazione può riuscire senza che l'audio cambi: per questo viene dopo Settings.
3. `AudioManager.setParameters("master_balance=value")`, best effort.

Il centro (balance 0) e il reset toccano `master_balance` solo se l'app l'aveva spostato, per non sovrascrivere quello dell'Accessibilità.

**Amplificazione**: `LoudnessEnhancer(audioSessionId = 0)` con `setTargetGain(boostMb)`, da 0 a 1500 mB (+15 dB), su tutto l'audio di sistema, senza root.

## Limiti noti

- L'amplificazione (`LoudnessEnhancer`) e il ripiego DynamicsProcessing agiscono sulla sessione audio globale 0, deprecata dalla documentazione Android: su alcune versioni o telefoni possono non sentirsi.
- `master_balance` vale per tutte le uscite audio, non solo per il Bluetooth. Se il processo viene ucciso mentre un profilo è applicato, il valore resta finché un nuovo profilo non lo sovrascrive.
- Il ripristino dopo la morte del processo non copre LE Audio e non conosce l'ordine di connessione originale.

## Architettura

Stack: Kotlin 2.0.21, Jetpack Compose (Material3), DataStore Preferences, JUnit 4; `minSdk 26`, `targetSdk 35`, AGP 8.5.2.

```
app/src/main/java/com/example/btaudiobalancer/
├── BtBalancerApp.kt                  ← Application class
├── MainActivity.kt                   ← entry Compose, chiede il permesso Bluetooth
├── audio/
│   └── AudioEffectApplier.kt         ← CORE: balance tripla strategia + LoudnessEnhancer
├── bluetooth/
│   └── BluetoothAudio.kt             ← isAudio(): esclude i device non audio
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

Scelte di progetto:

- **Un solo applier**: il `MainViewModel` non ne crea uno proprio, per evitare sovrascritture concorrenti. Le modifiche in tempo reale passano al `AudioEffectService` con `ACTION_UPDATE`.
- **Profili isolati**: il service tiene l'elenco (`connected`) dei device audio connessi che hanno un profilo. Un update dalla UI per un device che non è l'attivo viene solo salvato. Alla disconnessione dell'attivo si riapplica il profilo del precedente; se non ne resta nessuno si azzera e il service si ferma.
- **Comandi in coda**: APPLY, UPDATE e RESET passano da una coda e girano uno alla volta, perché l'applier non è thread-safe.
- **Salvataggio su azione dell'utente**: gli slider salvano e applicano solo al rilascio (`onValueChangeFinished`), mai alla comparsa della card. Un profilo di un device non connesso viene salvato ma non applicato.
- **Filtro audio**: `BluetoothReceiver` e la lista "connessi" della UI escludono i device non audio (`isAudio()`).
- **Ripristino**: dopo un restart senza intent il service rilegge i device A2DP connessi (`getProfileProxy`) e li accoda come nuove connessioni.

## Build e test

Servono JDK 17 e l'Android SDK. Su macOS `./setup.sh` cerca l'SDK e scrive `local.properties`; altrove basta aprire il progetto in Android Studio.

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installa sul dispositivo connesso via ADB
./gradlew testDebugUnitTest    # test unitari JVM, senza dispositivo
```

I test coprono il salvataggio per dispositivo (`DeviceSettingsRepositoryTest`) e la regola "vince l'ultimo connesso" (`ConnectedDevicesTest`).

## Attività aperte

Bug noti e lavori da fare: [`TODO.md`](TODO.md).
