# Handoff — BtAudioBalancer

## Contesto
App Android in Kotlin + Jetpack Compose che bilancia L/R e amplifica l'audio di sistema per ogni dispositivo Bluetooth, ricordando le impostazioni per MAC address.

## Stack
- Kotlin + Jetpack Compose Material3
- DataStore Preferences (persistenza)
- `minSdk 26`, `targetSdk 35`, AGP 8.5.2, Kotlin 2.0.21

## Architettura
```
BtAudioBalancer/app/src/main/java/com/example/btaudiobalancer/
├── BtBalancerApp.kt                  ← Application class
├── MainActivity.kt                   ← permessi BLUETOOTH_CONNECT + entry Compose
├── audio/
│   └── AudioEffectApplier.kt         ← CORE: balance via setParameters() + LoudnessEnhancer(0)
├── data/
│   ├── DeviceAudioSettings.kt        ← data class: mac, name, balance(-1..1), boostMb(0..1500), enabled
│   └── DeviceSettingsRepository.kt   ← DataStore: chiavi prefissate per MAC, allDevices Flow
├── receiver/
│   ├── BluetoothReceiver.kt          ← ACTION_ACL_CONNECTED/DISCONNECTED → avvia AudioEffectService
│   └── BootReceiver.kt               ← placeholder per BOOT_COMPLETED
├── service/
│   └── AudioEffectService.kt         ← ForegroundService (connectedDevice): applica o resetta settings
└── ui/
    ├── MainViewModel.kt              ← refreshConnectedDevices via reflection isConnected(), updateAndApply()
    ├── MainScreen.kt                 ← LazyColumn: sezione connessi + sezione profili salvati
    └── theme/                        ← Color, Type, Theme (dynamic color Android 12+)
```

## Meccanismi audio

| Funzione | Implementazione | Note |
|---|---|---|
| Bilanciamento L/R | `AudioManager.setParameters("master_balance=X")` | Best-effort, funziona su MIUI/molti vendor, ignorato silenziosamente altrove. Range -1.0..+1.0 |
| Amplificazione | `LoudnessEnhancer(audioSessionId = 0)` + `setTargetGain(boostMb)` | Sessione globale → tutto l'audio di sistema. Range 0..1500 mB (0..+15 dB). Funziona su tutti i device senza root |

## Comportamento chiave
- **Device nuovo** (MAC non in DataStore): il service si avvia ma non applica nulla
- **Device conosciuto**: al `ACL_CONNECTED` il service legge il profilo da DataStore e lo applica immediatamente
- **Disconnessione**: il service chiama `reset()` (balance=0, LoudnessEnhancer rilasciato) poi si ferma
- **UI**: lo slider salva+applica in tempo reale tramite `LaunchedEffect(balance, boostMb, enabled)`; switch per disabilitare un profilo senza cancellarlo

## Stato build (aggiornato)
- `./gradlew assembleDebug` **OK** (build verde)
- Warning non bloccante: AGP `8.5.2` con `compileSdk 35` (AGP testato fino a 34)
- Ambiente locale: utile mantenere memoria Gradle piu alta per evitare stop del daemon su macchine con heap default basso

## Modifiche recenti (2026-06-11)
1. **Risolto fail `checkDebugAarMetadata`**
    - Aggiunto `android.useAndroidX=true` in `gradle.properties`
2. **Risolto stop daemon Gradle per GC thrashing**
    - Aggiunto `org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8` in `gradle.properties`
3. **Risolto errore Kotlin `Unresolved reference 'serialization'`**
    - Rimosso import non necessario `kotlinx.serialization.Serializable` da `DeviceAudioSettings.kt`
4. **Aggiunto `.gitignore` di progetto**
    - Ignora artefatti build/caches, file locali IDE e `local.properties`
5. **Aggiunta icona launcher adaptive (Material/Android 13 ready)**
    - Creati file risorsa: `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `res/drawable/ic_launcher_background.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/drawable/ic_launcher_monochrome.xml`
    - Aggiornato `AndroidManifest.xml` con `android:icon="@mipmap/ic_launcher"` e `android:roundIcon="@mipmap/ic_launcher_round"`
    - Verifica risorse completata con `./gradlew :app:processDebugResources` (**OK**)

## Problemi noti / TODO prioritari
1. **`LaunchedEffect` salva ad ogni recomposition** — andrebbe sostituito con `snapshotFlow` + `debounce` per evitare scritture eccessive su DataStore
2. **`refreshConnectedDevices` usa reflection** (`device.isConnected()`) — funziona ma è fragile; valutare `BluetoothProfile.getConnectedDevices()` con `ServiceListener`
3. **Permesso `BLUETOOTH` nel Manifest** dichiarato per API < 31, ma manca `maxSdkVersion="30"` sull'attributo
4. **Nessun feedback visivo** se `setParameters` viene ignorato dal device — utile aggiungere un Toast/Snackbar diagnostico al primo utilizzo
5. **`BootReceiver` vuoto** — se si vuole riapplicare i settings dopo un reboot con device già connesso, serve logica aggiuntiva qui

## Come buildare senza Android Studio
```bash
chmod +x setup.sh && ./setup.sh   # scarica gradle-wrapper.jar, rileva SDK
./gradlew assembleDebug            # APK → app/build/outputs/apk/debug/
./gradlew installDebug             # installa via ADB
```
