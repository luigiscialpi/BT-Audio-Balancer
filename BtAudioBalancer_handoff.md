# Handoff — BtAudioBalancer

## Contesto
App Android in Kotlin + Jetpack Compose che bilancia L/R e amplifica l'audio di sistema per ogni dispositivo Bluetooth, ricordando le impostazioni per MAC address.

## Stack
- Kotlin + Jetpack Compose Material3
- DataStore Preferences (persistenza)
- JUnit 4 per i test unitari JVM (`app/src/test`)
- `minSdk 26`, `targetSdk 35`, AGP 8.5.2, Kotlin 2.0.21

## Architettura
```
BtAudioBalancer/app/src/main/java/com/example/btaudiobalancer/
13: BtAudioBalancer/app/src/main/java/com/example/btaudiobalancer/
14: ├── BtBalancerApp.kt                  ← Application class
15: ├── MainActivity.kt                   ← entry Compose, rimossi permessi bloccanti all'avvio
16: ├── audio/
17: │   └── AudioEffectApplier.kt         ← CORE: balance tripla strategia (System/DSP/setParameters) + LoudnessEnhancer
├── bluetooth/
│   └── BluetoothAudio.kt             ← isAudio(): esclude i device non audio (orologi, mouse...)
18: ├── data/
19: │   ├── DeviceAudioSettings.kt        ← data class: mac, name, balance(-1..1), boostMb(0..1500), enabled
20: │   └── DeviceSettingsRepository.kt   ← DataStore (iniettabile nel costruttore): chiavi prefissate per MAC, allDevices Flow; coperto da test
21: ├── receiver/
22: │   ├── BluetoothReceiver.kt          ← ACL_CONNECTED/DISCONNECTED → passa MAC e avvia AudioEffectService
23: │   └── BootReceiver.kt               ← placeholder per BOOT_COMPLETED
24: ├── service/
25: │   ├── AudioEffectService.kt         ← ForegroundService: coda di comandi APPLY/UPDATE/RESET, applica il profilo dell'ultimo device connesso
│   └── ConnectedDevices.kt           ← elenco dei device connessi in ordine (vale l'ultimo); coperto da test
26: └── ui/
27:     ├── MainViewModel.kt              ← refreshConnectedDevices via reflection isConnected(), delega al Service
28:     ├── MainScreen.kt                 ← LazyColumn: sezione connessi + sezione profili salvati, visualizer L/R
29:     └── theme/                        ← Color, Type, Theme (dynamic color Android 12+)
```

## Meccanismi audio

| Funzione | Implementazione | Note |
|---|---|---|
| Bilanciamento L/R | **Tripla Strategia con Fallback**: <br> 1. `Settings.System.putFloat(..., "master_balance", value)` <br> 2. `DynamicsProcessing` (DSP sessione 0) <br> 3. `AudioManager.setParameters("master_balance=value")` | **Strategia 1**: Richiede il permesso `WRITE_SECURE_SETTINGS` concesso via ADB. È l'unica via affidabile e testata su MIUI/HyperOS (Redmi Note 9 Pro) per spostare l'audio di sistema.<br>**Strategia 2**: Fallback DSP in user-space per dispositivi stock Android 9+ (API 28+). |
| Amplificazione | `LoudnessEnhancer(audioSessionId = 0)` + `setTargetGain(boostMb)` | Sessione globale → tutto l'audio di sistema. Range 0..1500 mB (0..+15 dB). Funziona su tutti i device senza root. |

## Comportamento chiave
- **Isolamento dei profili (Anti Cross-Device Leak)**: Il `AudioEffectService` tiene l'elenco (`connected`) dei device audio connessi che hanno un profilo salvato; gli effetti sono globali, quindi vale quello dell'ultimo connesso. Update dalla UI per un altro device vengono solo salvati. Alla disconnessione dell'attivo si riapplica il profilo del precedente; se non ne resta nessuno si azzera e il service si ferma. Un device senza profilo viene ignorato. I comandi (APPLY/RESET/UPDATE) passano da una coda e girano uno alla volta. `BluetoothReceiver` e la lista "connessi" della UI escludono i device non audio (`isAudio()`). Se il sistema uccide il processo e riavvia il service (intent nullo), il service rilegge i device A2DP connessi (`getProfileProxy`) e riapplica i loro profili; l'ordine di connessione originale non si conosce.
- **Service Unico**: Il `MainViewModel` non istanzia un proprio `AudioEffectApplier` per evitare sovrascritture concorrenti; delega tutte le modifiche in tempo reale al `AudioEffectService` via `ACTION_UPDATE` Intent.
- **UI & Performance**: Gli slider salvano e applicano solo al rilascio (`onValueChangeFinished`), mai alla comparsa della card, quindi una scrittura DataStore per gesto e nessun salvataggio spontaneo all'apertura dell'app. Un profilo di un device non connesso viene salvato ma non applicato.

## Stato build
- `./gradlew assembleDebug` **OK** (build verde, pronto per installazione locale)
- Warning non bloccante: AGP `8.5.2` con `compileSdk 35` (AGP testato fino a 34)

## Modifiche recenti (Giugno 2026)
1. **Risolto leak cross-device**: Il ViewModel delega al service tramite `ACTION_UPDATE` per usare un'unica istanza di applier.
2. **Risolto reset prematuro**: Il `BluetoothReceiver` passa l'indirizzo MAC a `ACTION_RESET`. Il Service spegne gli effetti solo se il device che si disconnette è quello attualmente attivo.
3. **Ottimizzazione scritture DataStore**: gli slider in `MainScreen.kt` salvano solo al rilascio (`onValueChangeFinished`).
4. **Implementato bilanciamento multilivello**: Creato l'applier a tripla strategia in `AudioEffectApplier.kt` che tenta per primo `Settings.System` (sbloccato da `WRITE_SECURE_SETTINGS` concesso via ADB) e in alternativa `DynamicsProcessing` a livello DSP o `setParameters` legacy.
5. **Rimozione della frizione permessi all'avvio**: Eliminata la chiamata invasiva a `ensureWriteSettingsPermission` in `MainActivity.kt` per far avviare l'app in modo pulito e immediato.
6. **Aggiunta del tag `maxSdkVersion="30"`** sul permesso legacy `BLUETOOTH` in `AndroidManifest.xml` (Risolto TODO n.3).

## Problemi noti / TODO prioritari
1. **`refreshConnectedDevices` usa reflection** (`device.isConnected()`) — funziona ma è fragile; valutare `BluetoothProfile.getConnectedDevices()` con `ServiceListener`.
2. **`BootReceiver` vuoto** — non serve: dopo un reboot nessun device è connesso e `ACL_CONNECTED` copre la riconnessione (vedi M6 in `TODO.md`).

## Come buildare e testare
```bash
# Rileva SDK e configura gradle wrapper
chmod +x setup.sh && ./setup.sh

# Compila l'APK di debug
./gradlew assembleDebug

# Test unitari (JVM, senza dispositivo): ConnectedDevicesTest, DeviceSettingsRepositoryTest
./gradlew testDebugUnitTest

# Installa l'app via ADB (quando il dispositivo è connesso)
./gradlew installDebug

# Concedi il permesso necessario per scrivere in Settings.System
adb shell pm grant com.example.btaudiobalancer android.permission.WRITE_SECURE_SETTINGS
```
