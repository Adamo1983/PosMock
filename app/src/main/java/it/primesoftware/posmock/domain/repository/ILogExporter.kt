package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.ExportResult

/**
 * Copia il log dalla cartella privata dell'app a `Download/PosMock/`.
 *
 * Serve perche' `getExternalFilesDir` e' invisibile ai file manager per via
 * dello scoped storage: il log c'e', ma dal telefono collegato al PC non si
 * raggiunge. Da `Download/` invece si copia via cavo come qualunque altro file.
 */
interface ILogExporter {

    /** Copia il log del giorno corrente, sovrascrivendo la copia precedente. */
    suspend fun exportTodayLog(): ExportResult
}
