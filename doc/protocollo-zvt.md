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

**1. Il timeout esiste solo prima del primo ACK.**
`NetworkTransport.receiveResponsePacket` applica 2 secondi finche' `masterMode` e' `true`;
appena arriva un pacchetto `80 00` la libreria mette `masterMode = false` e da li' in poi
**aspetta senza limiti**. Lato C# il `ZvtBridge` mette 5s sulla registrazione e nessun timeout
sull'autorizzazione.

Conseguenza diretta, ed e' il motivo per cui questa app esiste:

- tacere **prima** dell'ACK → il middleware dichiara `PosBusy` in ~5s;
- tacere **dopo** l'ACK → non se ne esce: il middleware resta appeso, il palmare va in timeout
  a 90s e nessuno sa se la carta e' stata addebitata. E' l'**incasso orfano**.

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
