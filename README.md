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

## Limiti noti

- L'amplificazione (`LoudnessEnhancer`) e il ripiego DynamicsProcessing agiscono sulla sessione audio globale 0, deprecata dalla documentazione Android: su alcune versioni o telefoni possono non sentirsi. L'unica via verificata dall'autore è `master_balance` in `Settings.System`, su un Redmi Note 9 Pro (MIUI/HyperOS).
- `master_balance` vale per tutte le uscite audio, non solo per il Bluetooth. Se il processo viene ucciso mentre un profilo è applicato, il valore resta finché un nuovo profilo non lo sovrascrive.
- Il ripristino dopo la morte del processo non copre LE Audio e non conosce l'ordine di connessione originale.

## Build e test

Servono JDK 17 e l'Android SDK. Su macOS `./setup.sh` cerca l'SDK e scrive `local.properties`; altrove basta aprire il progetto in Android Studio.

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installa sul dispositivo connesso via ADB
./gradlew testDebugUnitTest    # test unitari JVM, senza dispositivo
```

I test coprono il salvataggio per dispositivo (`DeviceSettingsRepositoryTest`) e la regola "vince l'ultimo connesso" (`ConnectedDevicesTest`).

## Sviluppo

Architettura e scelte di progetto: [`BtAudioBalancer_handoff.md`](BtAudioBalancer_handoff.md). Attività aperte: [`TODO.md`](TODO.md).
