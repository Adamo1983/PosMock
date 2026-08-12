package it.primesoftware.posmock.domain.model

/** Esito della copia del log nella cartella pubblica Download. */
sealed interface ExportResult {

    /** [publicPath] e' il percorso leggibile da mostrare all'utente. */
    data class Success(val publicPath: String) : ExportResult

    /** Non c'e' niente da esportare: il log del giorno non esiste o e' vuoto. */
    data object NoLogFile : ExportResult

    data class Failure(val message: String) : ExportResult
}
