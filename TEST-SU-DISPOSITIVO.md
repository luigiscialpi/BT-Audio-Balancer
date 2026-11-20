# Test su dispositivo

Checklist per provare l'app su un telefono vero, in debug USB o wireless. Va eseguita insieme a una chat AI che ha `adb` sul dispositivo: l'AI lancia i comandi e legge i risultati, tu fai ciò che richiede una persona (accendere e spegnere le cuffie, ascoltare) e confermi l'esito.

Perché serve: i test unitari (`./gradlew testDebugUnitTest`) e le simulazioni fatte durante lo sviluppo non toccano mai il vero Bluetooth, il vero audio di sistema e la vera interfaccia. Quello che segue è l'unico modo di sapere se le correzioni di `TODO.md` funzionano davvero.

Legenda: **[AI]** lo esegue la chat con `adb`; **[TU]** lo fai tu sul telefono; **Esito** da annotare per ogni test: OK, KO oppure NON ESEGUITO, con l'estratto di log che lo prova.

## Preparazione

1. **[TU]** Telefono con debug USB attivo (o debug wireless). Su MIUI/HyperOS attiva anche "Debug USB (impostazioni di sicurezza)": senza, `pm grant` e `input` non funzionano. L'ottimizzazione della batteria conta per D10 (che la imposta da sé) e per D13: lì tienila esclusa e abilita l'avvio automatico, altrimenti il sistema può impedire il riavvio del processo.
2. **[TU]** Cuffie o altoparlante Bluetooth A2DP già accoppiato, con musica pronta da riprodurre. Per il gruppo D serve un secondo dispositivo audio Bluetooth. Per D25 (facoltativo) uno con LE Audio, su Android 13 o successivo.
3. **[AI]** Dal PC, dalla cartella del progetto:

   ```bash
   export PKG=com.example.btaudiobalancer
   adb devices                        # il telefono deve comparire come "device"
   ./gradlew installDebug             # la build debug è debuggable: serve a run-as
   adb shell getprop ro.build.version.release   # annota: versione Android
   adb shell getprop ro.product.model           # annota: modello
   ```

4. **[AI]** Definizione dei profili usati nei test. **Profilo A**: bilanciamento -0.5 (verso sinistra) e amplificazione +3 dB. **Profilo B**: bilanciamento +0.5 e amplificazione +6 dB.

## Stato della sessione del 20 novembre 2026

Prima sospensione dei test:

- Dispositivo: Redmi Note 9 Pro, modello `Redmi_Note_9_Pro`
- Android: 12, API 31
- MIUI/HyperOS: `V140`
- Debug wireless: `adb-d511899c-0LpFp7._adb-tls-connect._tcp`
- Dispositivo Bluetooth: Redmi Buds 6 Active, `90:EF:4A:AB:8B:69`
- Test JVM: `./gradlew testDebugUnitTest` riuscito

| Test | Esito | Evidenza/annotazione |
|---|---|---|
| D01 | OK | Avvio pulito dopo `pm clear`; permesso Bluetooth concesso dall'utente |
| D02 | OK | `WRITE_SECURE_SETTINGS: granted=true`; avviso iniziale sparito |
| D03 | OK | Card connessa, profilo assente e nessun cambio del valore iniziale |
| D04 | OK | Profilo A applicato a circa `-0.478`, `300mB`; il telefono usa `APPLIED_VIA_DYNAMICS_PROCESSING` |
| D05 | OK | Trascinamento con 28 righe `Applied:`, nessun match `enqueue rate`; conferma auditiva dell'utente |
| D06 | KO | A `600mB`/`300mB`/`0mB` il volume resta invariato e l'audio viene percepito distorto; a 0 restano 0 effetti `LoudnessEnhancer` in `audio_flinger` |
| D07 | OK | `master_balance=0.4` resta dopo Centro e riconnessione delle cuffie |
| D08 | OK | OFF logga `disabled, resetting` e `Reset to neutral`; ON riapplica il profilo |
| D09-D25 | DA RIPRENDERE | Sessione sospesa prima della disconnessione D09 |

