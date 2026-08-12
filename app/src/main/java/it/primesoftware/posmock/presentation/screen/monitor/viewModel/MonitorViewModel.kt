package it.primesoftware.posmock.presentation.screen.monitor.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.primesoftware.posmock.domain.model.ExportResult
import it.primesoftware.posmock.domain.model.LogEntry
import it.primesoftware.posmock.domain.repository.ILogExporter
import it.primesoftware.posmock.domain.repository.ILogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Esito dell'ultimo export, da mostrare sotto i pulsanti. */
data class ExportStatus(val message: String, val isError: Boolean)

class MonitorViewModel(
    private val logRepository: ILogRepository,
    private val logExporter: ILogExporter,
) : ViewModel() {

    val entries: StateFlow<List<LogEntry>> = logRepository.entries

    val logFilePath: String? = logRepository.currentLogFilePath()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportStatus = MutableStateFlow<ExportStatus?>(null)
    val exportStatus: StateFlow<ExportStatus?> = _exportStatus.asStateFlow()

    fun onClearClicked() = logRepository.clear()

    fun onExportClicked() {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            _exportStatus.value = null
            val result = logExporter.exportTodayLog()
            _exportStatus.value = when (result) {
                is ExportResult.Success -> ExportStatus(
                    message = "Log copiato in ${result.publicPath}",
                    isError = false,
                )

                ExportResult.NoLogFile -> ExportStatus(
                    message = "Nessun log da esportare per oggi",
                    isError = true,
                )

                is ExportResult.Failure -> ExportStatus(
                    message = "Export non riuscito: ${result.message}",
                    isError = true,
                )
            }
            _isExporting.value = false
        }
    }
}
