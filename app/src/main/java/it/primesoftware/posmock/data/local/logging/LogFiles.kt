package it.primesoftware.posmock.data.local.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dove stanno i file di log e come si chiamano.
 *
 * Sta per conto suo perche' la convenzione la usano in due — chi scrive il log e
 * chi lo esporta in Download — e due copie della stessa regola prima o poi
 * divergono, con l'export che cerca un file che nessuno scrive piu'.
 */
object LogFiles {

    private const val DIR_NAME = "logs"
    private const val PREFIX = "posmock_"
    private const val SUFFIX = ".log"

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME)

    fun fileNameFor(date: Date): String = PREFIX + dayFormat.format(date) + SUFFIX

    fun todayFileName(): String = fileNameFor(Date())

    fun fileFor(context: Context, date: Date): File =
        File(directory(context).apply { mkdirs() }, fileNameFor(date))
}
