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

## Da leggere a inizio sessione

- [doc/struttura-app.md](doc/struttura-app.md) — mappa dell'architettura e file chiave.
- [doc/protocollo-zvt.md](doc/protocollo-zvt.md) — il dialogo ZVT byte per byte, i vincoli da
  non rompere e da dove viene questa conoscenza. **Leggerlo prima di toccare `data/server/protocol/zvt/`.**
- [doc/protocollo-iae37.md](doc/protocollo-iae37.md) — il protocollo italiano ricostruito dai
  trace della DLL: framing, LRC, comandi, cosa e' certo e cosa e' dedotto. **Leggerlo prima di
  toccare `data/server/protocol/iae37/`.**

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
- **I due silenzi sono cose diverse.** "Nessuna risposta" (prima dell'ACK) fa dichiarare POS
  occupato in 5 secondi; "ACK e poi silenzio" lascia tutti appesi ed e' il modo in cui nasce
  un incasso orfano. Chi tocca `ZvtTerminalHandler` deve tenerli distinti.
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
3. In `protocol.cfg`: `zvt` (simulazione vera) oppure `iae37` (solo registrazione dei byte).
4. Telefono e PC devono stare sulla stessa rete, e il middleware fa un **ping ICMP** prima di
   collegarsi: se la rete lo blocca, disattivare il ping per quel POS.
