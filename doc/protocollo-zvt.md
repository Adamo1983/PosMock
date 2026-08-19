# ZVT: cosa deve fare il terminale simulato

> Da leggere prima di toccare `data/server/protocol/zvt/`.
> Ultimo aggiornamento: 12/08/2026

## Da dove viene questa conoscenza

Non da una specifica pubblica, ma dal **lato cassa**, che qui in casa esiste in due copie
gemelle:

- `../UniquePosManager/Libs/CardTerminalLibrary.dll` — la libreria managed di Giano, usata da
  `Server/PaymentBridges/ZvtBridge.cs`. Chiusa.
- `../PosManagerKtorDe/src/main/kotlin/it/primesoftware/zvt/` — **il port Kotlin della stessa
  libreria**, ~4000 righe leggibili. E' la fonte di tutto quello che c'e' qui sotto.

Se qualcosa non torna, il file da riaprire e' quello: in particolare
`MagicResponseCommandTransmitter.kt` (il ciclo di lettura), `ApduResponse.create()` (quali
control field vengono riconosciuti) e `StatusInformationApdu.kt` (le lunghezze dei parametri).

## Il frame

Su TCP il TPDU **e'** l'APDU: niente STX/ETX, niente checksum (quelli servono sulla seriale).

```
<classe> <istruzione> <lunghezza> [dati...]
```

La lunghezza e' un byte; se vale `FF` seguono **due byte little-endian** con la lunghezza vera.
`ZvtCodec.build`/`read` fanno solo questo.

## Il dialogo (cassa → | ← terminale)

```
→ 06 00 Registration            ← 80 00 00 ACK
                                ← 06 0F Completion (19 00)        → 80 00 00

→ 06 01 Authorization           ← 80 00 00 ACK
  (04 <importo BCD 6 byte>      ← 04 FF Intermediate status       → 80 00 00
   49 09 78 = EUR)              ← 04 0F Status information        → 80 00 00
                                ← 06 0F Completion                → 80 00 00
```

Sul rifiuto l'ultimo pacchetto e' `06 1E` **Abort** (primo byte = error id) al posto della
Completion. La cassa distingue cosi': se non c'e' Completion ma c'e' Abort, fallito; altrimenti
guarda il result code dentro lo Status Information.

La cassa manda `80 00 00` dopo **ogni** pacchetto del terminale (`AckSenderApduHandler`): sono
gli ACK che il mock consuma con `awaitAck`.

## I due vincoli che contano

**1. Prima e dopo il primo ACK sono due mondi diversi.**
`NetworkTransport.receiveResponsePacket` applica 2 secondi finche' `masterMode` e' `true`;
appena arriva un pacchetto `80 00` la libreria mette `masterMode = false`, e da li' in poi
l'attesa e' molto piu' lunga. Lato C# il `ZvtBridge` mette 5s sulla registrazione e nessun
timeout sull'autorizzazione.

Conseguenza diretta, ed e' il motivo per cui questa app esiste:

- tacere **prima** dell'ACK → il middleware dichiara `PosBusy` in ~5s;
- tacere **dopo** l'ACK del pagamento → il middleware resta appeso, il palmare va in timeout a
  90s e nessuno sa se la carta e' stata addebitata. E' l'**incasso orfano**;
