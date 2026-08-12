package it.primesoftware.posmock.data.local.logging

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import it.primesoftware.posmock.domain.model.ExportResult
import it.primesoftware.posmock.domain.repository.ILogExporter
import it.primesoftware.posmock.domain.repository.ILogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copia il log del giorno da `getExternalFilesDir/logs` — invisibile ai file
 * manager per via dello scoped storage — a `Download/PosMock/`, da cui si tira
 * giu' col cavo come qualunque altro file.
 *
 * Ripreso dal `PublicLogExporter` di Ermes, comprese le due lezioni imparate li'
 * a caro prezzo: **MediaStore non sovrascrive** (a parita' di nome genera
 * `posmock_<data>(1).log`, poi `(2)`… e ti ritrovi a copiare sul PC la versione
 * sbagliata), e **il MIME type non va impostato** (con `text/plain` MediaStore
 * considera `.log` un'estensione non valida e rinomina il file in `.log.txt`,
 * rompendo il match per nome da cui dipende la sovrascrittura).
 */
class PublicLogExporter(
    private val context: Context,
    private val logRepository: ILogRepository,
) : ILogExporter {

    override suspend fun exportTodayLog(): ExportResult = withContext(Dispatchers.IO) {
        // Prima la barriera: la scrittura su file e' asincrona, e le righe piu'
        // interessanti sono sempre le ultime.
        logRepository.flush()

        try {
            val fileName = LogFiles.todayFileName()
            val source = File(LogFiles.directory(context), fileName)
            if (!source.exists() || source.length() == 0L) {
                return@withContext ExportResult.NoLogFile
            }

            val bytes = source.readBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(fileName, bytes)
            } else {
                writeViaLegacyFile(fileName, bytes)
            }
        } catch (e: Exception) {
            ExportResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Android 10+ : scrittura nella collezione pubblica Downloads via MediaStore. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(fileName: String, bytes: ByteArray): ExportResult {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_SUBDIR"

        val existing = findExistingExport(resolver, collection, relativePath, fileName)

        val uri = existing ?: run {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            resolver.insert(collection, values)
                ?: return ExportResult.Failure("MediaStore insert non riuscita")
        }

        // "wt" tronca prima di riscrivere: riusando una voce esistente, senza
        // troncamento resterebbe in coda il pezzo dell'export precedente.
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: return ExportResult.Failure("Apertura stream di scrittura non riuscita")

        return ExportResult.Success("$PUBLIC_LABEL/$fileName")
    }

    /** URI della copia pubblica con nome esatto [fileName], o null se assente. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findExistingExport(
        resolver: ContentResolver,
        collection: Uri,
        relativePath: String,
        fileName: String,
    ): Uri? {
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(fileName, "$relativePath%"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    /** Android 9 e precedenti : scrittura diretta nella cartella Download pubblica. */
    private fun writeViaLegacyFile(fileName: String, bytes: ByteArray): ExportResult {
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        val dir = File(downloads, PUBLIC_SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) {
            return ExportResult.Failure("Impossibile creare ${dir.absolutePath}")
        }
        File(dir, fileName).writeBytes(bytes)
        return ExportResult.Success("$PUBLIC_LABEL/$fileName")
    }

    private companion object {
        const val PUBLIC_SUBDIR = "PosMock"
        const val PUBLIC_LABEL = "Download/PosMock"
    }
}
