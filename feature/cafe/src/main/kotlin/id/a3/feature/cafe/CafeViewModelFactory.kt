package id.a3.feature.cafe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.data.security.ManagerApproverProvider
import id.a3.core.printer.PrinterService
import id.a3.core.printer.connection.DefaultPrinterService
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.repository.DefaultCafeMenuRepository

class CafeMenuViewModelFactory(
    private val context: Context,
    private val printerService: PrinterService? = null,
    private val deviceCodeProvider: (() -> String?)? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CafeMenuViewModel::class.java)) {
            val database = AppDatabase.build(context)
            val transactionManager = OrderTransactionManager(database)
            val repository = DefaultCafeMenuRepository(database.masterDataDao(), transactionManager, database.syncStateDao())
            val actualPrinterService = printerService ?: DefaultPrinterService(context).apply {
                autoConnectPairedPrinter()
            }
            return CafeMenuViewModel(
                repository,
                transactionManager,
                actualPrinterService,
                deviceCodeProvider = deviceCodeProvider,
                managerApprover = ManagerApproverProvider.get()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