- tacere **dopo l'ACK della registrazione** → il peggiore dei tre, e per questo ha un'opzione
  sua ("Muto dopo l'ACK di registrazione", separata dall'esito). La cassa entra in slave mode
  prima ancora di mandare un pagamento: `RegistrationApdu` non sovrascrive
  `SendsCompletionPacket`, quindi in `InternalIsCompletionPacket` la risposta `80 00` mette
  gia' `MasterMode = false`. Chi aspetta l'esito della registrazione prima di dichiarare il POS
  occupato — `UniquePosManager` fa `await initTask` dentro `ZvtBridge` — con la libreria
  vecchia non rispondeva **affatto**, nemmeno `PosBusy`.

**Gli stessi tre silenzi, sulla tratta diretta Giano ↔ PosMock**, raccontano un'altra storia,
perche' li' sopra la libreria non c'e' nessun middleware ma la cassa stessa:

| silenzio | catena col palmare | Giano da solo |
|---|---|---|
| prima dell'ACK | `PosBusy` in ~5s (`InitTimeoutMs`) | errore in pochi secondi (master mode, 2s) |
| dopo l'ACK del pagamento | middleware appeso fino ai 180s della libreria, palmare gia' andato a 90s | ~180s, poi `Empty response apdu data` e `Late answer from cct … the amount HAS been charged`; in asporto arriva prima la finestra dei 150s |
| dopo l'ACK della registrazione | riga a log a 5s **ma nessuna risposta** finche' l'`initTask` non rientra | 180s **per ogni tentativo di connessione**, poi riprova da capo |

I due piani di prova sono `../UniquePosManager/doc/piano-test-1.0.3.md` §8 (T8.1/T8.2/T8.3) e
`../GianoITA/Docs/TEST-POS-TIMEOUT.md` (A1/A2/A4/A5/A7). Non sono doppioni: e' la stessa
libreria vista da due chiamanti che sbagliano in modo diverso.

> **Aggiornamento 16-17/08/2026.** In slave mode la libreria non aspetta piu' per sempre:
> `SLAVE_MODE_RESPONSE_TIMEOUT` vale **180 s** (sia su `NetworkTransport` sia, dal 17/08, su
> `RS232Transport`), e la DLL aggiornata e' anche in `UniquePosManager/Libs/`. Il difetto
> nasceva da un `Timeout.Infinite` che teneva il chiamante appeso e lasciava il terminale
> "occupato" fino al riavvio dell'applicazione: vedi
> `../GianoITA/Docs/BUGFIX-POS-OCCUPATO-ZVT.md`.
>
> **Quel timeout misura il silenzio, non la durata**: riparte a ogni pacchetto ricevuto. E'
> il motivo per cui il mock manda un keep-alive durante le attese lunghe (sotto): senza,
> una transazione lenta ma viva sarebbe indistinguibile da un terminale morto, e il test la
> boccerebbe.

**1-bis. Il keep-alive durante le attese lunghe.**
Mentre aspetta il ritardo configurato o la scelta dell'utente, `ZvtTerminalHandler` manda un
`04 FF` ogni **10 s** (`KEEP_ALIVE_INTERVAL_MS`), come fa un terminale vero mentre il cliente
e' ancora al POS — nei log di campo arrivano ogni 2-10 s.

Il keep-alive si ferma **prima** che parta l'esito, quindi non intacca i due silenzi
simulabili: "Nessuna risposta" e "ACK e poi silenzio" restano silenzi veri.

Effetto pratico: con esito **Approvato**, `askEachTime` **spento** e un ritardo di 240.000 ms
si ottiene una transazione **lenta ma viva oltre i 180 s**, senza toccare il telefono. E' la
prova che nessun timeout scatti a sproposito, ed e' l'unica che puo' bocciare il lavoro sui
timeout — quella del terminale muto e' la piu' facile.

**2. Le lunghezze dei parametri non viaggiano sul filo.**
`LoadParameterHelper.loadParameters` scorre i dati leggendo un BMP e poi **tanti byte quanti
sa lui** che quel BMP occupa. Sbagliare una lunghezza non produce un errore: disallinea tutto
quello che segue, e i campi successivi vengono letti da posizioni casuali. Un BMP sconosciuto
invece interrompe la lettura senza rumore — motivo per cui il **result code va per primo**.

Lunghezze usate (dal port Kotlin):

| BMP | Campo | Byte |
|---|---|---|
| `27` | Result code | 1 |
| `04` | Importo (BCD) | 6 |
| `0B` | Trace number (BCD) | 3 |
| `0C` | Ora HHMMSS (BCD) | 3 |
| `0D` | Data MMDD (BCD) | 2 |
| `49` | Valuta (`09 78` = EUR) | 2 |
| `87` | Numero scontrino (BCD) | 2 |
| `8A` | Tipo carta | 1 |
| `29` | Terminal id (BCD) | 4 |
| `19` | Status byte (Completion) | 1 |
| `3B` | Authorisation attribute | 8 |

Lo status byte della Completion di registrazione a `00` dice "non serve inizializzazione ne'
diagnosi": con i bit 0 o 1 accesi la cassa manderebbe subito dopo un `06 93` o un `06 70`, che
il mock oggi gestisce con ACK + Completion generica.

## Cosa non e' implementato

- Print line (`06 D1` / `06 D3`): il middleware chiede al terminale di stampare da se'
  (`ECRPrintsPaymentReceipt = False`), quindi non arrivano.
- Reversal, end of day, diagnosi vera: rispondono con ACK + Completion vuota. Basta a non
  piantare la cassa, non simula il comportamento.
- TLV (`06`): la libreria non lo usa nel percorso di pagamento.
