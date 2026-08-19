# Struttura dell'app PosMock

> Mappa dell'architettura per riprendere il contesto senza rileggere il codice.
> Ultimo aggiornamento: 15/08/2026 (v1.0)

## Cos'e'

Simulatore di terminale POS. Apre un `ServerSocket` sulla porta scelta e serve le connessioni
del middleware (UniquePosManager / PosManagerKtorDe) come farebbe un terminale vero. Due
protocolli **entrambi simulati**, ZVT e IAE37 (vedi "I tre protocolli"); la modalita' `raw`
registra soltanto, ed e' lo strumento con cui IAE37 e' stato ricostruito.

## Configurazione

- **Mono-modulo**: solo `:app`
- AGP 9.3.1, Gradle 9.5, Kotlin 2.2.10, Compose BOM 2025.12.00, compile/target SDK 37,
  **minSdk 26**, JVM 11
- `applicationId = it.primesoftware.posmock`
- Librerie: Koin 4.1.1 (DI), coroutines, Compose Material3 + adaptive navigation suite,
  SharedPreferences. **Niente Room, niente Moshi, niente kotlinx-serialization**: non c'e'
  niente da persistere oltre a una manciata di preferenze.
- Il compilatore Kotlin e' quello **integrato in AGP 9**: nei plugin non c'e'
  `org.jetbrains.kotlin.android`. Aggiungere un plugin del compilatore Kotlin (serialization,
  parcelize…) non e' un'operazione neutra — verificare prima che conviva con la modalita'
  integrata.

## Architettura: MVVM + Clean, package root `it.primesoftware.posmock`

```
data/
├── hardware/AndroidBatteryOptimizationManager   esenzione da Doze/OEM (come Ermes)
├── local/
│   ├── PosMockPreferences        SharedPreferences (protocollo, porta, esito, ritardo…)
│   ├── ConfigStore               StateFlow della configurazione + persistenza
│   └── logging/                  LogFiles (convenzione nomi), LogRepositoryImpl (memoria +
│                                 file, scrittura async), PublicLogExporter (copia in Download)
├── network/NetworkInfoProvider   IP locale da mostrare (quello da mettere in posList.cfg)
└── server/
    ├── MockServerController      accept loop, wake/wifi lock, avvio del foreground service
    ├── OutcomeProviderImpl       preset oppure attesa della scelta manuale
    └── protocol/
        ├── ProtocolHandler       interfaccia: serve una connessione dall'inizio alla fine
        ├── zvt/   ZvtApdu + ZvtCodec (framing), ZvtMessages (pacchetti), ZvtTerminalHandler
        ├── iae37/ Iae37Codec (framing + LRC), Iae37Messages, Iae37TerminalHandler
        └── raw/   RawProtocolHandler   registrazione esadecimale + risposta configurabile

domain/
├── model/       MockProtocol, ServerConfig, ServerState, MockOutcome, ZvtErrorId,
│                PaymentRequest/PendingDecision, LogEntry
├── hardware/    BatteryOptimizationManager
└── repository/  IServerController, IConfigStore, ILogRepository, IOutcomeProvider,
                 IPreferencesRepository, INetworkInfoProvider

di/AppModule.kt  moduli Koin (logger, local, hardware, server, viewModel) — tutti `single`

presentation/
├── activity/    MainActivity (permesso notifiche), MainViewModel (richiesta in attesa),
│                PosMockApp (NavigationSuiteScaffold + dialog)
├── navigation/  AppDestination (Stato, Configura, Traffico)
├── screen/      status/, config/, monitor/ — ognuna con {view, viewModel}
├── components/  OutcomeDialog, SharedBlocks (SectionCard, InfoRow, OptionRow)
└── theme/       PosMockTheme

service/MockServerService   foreground service `specialUse`
```

## Le tre decisioni che spiegano il resto

**Il server non vive nel service.** Vive nel `MockServerController`, singleton Koin di
processo; il service serve solo a impedire che Android uccida il processo con l'app in
background — cosa che chiuderebbe la socket a meta' transazione e manderebbe a monte proprio
la prova in corso. Cosi' la UI osserva gli StateFlow del controller direttamente, senza
binder ne' broadcast. Il tipo e' `specialUse` e non `dataSync` perche' su Android 15 un FGS
`dataSync` viene fermato dopo 6 ore complessive, e un banco puo' restare acceso un turno intero.

