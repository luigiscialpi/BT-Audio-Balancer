# TODO

Si spunta `[x]` nella stessa patch che chiude l'attività. Ogni voce ha un ID da citare nel commit (es. `fix(B1): ...`).

## Bug

- [x] **B1** Salvataggio e applicazione automatici all'apertura dell'app (`DeviceCard`, `MainViewModel.updateAndApply`): può sovrascrivere il profilo con i valori di default, applica il profilo di un device non connesso, crea profili per device non audio.
- [x] **B2** Più device Bluetooth: effetti persi o sostituiti. Un device senza profilo diventa "attivo" senza toccare gli effetti; alla disconnessione dell'attivo non si riapplica il profilo del device rimasto; il reset scrive `master_balance=0` anche se il device non era attivo; `BluetoothReceiver` reagisce anche ai device non audio.
- [ ] **B3** Effetti persi dopo l'uccisione del processo: il restart `START_STICKY` arriva con intent nullo e il service non fa nulla, quindi gli effetti restano spenti fino alla riconnessione. Da rileggere dal sistema i device A2DP già connessi (`getProfileProxy`). La race tra i comandi è chiusa con B2 (coda in `AudioEffectService`).
- [x] **B4** L'interruttore "abilitato" non azzera gli effetti già applicati (`AudioEffectApplier.apply`, uscita anticipata).
- [ ] **B5** Balance, da verificare su device: ordine delle strategie diverso dall'handoff (DynamicsProcessing prima di Settings), `Settings.System.canWrite` non copre `WRITE_SECURE_SETTINGS`, effetti sulla sessione 0 deprecati dalla documentazione Android.

## Minori

- [ ] **M1** `LoudnessEnhancer` rilasciato e ricreato a ogni modifica: aggiornare `setTargetGain` sull'istanza esistente.
- [x] **M2** `ACTION_RESET` non chiama `startForeground` ma il receiver usa `startForegroundService`: rischio `ForegroundServiceDidNotStartInTimeException`.
- [ ] **M3** Senza `BLUETOOTH_CONNECT` (Android 14+) `startForeground` può lanciare `SecurityException` dal percorso UI.
- [ ] **M4** Eliminare il profilo del device attivo non azzera gli effetti.
- [ ] **M5** Lista "connessi": `isConnected` via reflection su API nascosta (se fallisce risultano connessi tutti i device accoppiati) e aggiornamento solo in `onResume` o a mano. (I device non audio sono già esclusi.)
- [ ] **M6** `BootReceiver` non fa nulla e non serve: dopo un riavvio nessun device è connesso e `ACL_CONNECTED` copre la riconnessione. Eliminarlo insieme a `RECEIVE_BOOT_COMPLETED`.

## UX/UI

- [ ] **U1** Target di tocco sotto i 48 dp (switch forzato a 40×24 dp, cestino a 36 dp) ed eliminazione del profilo senza conferma né annulla.
- [ ] **U2** Il pulsante "Aggiorna" usa l'icona Bluetooth invece di una di aggiornamento. Meglio aggiornare la lista in automatico agli eventi di connessione (vedi M5).
- [ ] **U3** Sotto gli slider compaiono testi tecnici ("DynamicsProcessing, sessione globale...") che dipendono dalla strategia realmente usata; `BalanceResult.NOT_SUPPORTED` viene calcolato ma non arriva mai alla UI.
- [ ] **U4** Dopo B1 l'effetto del balance si sente solo al rilascio dello slider (prima, dopo 300 ms di pausa durante il trascinamento). Valutare un'anteprima dal vivo che applichi senza salvare.

## Documentazione

- [ ] **R1** Scrivere `README.md` dopo B5, così non documenta permessi e strategie ancora incerti. Poi ridurre `BtAudioBalancer_handoff.md` alle sole note di sviluppo, per non avere due copie delle stesse istruzioni.

## Test

- [x] **T1** JUnit 4 introdotto (`./gradlew testDebugUnitTest`). Primo test: `ConnectedDevicesTest`, sulla regola "vince l'ultimo connesso, alla disconnessione torna il precedente", estratta da `AudioEffectService` in `ConnectedDevices`.
- [x] **T2** `DeviceSettingsRepository` riceve il `DataStore` nel costruttore (da `Context` resta un costruttore secondario, i chiamanti non cambiano). `DeviceSettingsRepositoryTest` copre il salvataggio per-device (chiavi `<mac>_*`): rilettura dello stesso device, device sconosciuto, impostazioni separate per device, aggiornamento senza duplicati, eliminazione.
