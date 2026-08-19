# IAE37: cosa deve fare il terminale simulato

> Da leggere prima di toccare `data/server/protocol/iae37/`.
> Ultimo aggiornamento: 12/08/2026

## Da dove viene questa conoscenza

Il formato sul filo di IAE37 **non e' documentato da nessuna parte**. La specifica Ingenico
(`ING_DOC_DT_IAE37_DLL_20240621-5_45-EN.pdf`, 94 pagine) descrive la **DLL**: le funzioni
(`IAE37AX_Payment`, `IAE37AX_StatoPos`, …) e le strutture `TECRData` / `TPOSData`. Di cosa
viaggi sulla socket non dice niente.

Il tracciato qui sotto e' stato **ricostruito dai trace della DLL** (`*-iaedll-log.txt`, che
registrano `Data Tx` / `Data Rx` in esadecimale) di terminali veri: un DESK3200 il 12/08/2026 e
altri due nel 2024-2025.

⚠️ **La cosa che rende affidabile la ricostruzione e' la verifica incrociata**: le lunghezze dei
campi dedotte dai byte combaciano con le strutture dichiarate nella specifica
(`TransactionResult[2+1]`, `KODescription[24+1]`, ProductNumber 33, SerialNumber 18,
InfoRelease 80) e la loro somma da' **esattamente** i 225 byte del payload catturato. Due fonti
indipendenti che tornano.

⚠️ **I trace della DLL si cancellano a mezzanotte.** Vanno recuperati in giornata dalla cartella
dell'eseguibile di UniquePosManager (`--debug-path`, cioe' `AppDomain.BaseDirectory`). Le
catture su cui poggia questo documento sono conservate in [campioni/](campioni/) proprio per
questo — sono anche i dati usati dai test.

## Il frame

```
messaggio:  02 <payload ASCII> 03 <LRC>
ACK:        06 03 7A
NAK:        15 03 69
display:    01 <testo>            (solo POS → cassa: "ATTENDERE", "Transazione…")
```

**LRC = XOR di tutti i byte del frame — primo byte e ETX inclusi — XOR 0x7F.**
Verificato su tutti i frame delle catture, ACK e NAK compresi: `06^03^7F = 7A`,
`15^03^7F = 69`.

Il payload e' ASCII stampabile, quindi `0x03` non puo' comparirci dentro: leggere fino al primo
ETX e' sicuro.

## Testa comune e comandi

Ogni messaggio comincia con:

```
<terminalId 8><riempitivo '0' 1><comando 1>
```

La cassa manda `00000000` come terminal id (e' quello che `PosBridge.exe` passa con
`--terminal-id`), il terminale risponde col suo vero.

| Comando | Cosa | Chi lo usa |
|---|---|---|
| `t` | status esteso | **`IAE37AX_StatoPos`, il pre-flight del middleware** |
| `s` | status breve | diagnostica |
| `P` | pagamento | `IAE37AX_Payment` |
| `X` | pagamento esteso | |
| `E` | esito | risposta del terminale a `P` |
| `S` | scontrino (multi-frame) | |
| `C` `D` `B` `W` | chiusura, diagnostica, blocco, pagamento legacy | |

## Il dialogo

```
cassa → 02 …0t 03 LRC        ← 06 03 7A            ACK
                             ← 02 …0t <stato> 03 LRC
       → 06 03 7A                                  ACK

cassa → 02 …0P … 03 LRC      ← 06 03 7A            ACK
                             ← 01 … righe display  (facoltative)
                             ← 02 …0E <esito> 03 LRC
       → 06 03 7A                                  ACK
```

## Perche' lo status e' il guardiano

`Iae37Bridge` chiama `StatoPos` con un timeout di **5 secondi prima di ogni pagamento**. Se non
riceve una risposta valida, restituisce `PosBusy` e **il pagamento non parte nemmeno**: la
richiesta `P` non viene mai mandata. Finche' il mock non rispondeva a `t`, di IAE37 non si
poteva provare nient'altro — e nel log non compariva neppure l'importo, perche' lo status non lo
porta.

⚠️ **Il guardiano e' del middleware, non del protocollo.** Sulla tratta diretta Giano ↔ PosMock
non c'e' nessun pre-flight da 5 secondi: Giano applica `IAE37_SetTimeout` una volta sola, dentro
`CreateAndInitializeWrapper` (`IngenicoIAE37CreditCardTerminalDevice.cs:120`), quindi lo **stesso**
timeout vale per ogni operazione sulla DLL, `StatoPos` compreso. Il valore e'
`PaymentReadTimeoutSeconds = 60` — sessanta **secondi** per lettura, per tre letture: 180s, che
devono restare sotto il watchdog dei 200s (`PaymentTimeoutMs`). Conseguenza pratica: uno status
muto che sul middleware costa 5 secondi, su Giano ne costa fino a 180.

> Attenzione all'unita' se si va a leggere il wrapper: il parametro si chiama
> `SetTimeout(int timeoutMs)` (`IAE37Wrapper.cs:431`) ma il commento sopra dice che la DLL vuole
> **secondi**, ed e' l'aritmetica 60×3 &lt; 200 a dire chi ha ragione. Chi un giorno passasse
> millisecondi credendo al nome porterebbe il timeout a 60 ms.