**La configurazione sta per conto suo** (`IConfigStore`), non dentro il controller: la vogliono
sia il controller (su che porta ascoltare) sia chi decide gli esiti (quale preset applicare).
Con la config dentro il controller quei due si dipenderebbero a vicenda e il grafo Koin
sarebbe circolare.

**L'attesa della decisione manuale e' un `CompletableDeferred`**, non un blocco: la coroutine
che serve la connessione si sospende e resta cancellabile, cosi' lo stop del server non lascia
connessioni appese a una decisione che non arrivera' mai. Un `Mutex` serializza le richieste —
un terminale fisico serve una transazione per volta, e due dialog sovrapposti non si saprebbe
nemmeno a quale pagamento si riferiscono.

## Perche' il mock non deve sparire (le quattro difese)

Un banco di prova che smette di rispondere produce **gli stessi sintomi del guasto che si sta
cercando**: POS occupato, esito che non arriva, tavolo che resta aperto. Si finisce a indagare
il middleware mentre il colpevole e' il telefono in Doze. Da qui quattro protezioni, le stesse
di Ermes:

1. **Foreground service** `specialUse` — il processo non viene ucciso con l'app in background.
2. **Wake lock** parziale, tenuto per tutta la durata del server (`MockServerController`).
3. **Wifi lock** `FULL_LOW_LATENCY` (o `FULL_HIGH_PERF` sotto API 29): senza, a schermo spento
   il Wi-Fi va in risparmio energetico.
4. **`FLAG_KEEP_SCREEN_ON` + esenzione dalle ottimizzazioni di batteria** (`MainActivity`):
   l'activity resta RESUMED e Samsung One UI non sospende l'app. Il dialog di esenzione
   ricompare a **ogni avvio** finche' l'utente non concede — la sorgente di verita' e' lo stato
   del sistema (`isExempt()`), non una preferenza nostra.

L'ordine dei dialog all'avvio conta: prima le notifiche, poi la batteria, il secondo lanciato
dal callback del primo. Insieme si coprirebbero a vicenda.

Le quattro difese tengono in piedi il processo, ma non impediscono al server di morire per conto
suo: quel caso e' in "Quando il server muore da solo", piu' sotto.

## Le tre schermate

**Stato** e' quella di comando: stato del server a caratteri grandi, indirizzo da ribattere in
`posList.cfg`, connessioni attive, pulsante avvia/ferma. **Configura** contiene solo le scelte.
**Traffico** il log.

Lo split e' arrivato quando la vecchia schermata unica ha smesso di starci in uno schermo: chi
avvia una prova guarda tre righe e un pulsante, chi la prepara scorre le opzioni, e sono due
momenti diversi.

I due ViewModel non si parlano: **passano entrambi da `IConfigStore`**, che e' singleton. Config
scrive, Stato legge. Ne segue una proprieta' comoda — un valore non valido resta nel campo di
testo e non viene mai persistito, quindi il riepilogo mostra sempre una configurazione con cui
si puo' davvero partire, e non serve nessuna validazione nella schermata Stato.

⚠️ Le modifiche **non** vengono bloccate col servizio acceso (si prepara la prossima prova
mentre gira quella in corso), ma porta e protocollo valgono dal prossimo avvio: lo dice un
banner in Configura e una riga nel riepilogo. Senza quell'avviso si cambia porta e ci si chiede
perche' il middleware bussa ancora alla vecchia.

## Portare il log sul PC

Il log vive in `getExternalFilesDir/logs/posmock_<data>.log`, che lo scoped storage rende
**invisibile ai file manager**: c'e', ma col telefono collegato al PC non si raggiunge. Il
pulsante "Esporta" della schermata Traffico ne fa una copia in `Download/PosMock/`, da cui si
tira giu' col cavo. Il percorso resta scritto a video finche' non si esporta di nuovo — e' quello
da cercare, e un messaggio che sparisce dopo due secondi costringerebbe a riesportare per
rileggerlo.

Due dettagli che non sono opzionali:

