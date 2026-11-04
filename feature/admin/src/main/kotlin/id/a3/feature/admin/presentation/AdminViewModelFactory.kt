package id.a3.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.security.ManagerApproverProvider
import id.a3.core.printer.PrinterService
import id.a3.feature.admin.update.UpdatePreconditionValidator

class AdminViewModelFactory(
    private val database: AppDatabase,
    private val printerService: PrinterService? = null,
    private val deviceId: String = "DEV-TABLET-01",
    private val businessUnitId: String = "BU-01",
    private val appVersion: String = "1.0.0"
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            val shiftDao = database.shiftDao()
            val outboxDao = database.outboxDao()
            val validator = UpdatePreconditionValidator(shiftDao, outboxDao)

            return AdminViewModel(
                shiftDao = shiftDao,
                outboxDao = outboxDao,
                updateValidator = validator,
                printerService = printerService,
                deviceId = deviceId,
                businessUnitId = businessUnitId,
                appVersion = appVersion,
                managerApprover = ManagerApproverProvider.get()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
