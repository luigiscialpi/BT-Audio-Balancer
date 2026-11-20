# BT Audio Balancer

App Android che ricorda bilanciamento L/R e amplificazione per ogni dispositivo Bluetooth audio e li applica da sola quando il dispositivo si connette.

## Come funziona

- Per ogni dispositivo (riconosciuto dal MAC address) si salvano: bilanciamento L/R (da -1 a +1), amplificazione (da 0 a +15 dB, a passi di 1 dB) e un interruttore per disabilitare il profilo.
- Il profilo si crea dall'app: con il dispositivo connesso, muovi uno slider (l'effetto si sente mentre trascini, si salva al rilascio) o l'interruttore. Dalla sezione "Profili salvati" si modifica o si elimina.
- Quando un dispositivo audio con un profilo si connette, un servizio in primo piano applica gli effetti; quando si disconnette li azzera. I dispositivi senza profilo e quelli non audio (orologi, mouse...) vengono ignorati.
- Gli effetti valgono per tutto l'audio del telefono, non per un solo dispositivo. Con più dispositivi connessi vale il profilo dell'ultimo connesso; se si disconnette, o se ne elimini il profilo, torna quello del precedente.
- Dopo che il sistema uccide il processo, il servizio riparte e riapplica i profili dei dispositivi A2DP ancora connessi.
- L'elenco dei dispositivi connessi nell'app si aggiorna da solo quando un dispositivo A2DP si connette o si disconnette, o il Bluetooth si accende o si spegne.

## Requisiti e permessi

- Android 8.0 o successivo (`minSdk 26`).
- Permesso Bluetooth: `BLUETOOTH_CONNECT` su Android 12+, chiesto all'avvio.
- Android 12+: per applicare un profilo quando l'app è chiusa, escludi l'app dall'ottimizzazione della batteria (Impostazioni → App → BT Audio Balancer → Batteria → Nessuna restrizione; su MIUI/HyperOS anche l'avvio automatico). Il sistema non permette di avviare un service in primo piano dal background in risposta a un broadcast Bluetooth, tranne con quell'esclusione ([documentazione Android](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).
- Per il bilanciamento di sistema serve concedere una volta, dal PC con l'app installata:

  ```bash
  adb shell pm grant com.example.btaudiobalancer android.permission.WRITE_SECURE_SETTINGS
  ```

  Senza il permesso l'app ripiega su altre strategie (DynamicsProcessing, `setParameters`) che dipendono dal telefono e possono non avere effetto. Finché il permesso manca, in cima alla lista compare un avviso.

## Meccanismi audio

**Bilanciamento L/R**: tripla strategia, vale la prima che riesce.

1. `Settings.System.putFloat(..., "master_balance", value)`. Richiede `WRITE_SECURE_SETTINGS`. È l'unica via verificata dall'autore, su un Redmi Note 9 Pro (MIUI/HyperOS). Si prova a scrivere senza passare da `Settings.System.canWrite`, che guarda solo `WRITE_SETTINGS` e darebbe falso anche con `WRITE_SECURE_SETTINGS` concesso.
2. `DynamicsProcessing` sulla sessione 0 (Android 9+). Sulla sessione 0 la creazione può riuscire senza che l'audio cambi: per questo viene dopo Settings.
3. `AudioManager.setParameters("master_balance=value")`, best effort.

Il centro (balance 0) e il reset toccano `master_balance` solo se l'app l'aveva spostato, per non sovrascrivere quello dell'Accessibilità.

**Amplificazione**: `LoudnessEnhancer(audioSessionId = 0)` con `setTargetGain(boostMb)`, da 0 a 1500 mB (+15 dB), su tutto l'audio di sistema, senza root.

## Limiti noti

- Su Android 12+, con l'app chiusa e senza l'esclusione dalla batteria, l'avvio del service dal receiver può essere rifiutato: l'app non crolla, ma il profilo non si applica alla connessione e un `master_balance` già impostato resta com'è alla disconnessione.
- L'amplificazione (`LoudnessEnhancer`) e il ripiego DynamicsProcessing agiscono sulla sessione audio globale 0, deprecata dalla documentazione Android: su alcune versioni o telefoni possono non sentirsi.
- `master_balance` vale per tutte le uscite audio, non solo per il Bluetooth. Se il processo viene ucciso mentre un profilo è applicato, il valore resta finché un nuovo profilo non lo sovrascrive.
- Né l'elenco dei connessi nell'app né il ripristino dopo la morte del processo coprono LE Audio: contano solo i dispositivi A2DP. Un dispositivo solo LE Audio riceve il profilo alla connessione (`BluetoothReceiver`) ma nell'app non compare tra i connessi, quindi da lì il profilo non si può creare.
- Dopo la morte del processo non si conosce l'ordine di connessione originale: vale quello restituito dal sistema.
- Verifica reale sul Redmi Note 9 Pro con Android 12/MIUI V140: il percorso `master_balance` non è stato scritto dal processo e il bilanciamento è ricaduto su `DynamicsProcessing`; a `+6 dB` `LoudnessEnhancer` ha raggiunto `600mB`, ma il volume è rimasto percepito invariato e l'audio è stato giudicato distorto.