- **`ILogRepository.flush()` prima di copiare.** La scrittura su file e' asincrona (coda +
  singola coroutine): senza barriera, un export fatto subito dopo una transazione copierebbe un
  file a cui mancano proprio le ultime righe. L'attesa e' limitata a 2s, come il `drain()` di
  Ermes: con un disco bloccato meglio un file quasi completo che un pulsante che non risponde.
- **MediaStore non sovrascrive.** A parita' di nome genera `posmock_<data>(1).log`, poi `(2)`, e
  sul PC finisci per copiare la versione sbagliata. `PublicLogExporter` riusa la voce esistente
  e riapre in `"wt"` (troncamento), e **non imposta il MIME type**: con `text/plain` MediaStore
  considera `.log` non valida e rinomina in `.log.txt`, rompendo il match per nome da cui
  dipende tutto il meccanismo. Entrambe le lezioni vengono da Ermes.

## Come si ferma il server

`accept()` e le letture sui socket sono bloccanti e **non reagiscono al cancel della
coroutine**. `stop()` quindi chiude prima il `ServerSocket` e tutte le socket aperte (le
letture saltano con eccezione), poi fa il `cancelAndJoin`. L'ordine non e' invertibile: al
contrario, `stop()` tornerebbe con la porta ancora occupata e un riavvio immediato fallirebbe.

`stop()` e' **`suspend`**: aspetta davvero la fine dell'accept loop, cosi' al ritorno la porta
e' libera. Chi la chiama la lancia in uno scope — `viewModelScope` dalla schermata Stato,
quello del service dall'azione della notifica. Tre dettagli che non sono opzionali:

- **Il corpo gira in `NonCancellable`.** Gli scope che la ospitano muoiono per conto loro: il
  `viewModelScope` alla rotazione dello schermo, quello del service quando il service si ferma.
  Senza, chiudere l'app subito dopo aver premuto "Ferma" cancellerebbe la coroutine sul
  `cancelAndJoin` e salterebbe il `releaseLocks()` — wake lock in mano fino alla morte del
  processo. **Una pulizia interrotta a meta' e' peggio di nessuna pulizia.**
- **L'attesa ha un tetto di 2s**, come il `flush()` del log, e se scatta finisce nel log: cosi'
  l'`Address already in use` del riavvio successivo ha una riga che lo spiega.
- **`stopService()` e' l'ultima istruzione**, dopo lo stato e il log. Fermare il service fa
  partire il suo `onDestroy`, che cancella lo scope in cui quella stessa pulizia sta girando.
- **La notifica se la toglie il service da solo**, sullo stato `Stopped`, invece di aggiornarne
  il testo: sul filo del rasoio, ActivityManager cancella la notifica del foreground service
  appena riceve lo `stopService()` e consegna l'`onDestroy` **dopo**. In quella finestra un
  aggiornamento della notifica la fa rinascere orfana — icona accesa a banco spento e, con
  `setOngoing(true)`, nemmeno scartabile con lo swipe. La stessa pulizia sta anche in
  `onDestroy`, per l'ordine inverso.

## Quando il server muore da solo

Altra cosa dall'arresto voluto: l'interfaccia di rete cade e l'`accept()` salta mentre il banco
e' acceso. Il ramo d'errore di `start()` chiude le connessioni e rilascia i lock sul posto —
prima restavano presi fino alla morte del processo, con il pulsante in app tornato a "Avvia" e
il wake lock ancora in mano — e lascia lo stato a `Error`.

Il foreground service, che quello stato lo vede passare, si ferma da solo. Prima pero' lascia in
tendina una notifica d'errore **con un id diverso** dalla sua: quella del service il sistema se
la porta via quando il service muore, questa no. E' scartabile, perche' a quel punto non c'e'
piu' niente da fermare, e la cancella l'`onCreate` del prossimo avvio — due notifiche che si
contraddicono sono peggio di nessuna.

⚠️ La seconda notifica non e' un vezzo. Senza, una caduta di rete a schermo spento lascerebbe il
telefono muto e in apparenza a posto: **e' esattamente lo sparire in silenzio che le quattro
difese esistono per impedire**, solo per un'altra strada.

Nota di simmetria: `acquireLocks()` fa un `releaseLocks()` difensivo in testa. Un lock preso due
volte perde il riferimento al primo, che resta preso per sempre.

