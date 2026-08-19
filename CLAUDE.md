# PosMock — istruzioni per Claude

App Android (Jetpack Compose) che **finge di essere un terminale POS**. Si mette in ascolto
su una porta TCP e risponde a chi la interroga: nella pratica **UniquePosManager** (middleware
C#/WPF, Italia) o **PosManagerKtorDe** (Ktor, Germania).

```
Giano (cassa) ↔ Ermes (palmare) ──WS──► UniquePosManager ──TCP──► PosMock   ← invece del POS vero
```

Serve a provare il percorso di pagamento senza terminale fisico, e soprattutto a **riprodurre
a comando i casi che sul banco non si riescono a provocare**: il terminale che non risponde,
quello che risponde dopo un minuto, quello che chiude la connessione a meta' transazione. Sono
gli scenari da cui nasce il pagamento orfano (`../UniquePosManager/doc/pagamenti-orfani.md`,
`../Ermes/doc/analisi-pagamento-pos-orfano.md`).

## Stato al 14/08/2026

**ZVT è validato contro il middleware vero. IAE37 no**: il codice c'è tutto (status `t`,
pagamento `P`, esito `E`, i cinque esiti mock) ma non è mai stato messo davanti a
UniquePosManager. Il collaudo è il §1 di `../UniquePosManager/doc/piano-test-1.0.3.md`, e i due
passi che contano sono:

1. **lo status `t`** — il middleware lo interroga con 5s di timeout prima di *ogni* pagamento e
   senza risposta valida dichiara `PosBusy`: finché non passa quello, di IAE37 non si prova
   nient'altro;
2. **la calibrazione dell'importo** — `Iae37Messages.AMOUNT_OFFSET = 23` è dedotto da **una sola
   cattura** (0,05 €). Due pagamenti di importo diverso lo confermano o danno subito il valore
   giusto; è l'unica costante da correggere.

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
  parte nemmeno.
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