## Tracciato dello status esteso (`t`), 225 byte

| Campo | Byte | Esempio |
|---|---|---|
| TerminalId | 8 | `02575733` |
| riempitivo | 1 | `0` |
| comando | 1 | `t` |
| contatore | 10 | `0000000000` |
| data GGMMAA | 6 | `120826` |
| ora HHMM | 4 | `1050` |
| ProductNumber | 33 | `P/NDESK3200EM4` + spazi |
| SerialNumber | 18 | `S/N182592207401594` |
| InfoRelease | 80 | `MAN16.08EMV3242A…EDL39.00` + spazi |
| IP | 12 | `192168001106` (ottetti a 3 cifre) |
| gateway | 12 | `192168001001` |
| ? | 2 | `01` |
| contratto | 13 | `0257573388006` |
| esercente | 24 | `PRIME SOFTWARE SRL` + spazi |
| **StatoPos** | 1 | `2` |

`StatoPos` secondo la specifica: `9` non inizializzato, `0` non configurato, `1` senza dati
acquirer, **`2` pronto (after first DLL)**, `3` fuori sequenza, `4` chiavi corrotte.

## Tracciato dell'esito (`E`)

Dopo `TransactionResult` **il tracciato cambia a seconda dell'esito** — su OK segue il PAN, su
KO la descrizione dell'errore:

```
OK:  <term 8> 0 E "00" <PAN 19> <codice autorizzazione, data, STAN, importi…>
KO:  <term 8> 0 E "01" <KODescription 24> <coda>
```

### Il return code non dice se hai incassato

Dal JSON di `PosBridge` di una carta scaduta
([campioni/posbridge-rifiuto-20260812.json](campioni/posbridge-rifiuto-20260812.json)):

```json
{"return_code":0, "transaction_result":"01", "ko_description":"TRANSAZIONE RIFIUTATA",
 "auth_code":"", "stan":"000027", "pan":"", "card_type":"2"}
```

**`return_code` e' `IAE_OK` anche su un rifiuto**: dice che si e' riusciti a parlare col
terminale, non che i soldi sono stati presi. Per questo `Iae37Bridge` pretende **entrambe** le
condizioni — `return_code == IAE_OK && transaction_result == "00"` — per dichiarare `PaymentOk`,
e manda tutto il resto su `PaymentFailed`.

### Le descrizioni di errore

Quattro, tutte osservate su terminali veri:

| Descrizione | Quando |
|---|---|
| `TRANSAZIONE RIFIUTATA` | rifiuto autorizzativo (carta scaduta, credito, …) |
| `ABORTED_MANUALLY` | annullo dall'operatore |
| `CARD_SEARCH_TIMEOUT` | carta mai avvicinata |
| `ERROR_UNKNOWN` | errore generico |

⚠️ Il terminale italiano **non dice perche' ha rifiutato**: carta scaduta e credito
insufficiente arrivano al middleware con la stessa identica stringa. `DeclineReason` rispecchia
questo e non inventa `CARD_EXPIRED` o simili — sarebbe una precisione che in campo non esiste.
Nessuna conseguenza sull'esito: il middleware non interpreta queste stringhe, le rigira come
testo in `protocolSpecificErrorDescription`.

## Cosa e' certo e cosa no

**Certo** (verificato su frame reali, coperto dai test): framing, LRC, comandi, testa comune,
tracciato dello status, posizione di `TransactionResult` e `KODescription`.

**Dedotto da un solo campione**: la posizione dell'importo nella richiesta `P` — campo `Amount`
di 8 cifre all'offset 23 del payload (unica cattura, 0,05 €). Se un giorno il log mostrasse un
importo diverso da quello chiesto dalla cassa, e' `Iae37Messages.AMOUNT_OFFSET` da correggere,
e non serve toccare altro.

**Non parametrizzato**: la coda del frame di esito positivo (PAN, codice autorizzazione, STAN,
importi) e' quella di una cattura reale. Ci sono piu' campi numerici e senza due catture con
importi diversi non si puo' dire quale sia l'importo: scrivere cifre nel campo sbagliato sarebbe
peggio che lasciare quelle della cattura. Il middleware questi campi non li usa per decidere, li
archivia come diagnostica.

**Non simulato**: scontrino (`S`), chiusura (`C`), diagnostica (`D`), blocco (`B`), le righe
display `01`. A questi comandi il mock risponde NAK — che e' una risposta prevista dal
protocollo, mentre il silenzio manderebbe la DLL in timeout facendo sembrare morto un terminale
a cui si e' solo chiesta una cosa non gestita.

## Come migliorare la ricostruzione

Serve un trace con **due pagamenti di importo diverso**: basta a identificare i campi importo
nella richiesta e nell'esito per differenza. Fare i pagamenti, poi recuperare i due file
`*-iaedll-*.txt` dalla cartella dell'eseguibile **prima di mezzanotte**.
