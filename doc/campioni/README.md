# Campioni di traffico IAE37

Le catture su cui poggia [../protocollo-iae37.md](../protocollo-iae37.md). Stanno qui perche'
**i trace della DLL Ingenico si cancellano a mezzanotte**: senza questa copia, la ricostruzione
del protocollo non sarebbe piu' verificabile da nessuno, e a quel punto sarebbe solo un insieme
di affermazioni in un file di testo.

Sono anche i dati usati dai test (`Iae37CodecTest`): i frame che i test danno per buoni sono
questi, non esempi costruiti a tavolino.

| File | Cosa contiene |
|---|---|
| `iaedll-status-20260812.txt` | Lo `StatoPos` completo di un DESK3200: richiesta, ACK, risposta da 225 byte, ACK. E' il campione da cui viene il tracciato dello status. |
| `iaedll-pagamenti-20250708.txt` | Pagamenti andati a buon fine: richieste `P`/`X`, righe display `01`, esiti `E` con PAN mascherato e codice autorizzazione, scontrini `S`. |
| `iaedll-annulli-20250723.txt` | Esiti negativi: `ABORTED_MANUALLY`, `CARD_SEARCH_TIMEOUT`, e alcuni NAK — utile per vedere il protocollo quando qualcosa va storto. |
| `posbridge-rifiuto-20260812.json` | Esito di `PosBridge.exe` per una carta scaduta. E' il file che ha corretto un'ipotesi sbagliata (vedi sotto). |

## Cosa ha insegnato il JSON del rifiuto

```json
{"bridge_ok":true,"preflight_ok":true,"return_code":0,
 "transaction_result":"01","ko_description":"TRANSAZIONE RIFIUTATA",
 "auth_code":"","stan":"000027","pan":"","card_type":"2"}
```

Due cose che dai soli trace non si vedevano:

1. **`return_code` e' `0` (IAE_OK) anche su una carta rifiutata.** Il return code della DLL dice
   se si e' riusciti a parlare col terminale, non se i soldi sono stati presi. A distinguere e'
   `transaction_result`, e infatti il middleware chiede **entrambe** le condizioni
   (`return_code == IAE_OK && transaction_result == "00"`) per dichiarare un incasso.
2. **La `KODescription` reale e' `TRANSAZIONE RIFIUTATA`**, in italiano. I codici inglesi con
   underscore dei trace 2024-2025 (`ABORTED_MANUALLY`, `CARD_SEARCH_TIMEOUT`) esistono, ma sono
   per gli abort locali del terminale: il rifiuto autorizzativo ha una stringa sua, e **non dice
   il motivo**. Da qui la scelta in `DeclineReason` di non inventare `CARD_EXPIRED` e simili.

Su `auth_code` e `pan` vuoti nel rifiuto: coerente col fatto che quei campi arrivano solo
dall'autorizzazione riuscita.

## Privacy

I PAN sono gia' mascherati dal terminale (`438746******9993`). Restano identificativi
dell'esercente (ragione sociale, terminal id, seriale del POS): sono terminali di prova
dell'azienda, non di clienti.
