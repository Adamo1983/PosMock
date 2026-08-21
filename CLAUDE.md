# PosMock — istruzioni per Claude

App Android (Jetpack Compose) che **finge di essere un terminale POS**. Si mette in ascolto
su una porta TCP e risponde a chi la interroga: nella pratica **UniquePosManager** (middleware
C#/WPF, Italia), **PosManagerKtorDe** (Ktor, Germania) oppure **Giano** stesso, che al terminale
ci parla anche da solo.

```
Giano (cassa) ↔ Ermes (palmare) ──WS──► UniquePosManager ──TCP──► PosMock   ← invece del POS vero
Giano (cassa) ─────────────────────────────────────────────TCP──► PosMock   ← tratta diretta
```

**Sono due tratte, non due modi di guardare la stessa.** La catena col palmare passa dal
middleware, che ha timeout suoi; la tratta diretta no — li' la cassa parla al terminale con la
`CardTerminalLibrary` (ZVT) o con la DLL Ingenico (IAE37), e i tempi sono altri. Ognuna ha il
suo piano di prova: `../UniquePosManager/doc/piano-test-1.0.3.md` e
`../GianoITA/Docs/TEST-POS-TIMEOUT.md`. **Vanno provate tutte e due**: a parita' di guasto
raccontano storie diverse.

Serve a provare il percorso di pagamento senza terminale fisico, e soprattutto a **riprodurre
a comando i casi che sul banco non si riescono a provocare**: il terminale che non risponde,
quello che risponde dopo un minuto, quello che chiude la connessione a meta' transazione. Sono
gli scenari da cui nasce il pagamento orfano (`../UniquePosManager/doc/pagamenti-orfani.md`,
`../Ermes/doc/analisi-pagamento-pos-orfano.md`).

## Stato al 21/08/2026

**Validati contro il middleware vero tutti e due i protocolli**: ZVT dal 14/08, **IAE37 dal
21/08**, in una sessione con Ermes 2.2.2-debug + UniquePosManager 1.0.3 e il POS `adamo_mock`
(192.168.1.104:5577). I due passi che erano rimasti aperti sono chiusi:

1. **lo status `t`/`s`** — il middleware lo interroga prima di *ogni* pagamento e senza risposta
   valida dichiara `PosBusy`: risposto correttamente, con l'ACK a 4 ms e il frame di stato subito
   dopo. Finché non passa quello, di IAE37 non si prova nient'altro;
2. **la calibrazione dell'importo** — `Iae37Messages.AMOUNT_OFFSET = 23`, che era dedotto da
   **una sola cattura** (0,05 €), è confermato: `--amount "00000100"` letto come 1,00 € e
   `"00000200"` come 2,00 €. Due importi diversi, nessuna correzione da fare.

Provati dal vivo anche `Approvato`, `Rifiutato (credito insufficiente)`, `Nessuna risposta`,
`ACK e poi silenzio` e `Chiude la connessione`, ciascuno riconosciuto correttamente lungo tutta
la catena fino al palmare. Lo scenario "ACK e poi silenzio" ha fatto emergere un difetto vero
lato Ermes (l'avviso di esito incerto che si perdeva al tentativo successivo), corretto lo stesso
giorno: cronaca in `../ermes-jetpack-compose/doc/analisi-pagamento-pos-orfano.md`, quinto giro.

### ⚠️ Due trappole del banco, imparate quel giorno

- **Lo status legge `defaultOutcome`, non la scelta del dialog.** Con "Chiedi ogni volta" attivo,
  quello che tocchi nel dialog governa **solo il pagamento**: `handleStatus` guarda l'esito
  *predefinito* nelle impostazioni. Il 21/08 il predefinito era rimasto su "Chiude la
  connessione" mentre dal dialog si sceglieva "ACK e poi silenzio" — risultato: il pre-flight
  moriva prima che il dialog comparisse, e per un quarto d'ora tutto rispondeva "POS occupato"
  senza che nel log del mock comparisse una sola riga `Richiesta pagamento`.
- **Con `Nessuna risposta` o `Chiude la connessione` come predefinito non si prova più niente**:
  il middleware non supera mai il pre-flight, quindi *qualunque* scenario si volesse provare
  diventa "POS occupato". Per `NoAck` è voluto e realistico (un POS occupato dal cassiere davvero
  non risponde allo stato); per `DropConnection` è un effetto collaterale che blocca il banco in
  silenzio. **Da valutare**: rendere lo status indipendente dall'esito di pagamento, o almeno
  avvisare in UI che con quei due predefiniti nessun pagamento partirà.

Le due firme si distinguono a occhio dai tempi, ed è utile saperlo per non confondersi:
connessione chiusa → `returnCode=5` in ~120 ms; silenzio → `returnCode=1` dopo ~3 s.

## Da leggere a inizio sessione

- [doc/struttura-app.md](doc/struttura-app.md) — mappa dell'architettura e file chiave.
- [doc/protocollo-zvt.md](doc/protocollo-zvt.md) — il dialogo ZVT byte per byte, i vincoli da
  non rompere e da dove viene questa conoscenza. **Leggerlo prima di toccare `data/server/protocol/zvt/`.**
- [doc/protocollo-iae37.md](doc/protocollo-iae37.md) — il protocollo italiano ricostruito dai
  trace della DLL: framing, LRC, comandi, cosa e' certo e cosa e' dedotto. **Leggerlo prima di
  toccare `data/server/protocol/iae37/`.**
- [doc/Service-Android-in-PosMock.pdf](doc/Service-Android-in-PosMock.pdf) — perche' c'e' un
  foreground service, perche' il server non vive dentro di lui e cosa succede allo stop. E'
  **generato**: si modifica `doc/genera-pdf-service.py` e si rigenera, mai il PDF a mano.

## Convenzioni

- Rispondi in **italiano**; commenti nel codice e messaggi di commit in italiano.
- Commit nello stile degli altri repo: `GG/MM/AAAA: descrizione breve`.
- Architettura: MVVM + Clean (data/domain/presentation), DI con Koin in `di/AppModule.kt`,
  schermate come `screen/<nome>/{view,viewModel}` — la stessa di Ermes.
- I commenti spiegano **perche'**, non cosa.

## Vincoli da non rompere

- **Le lunghezze dei campi non stanno sul filo**, in nessuno dei due protocolli: chi legge le
  conosce a memoria. Un campo di lunghezza sbagliata non da' errore, disallinea tutto quello che
  segue. Vedi le tabelle in `ZvtMessages` e `Iae37Messages`.
- **Lo status IAE37 (`t`) e' il guardiano**: il middleware lo interroga con 5s di timeout prima
  di ogni pagamento e senza risposta valida dichiara `PosBusy` — la richiesta di pagamento non
  parte nemmeno. Quei 5s sono pero' **del middleware, non del protocollo**: Giano applica alla
  DLL un unico timeout da 60s per lettura, status compreso, quindi lo stesso silenzio li' costa
  fino a 180s. Vedi `doc/protocollo-iae37.md`.
- **I tre silenzi sono cose diverse.** "Nessuna risposta" (prima dell'ACK) fa dichiarare POS
  occupato in 5 secondi; "ACK e poi silenzio" lascia tutti appesi sul pagamento ed e' il modo
  in cui nasce un incasso orfano; **"Muto dopo l'ACK di registrazione"** (opzione a parte, non
  un esito) blocca la cassa prima ancora che un pagamento parta. Chi tocca
  `ZvtTerminalHandler` deve tenerli distinti.
- **Il keep-alive non deve entrare nei silenzi.** Durante l'attesa dell'esito il mock manda uno
  stato intermedio ogni 10 s, perche' altrimenti una transazione lenta ma viva sarebbe
  indistinguibile da un terminale morto. Si ferma pero' **prima** che l'esito parta: se
  finisse per coprire anche i silenzi qui sopra, il mock smetterebbe di saper riprodurre il
  guasto per cui esiste.
- **Il server e' un singleton di processo, non vive nel service.** Il foreground service
  serve solo a tenere vivo il processo e a mostrare la notifica.
- **Le quattro difese contro Doze non si tolgono** (foreground service, wake lock, wifi lock,
  keep-screen-on + esenzione batteria): un mock che sparisce a schermo spento produce gli
  stessi sintomi del guasto che si sta indagando. Vedi `doc/struttura-app.md`.

## Build

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

AGP 9.3.1 / Gradle 9.5 / Kotlin 2.2.10 (compilatore Kotlin **integrato in AGP**: non c'e' il
plugin `org.jetbrains.kotlin.android`, e per lo stesso motivo non c'e' kotlinx-serialization).
minSdk 26, target/compile 37.

Le prove contro il middleware vero le fa l'utente.

## Collegarlo a UniquePosManager

1. Avvia il servizio in PosMock e leggi l'indirizzo mostrato nella schermata Configurazione.
2. Lato middleware, in `posList.cfg`: `PosMock:<ip del telefono>:<porta>`.
3. In `protocol.cfg`: `zvt` oppure `iae37` — **entrambi simulati per davvero** (status, pagamento,
   esiti). La modalita' `raw` registra e basta, e serve per i protocolli ancora da capire.
4. Telefono e PC devono stare sulla stessa rete, e il middleware fa un **ping ICMP** prima di
   collegarsi: se la rete lo blocca, disattivare il ping per quel POS.

## Collegarlo a Giano, senza middleware

1. Avvia il servizio in PosMock: protocollo **ZVT** su **20007**, oppure **IAE37** su **5577**.
2. In Giano, pagina admin → hardware registrato → terminale Ingenico: TCP/IP, IP del telefono,
   la stessa porta, `InUse` acceso. Per IAE37 servono **tre** cose e non una:
   `RegionalSettingsLanguage` = Italiano, riavvio, porta 5577. Il log deve dire
   `protocol=IAE37 (Protocollo 17)` prima di cominciare.
3. Il telefono deve **rispondere al ping ICMP**: Giano pinga ogni 5 s e — a differenza del
   middleware — **non lo si puo' disattivare**. Se non risponde, il terminale risulta
   `NotResponding` e il tasto POS resta spento.
4. Il tender deve contenere `KARTE`, `CARTA` o `CREDIT CARD` nel nome e non essere marcato cash,
   altrimenti `TenderVM.IsCardTender` non lo riconosce.

⚠️ Su Giano **asporto e tavolo non sono la stessa prova**: l'asporto ha una finestra di 150 s
sulla durata totale, sui tavoli non c'e' (`SendPaymentAndForget`). Un ritardo lungo va provato
**sul tavolo**, altrimenti scatta la finestra e non si misura quello che si voleva misurare.