Per riprendere, partire da D09 mantenendo il Profilo A salvato. Sul Redmi questo test richiede un'attenzione particolare: il percorso `Settings.System.putFloat("master_balance")` non viene scritto e il bilanciamento usa il fallback `DynamicsProcessing`; l'effetto LoudnessEnhancer non ha prodotto un aumento percettibile del volume.

### Comandi utili

| Cosa | Comando |
|---|---|
| Bilanciamento di sistema (`null` = mai impostato) | `adb shell settings get system master_balance` |
| Impostarlo / rimuoverlo a mano | `adb shell settings put system master_balance 0.4` / `adb shell settings delete system master_balance` |
| Log dell'app | `adb logcat -c` poi `adb logcat -v time -s AudioEffectApplier:V AudioEffectService:V MainViewModel:V AndroidRuntime:E` |
| Crash | `adb logcat -b crash -d` |
| Service in primo piano? | `adb shell dumpsys activity services $PKG` (cerca `isForeground=true`; se non c'è nulla, il service non gira) |
| Notifica | `adb shell dumpsys notification --noredact \| grep -A12 "pkg=$PKG"` |
| PID del processo | `adb shell pidof $PKG` |
| Permessi | `adb shell dumpsys package $PKG \| grep -E "WRITE_SECURE_SETTINGS\|BLUETOOTH_CONNECT"` (guarda `granted=`) |
| Dare / togliere un permesso | `adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS` (idem `BLUETOOTH_CONNECT`); `pm revoke` per toglierlo |
| Profili salvati (DataStore) | `adb exec-out run-as $PKG cat files/datastore/device_settings.preferences_pb \| strings` (chiavi `<mac>_name`, `_balance`, `_boost`, `_enabled`) |
| Esclusione dalla batteria | `adb shell cmd deviceidle whitelist +$PKG` (esclude) / `-$PKG` (rimette l'ottimizzazione); controllo: `adb shell dumpsys deviceidle whitelist \| grep $PKG` (se il comando non va, da Impostazioni) |
| Cancellare i dati dell'app | `adb shell pm clear $PKG` |
| Aprire l'app | `adb shell am start -n $PKG/.MainActivity` |
| Screenshot | `adb exec-out screencap -p > shot.png` |
| Albero della UI (testi, coordinate) | `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` |
| Trascinare uno slider | `adb shell input swipe X1 Y1 X2 Y2 DURATA_MS` (le coordinate vengono dal dump o dallo screenshot) |
| Effetti audio attivi | `adb shell dumpsys media.audio_flinger` (cerca `LoudnessEnhancer`; il formato cambia da versione a versione) |
| Schermo 320 / 360 / 411 dp | `adb shell wm size 640x1280` / `720x1440` / `822x1700`, sempre con `adb shell wm density 320`; ripristino: `wm size reset` e `wm density reset` |
| Testo grande | `adb shell settings put system font_scale 1.3` (poi `1.5`, e `1.0` per ripristinare) |
| Bluetooth spento / acceso | `adb shell svc bluetooth disable` / `enable` (se il comando non c'è, da Impostazioni) |
| Uccidere il processo | `PID=$(adb shell pidof $PKG \| tr -d '\r'); adb shell run-as $PKG kill -9 $PID` |

Limiti da conoscere: il service non è esportato e `ACL_CONNECTED` è un broadcast protetto, quindi dal PC non si possono simulare le connessioni. Per connettere e disconnettere servono le cuffie vere, oppure `svc bluetooth`.

## Gruppo A: un dispositivo

**D01 Primo avvio e permesso (U3).** [AI] `pm clear`, avvia l'app. [TU] Concedi il permesso Bluetooth quando lo chiede.
Atteso: nessun crash; in cima compare l'avviso "Per bilanciare l'audio di sistema serve un permesso…"; "Profili salvati" è vuoto.

**D02 Permesso per il bilanciamento (B5, U3).** [AI] `pm grant … WRITE_SECURE_SETTINGS`, verifica `granted=true`, riapri l'app.
Atteso: l'avviso è sparito. Da qui in poi il permesso resta concesso, salvo D01 ripetuto.

**D03 Connessione senza profilo (B1, M5, U1).** [AI] Annota `master_balance`. [TU] Accendi le cuffie con l'app aperta, senza premere Aggiorna.
Atteso: entro pochi secondi la card compare in "Dispositivi connessi", con "Connesso · <MAC>" sotto il nome; "Profili salvati" resta vuoto; `master_balance` invariato; nel log "no saved profile, ignoring".

**D04 Creare il profilo A (B5).** [AI] Trascina il bilanciamento a circa -0.5 e rilascia, poi porta l'amplificazione a +3 dB. [TU] Con la musica in riproduzione.
Atteso: `master_balance` ≈ -0.5; log `Applied: device=… balance=-0.5 boost=300mB balanceResult=APPLIED_VIA_SETTINGS`; il profilo compare in "Profili salvati" e nel DataStore. [TU] Si sente più forte a sinistra. Se `balanceResult` è diverso o non si sente nulla, annotalo: è il rischio principale del progetto.

**D05 Anteprima mentre trascini (U4).** [AI] `logcat -c`, poi un trascinamento lento del bilanciamento (`input swipe` da 3000 ms) e, in parallelo, `settings get system master_balance` ogni 300 ms; a metà gesto leggi anche il DataStore. [TU] Ascolta mentre il gesto avviene.
Atteso: `master_balance` assume valori diversi prima della fine del gesto; a metà gesto il DataStore ha ancora il valore vecchio, dopo il rilascio quello nuovo; le righe `Applied:` sono al massimo circa 35 (non centinaia); `adb logcat -d | grep -i "enqueue rate"` non trova nulla; la notifica resta una sola. [TU] Si sente il cambio mentre trascini.

**D06 Amplificazione (M1).** [AI] Porta l'amplificazione a +6 dB, poi a +3, poi a 0. [TU] Il volume sale e scende.
Atteso: log `LoudnessEnhancer set to 600mB`, poi `300mB`; con `dumpsys media.audio_flinger` il numero di effetti `LoudnessEnhancer` non cresce a ogni cambio e a 0 l'effetto sparisce. Se non si sente nulla: annotalo, è il limite della sessione audio 0 descritto nel README.

**D07 Centra e rispetto del valore dell'utente (B5).** [AI] Premi "Centra" (il profilo A resta con amplificazione +3 dB): `master_balance` deve tornare a 0. Poi `settings put system master_balance 0.4`. [TU] Spegni e riaccendi le cuffie.
Atteso: `master_balance` resta 0.4: l'app non sovrascrive un valore che non ha impostato lei.

**D08 Interruttore del profilo (B4).** [AI] Spegni l'interruttore del dispositivo connesso, poi riaccendilo. [TU] Ascolta.
Atteso: da spento `master_balance` torna neutro, nel log "disabled, resetting" o "Reset to neutral", il suono torna normale e la card dice "Profilo disabilitato -- non verra applicato alla connessione"; da acceso il profilo si riapplica.

**D09 Disconnessione (B2).** [TU] Spegni le cuffie.
Atteso: la card sparisce da "Dispositivi connessi" entro pochi secondi; `master_balance` torna neutro; il service si ferma (`dumpsys activity services` non lo mostra) e la notifica sparisce.

**D10 Riconnessione senza aprire l'app (B1, M6, B6).** [AI] Manda l'app in secondo piano con `input keyevent KEYCODE_HOME`: niente `force-stop`, perché un'app fermata così non riceve i broadcast del manifest finché non la riapri a mano. Su Android 12 o successivo fai il test due volte: prima **con** l'esclusione dalla batteria (`cmd deviceidle whitelist +$PKG`), poi **senza** (`-$PKG`). [TU] Spegni e riaccendi le cuffie.
Atteso con l'esclusione (e su Android 11 o precedente, sempre): compare la notifica "Connesso: <nome>", il log mostra `Applied:` e `master_balance` è quello del profilo. [TU] Si sente.
Atteso su Android 12+ senza esclusione (limite noto B6): nessun crash (`logcat -b crash -d` vuoto per il pacchetto), nel log "service non avviabile dal background", profilo non applicato e, allo spegnimento delle cuffie, effetti non azzerati. Se invece si applica comunque, annotalo: sul tuo telefono il limite non c'è.

**D11 Aprire l'app non cambia nulla (B1).** [AI] Con le cuffie connesse e il profilo A salvato, apri e chiudi l'app 3 volte, anche dopo `force-stop`.
Atteso: DataStore identico a prima e nessuna riga `Applied:` causata dall'apertura.

**D12 Riavvio del telefono (M6).** [AI] `adb reboot`, aspetta l'avvio e riconnetti `adb`.
Atteso: il profilo A è ancora salvato; non gira nessun service finché non si connettono le cuffie; [TU] riconnettendole il profilo si applica; nessun crash.

## Gruppo B: robustezza

**D13 Morte del processo (B3).** [TU] Cuffie connesse, profilo A applicato. [AI] Uccidi il processo con `kill -9`, poi controlla ogni secondo per 15 secondi.
Atteso: nuovo PID, service di nuovo in primo piano, notifica presente, nel log `Applied:` e `master_balance` coerente con il profilo. KO se restano tutti gli effetti azzerati o il service non riparte (prima controlla le restrizioni batteria del punto 1).

**D14 Permesso Bluetooth revocato mentre il service gira (M3).** [TU] Cuffie connesse. [AI] `pm revoke … BLUETOOTH_CONNECT`, aspetta 15 secondi, controlla `logcat -b crash -d`, poi `pm grant`.
Atteso: nessun crash e nessun dialogo "l'app si è fermata"; eventualmente nel log "startForeground refused". Annota la versione di Android: il caso interessante è la 14 o successiva. Se compare `ForegroundServiceDidNotStartInTimeException` è il rischio noto di M3: riportalo con il log.

**D15 Bluetooth spento e acceso (M5).** [AI] Con l'app aperta e le cuffie connesse: `svc bluetooth disable`, poi `enable`.
Atteso: a Bluetooth spento la lista dei connessi si svuota, `master_balance` torna neutro e il service si ferma; a Bluetooth riacceso e cuffie riconnesse la card ricompare da sola.

**D16 Eliminare il profilo (M4, U1).** [AI] Con le cuffie connesse e il profilo A attivo, tocca il cestino. Prima "Annulla", poi di nuovo il cestino e "Elimina".
Atteso: "Annulla" non cambia nulla; "Elimina" fa sparire il profilo da "Profili salvati", azzera gli effetti (`master_balance` neutro), ferma il service se non ci sono altri dispositivi; la card connessa resta, con gli slider ai valori di default (bilanciamento al centro, amplificazione a 0) e il nome invariato.

**D17 Stabilità e perdite (M1, M5).** [TU] Connetti e disconnetti le cuffie 10 volte, muovendo ogni tanto gli slider. [AI] Alla fine confronta con l'inizio: `dumpsys media.audio_flinger` (numero di effetti), `dumpsys meminfo $PKG`, `logcat -b crash -d`.
Atteso: nessun crash, nessun effetto in più rimasto aperto, memoria stabile.

## Gruppo C: interfaccia

**D18 Schermi stretti (U1).** [TU] Rinomina il dispositivo Bluetooth con un nome lungo (per esempio "Cuffie Bluetooth di Luigi Super Lunghe"). [AI] Per 320, 360 e 411 dp (comandi `wm` in tabella), con il dispositivo connesso: screenshot e `uiautomator dump`.
Atteso: il nome si legge intero, su 2 righe se serve, e si accorcia per primo il MAC, non il nome; "Connesso" è visibile; interruttore e cestino non si sovrappongono e hanno un'area di tocco di almeno 48 dp (96 px a 320 dpi nei `bounds` del dump). Ripeti a 320 dp con testo grande (`font_scale` 1.3 e 1.5): niente testi tagliati o sovrapposti. Tra le card ci sono 16 dp, come i margini laterali. Ripristina `wm` e `font_scale`.

**D19 Testi (U3, M5).** [AI] Screenshot di: lista vuota, card con sliders, avviso del permesso (dopo `pm revoke … WRITE_SECURE_SETTINGS` e riapertura).
Atteso: lista vuota con "Nessun dispositivo Bluetooth connesso. Collega un auricolare: comparirà qui."; nessun testo tecnico sotto gli slider; l'avviso compare solo senza permesso. Poi ridai il permesso.

**D20 Accessibilità (non coperta).** [TU] Con TalkBack acceso, passa sull'interruttore della card.
Atteso: è un limite noto che l'interruttore non abbia un'etichetta ("interruttore" e basta). Annota se è un problema per te: si risolve con una `contentDescription`.

## Gruppo D: due dispositivi (facoltativo)

Prerequisito: A e B accoppiati, con il profilo A già creato (D04) e il profilo B creato collegandolo da solo.

**D21 Vale l'ultimo connesso (B2).** [TU] Connetti A, poi B. Atteso: `master_balance` e boost sono quelli del profilo B. Disconnetti B: tornano quelli di A. Disconnetti A: tutto neutro e service fermo.

**D22 Un dispositivo nuovo non scavalca l'attivo (M7).** [TU] Connetti A (attivo), poi B senza profilo. [AI] Crea il profilo di B dall'app. Atteso: gli effetti restano quelli di A; disconnettendo A subentra B.

**D23 Eliminare l'attivo con un altro connesso (M4).** [AI] Con A attivo e B connesso con profilo, elimina il profilo di A. Atteso: si applica quello di B.

**D24 Disconnettere il secondario (B2).** [TU] Con A e B connessi, disconnetti quello che non è attivo. Atteso: gli effetti e la notifica non cambiano.

## Gruppo E: LE Audio (facoltativo)

**D25 Dispositivo LE Audio.** [TU] Su Android 13 o successivo, connetti un dispositivo LE Audio che ha un profilo salvato.
Atteso (limite noto, vedi README e voce M8 di `TODO.md`): alla connessione il profilo viene applicato (log `Applied:`), ma il dispositivo non compare tra i connessi dell'app. Annota modello e versione di Android. Se invece compare, o se il profilo non si applica, riportalo: cambia la decisione su M8.

## Da verificare a orecchio

Nessun comando può dire se l'audio è davvero cambiato: [TU] conferma con musica in riproduzione D04, D05, D06 e D08. Il rischio maggiore è che `LoudnessEnhancer` e il ripiego `DynamicsProcessing` sulla sessione audio 0 (deprecata) non abbiano effetto su alcuni telefoni.

## Come riportare gli esiti

Per ogni test: ID, esito (OK, KO, NON ESEGUITO) e, per i KO, queste tre cose:

1. le ultime 300 righe di `adb logcat -d -v time`;
2. l'output di `dumpsys activity services $PKG`;
3. uno screenshot, se il problema è nell'interfaccia.

Intestazione da compilare una volta: modello, versione di Android, versione di MIUI/HyperOS, dispositivi Bluetooth usati, data.

Ogni KO diventa una voce nuova in `TODO.md` con un ID (`B6`, `B7`…), come per quelle già chiuse.