## Architettura

Stack: Kotlin 2.0.21, Jetpack Compose (Material3), DataStore Preferences, JUnit 4; `minSdk 26`, `targetSdk 35`, AGP 8.5.2.

```
app/src/main/java/com/example/btaudiobalancer/
├── BtBalancerApp.kt                  ← Application class
├── MainActivity.kt                   ← entry Compose, chiede il permesso Bluetooth
├── audio/
│   └── AudioEffectApplier.kt         ← CORE: balance tripla strategia + LoudnessEnhancer
├── bluetooth/
│   └── BluetoothAudio.kt             ← isAudio() (esclude i device non audio), displayName(), queryConnectedA2dp() (device A2DP connessi adesso)
├── data/
│   ├── DeviceAudioSettings.kt        ← data class: mac, name, balance(-1..1), boostMb(0..1500), enabled
│   └── DeviceSettingsRepository.kt   ← DataStore (iniettabile nel costruttore): chiavi prefissate per MAC, allDevices Flow
├── receiver/
│   └── BluetoothReceiver.kt          ← ACL_CONNECTED/DISCONNECTED → passa MAC e avvia AudioEffectService
├── service/
│   ├── AudioEffectService.kt         ← ForegroundService: coda di comandi APPLY/UPDATE/RESET, applica il profilo dell'ultimo device connesso
│   └── ConnectedDevices.kt           ← elenco dei device connessi in ordine (vale l'ultimo)
└── ui/
    ├── LatestThrottle.kt             ← passa solo l'ultimo valore, al massimo uno ogni intervallo (anteprima degli slider)
    ├── MainViewModel.kt              ← elenco dei connessi da A2DP, aggiornato dai broadcast di sistema; delega al Service
    ├── MainScreen.kt                 ← LazyColumn: sezione connessi + sezione profili salvati, visualizer L/R
    └── theme/                        ← Color, Type, Theme (dynamic color Android 12+)

app/src/test/java/com/example/btaudiobalancer/   ← JUnit 4: DeviceSettingsRepositoryTest, ConnectedDevicesTest, LatestThrottleTest
```

Scelte di progetto:

- **Un solo applier**: il `MainViewModel` non ne crea uno proprio, per evitare sovrascritture concorrenti. Le modifiche in tempo reale passano al `AudioEffectService` con `ACTION_UPDATE`.
- **Profili isolati**: il service tiene l'elenco (`connected`) dei device audio connessi che hanno un profilo. Un update dalla UI per un device che non è l'attivo viene solo salvato; se il service non lo conosceva, entra nell'elenco senza scavalcare l'attivo. Alla disconnessione dell'attivo si riapplica il profilo del precedente; se non ne resta nessuno si azzera e il service si ferma.
- **Comandi in coda**: APPLY, UPDATE e RESET passano da una coda e girano uno alla volta, perché l'applier non è thread-safe. La notifica in primo piano si ripubblica solo se il testo cambia, così l'anteprima degli slider non la ripubblica a ogni comando.
- **Salvataggio su azione dell'utente**: gli slider salvano al rilascio (`onValueChangeFinished`), mai alla comparsa della card. Durante il trascinamento applicano solo in anteprima, senza salvare (`MainViewModel.preview` tramite `LatestThrottle`: l'ultimo valore, al massimo 10 al secondo). Un profilo di un device non connesso viene salvato ma non applicato.
- **Filtro audio**: `BluetoothReceiver` esclude i device non audio (`isAudio()`); la lista "connessi" della UI viene dal profilo A2DP, che per definizione è audio.
- **Device connessi**: li chiede `queryConnectedA2dp()` (`getProfileProxy` + `getConnectedDevices`), usata sia dalla UI sia dal ripristino. Dopo un restart senza intent il service li accoda come nuove connessioni; la UI si aggiorna con un receiver registrato dal `MainViewModel` (broadcast protetti: `A2DP CONNECTION_STATE_CHANGED`, `ADAPTER STATE_CHANGED`).

## Build e test

Servono JDK 17 e l'Android SDK. Su macOS `./setup.sh` cerca l'SDK e scrive `local.properties`; altrove basta aprire il progetto in Android Studio.

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installa sul dispositivo connesso via ADB
./gradlew testDebugUnitTest    # test unitari JVM, senza dispositivo
```

I test coprono il salvataggio per dispositivo (`DeviceSettingsRepositoryTest`), la regola "vince l'ultimo connesso" (`ConnectedDevicesTest`) e la cadenza dell'anteprima degli slider (`LatestThrottleTest`).

Le prove su un telefono vero (Bluetooth, audio di sistema, interfaccia) non sono automatizzabili: la checklist è in [`TEST-SU-DISPOSITIVO.md`](TEST-SU-DISPOSITIVO.md).

## Attività aperte

Bug noti e lavori da fare: [`TODO.md`](TODO.md).