## Gli esiti (`MockOutcome`)

| Esito | Cosa fa il terminale | Cosa vede il middleware |
|---|---|---|
| Approvato | Status information `00` + Completion | `PaymentOk` |
| Rifiutato | Status information con error id + Abort | `PaymentFailed` |
| Nessuna risposta | tace **prima** dell'ACK | `PosBusy` dopo ~5s (timeout di registrazione) |
| ACK e poi silenzio | ACK e poi piu' niente | resta appeso: **e' l'incasso orfano** |
| Chiude la connessione | ACK e poi close | errore di comunicazione |

⚠️ In modalita' manuale l'ACK parte **subito**, prima che l'utente scelga: senza, il middleware
chiuderebbe dopo pochi secondi prima ancora che si arrivi a toccare lo schermo. Per questo li'
"nessuna risposta" si degrada a "silenzio dopo l'ACK" — che e' comunque lo scenario piu'
interessante.

Il **ritardo** configurabile si applica solo all'autorizzazione, non alla registrazione: sulla
registrazione il middleware molla dopo 5s, quindi un ritardo la' significherebbe soltanto
"POS occupato" a ogni prova, nascondendo i test veri.

## I tre protocolli

| | ZVT | IAE37 | Raw |
|---|---|---|---|
| Porta di default | 20007 | 5577 | 6000 |
| Filo | binario, BCD | ASCII, LRC | — |
| Simulato | si' | si' | no, registra |
| Importo letto | si' | si' | no |

**IAE37 e' stato ricostruito dai trace della DLL**, perche' il formato sul filo non e'
documentato: la specifica Ingenico descrive la DLL, non la socket. Dettagli, cosa e' certo e
cosa e' dedotto in [protocollo-iae37.md](protocollo-iae37.md). Il pezzo che sblocca tutto e' lo
status `t`: e' il pre-flight che il middleware fa con 5s di timeout prima di **ogni** pagamento,
e se non risponde nessuno il pagamento non parte nemmeno.

La modalita' **raw** resta, ed e' lo strumento con cui IAE37 e' stato ricostruito: serve alla
prossima volta — un protocollo sconosciuto, un terminale che si comporta diversamente, o il
bisogno di vedere in chiaro cosa passa. Sa fare anche le due cose che non richiedono di capire
il protocollo: tacere e chiudere la connessione.

Per aggiungere un protocollo: implementare `ProtocolHandler` e registrarlo in
`MockServerController.createHandler`. Il resto — esiti, ritardi, dialog, log, difese anti-Doze —
vale per tutti senza modifiche.

## File chiave (path da `app/src/main/java/it/primesoftware/posmock/`)

| Cosa | Dove |
|---|---|
| DI completo | `di/AppModule.kt` |
| Accept loop, lock, avvio service | `data/server/MockServerController.kt` |
| Macchina a stati del terminale ZVT | `data/server/protocol/zvt/ZvtTerminalHandler.kt` |
| Framing e BCD (ZVT) | `data/server/protocol/zvt/ZvtApdu.kt` |
| Costruzione dei pacchetti (ZVT) | `data/server/protocol/zvt/ZvtMessages.kt` |
| Macchina a stati del terminale IAE37 | `data/server/protocol/iae37/Iae37TerminalHandler.kt` |
| Framing e LRC (IAE37) | `data/server/protocol/iae37/Iae37Codec.kt` |
| Tracciati a posizione fissa (IAE37) | `data/server/protocol/iae37/Iae37Messages.kt` |
| Motivi di rifiuto, tradotti nei due protocolli | `domain/model/DeclineReason.kt` |
| Sniffer | `data/server/protocol/raw/RawProtocolHandler.kt` |
| Scelta dell'esito | `data/server/OutcomeProviderImpl.kt` |
| Copia del log in Download | `data/local/logging/PublicLogExporter.kt` |
| Schermata di comando (avvio/arresto) | `presentation/screen/status/view/StatusScreen.kt` |
| Schermata delle scelte | `presentation/screen/config/view/ConfigScreen.kt` |
| Prove sul formato | `app/src/test/…/ZvtCodecTest.kt`, `Iae37CodecTest.kt` |
