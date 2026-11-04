package id.a3.feature.laundry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.data.security.ManagerApproverProvider
import id.a3.core.printer.PrinterService
import id.a3.core.sync.api.SyncApiService
import id.a3.feature.laundry.presentation.LaundryOrderViewModel
import id.a3.feature.laundry.presentation.LaundryOrdersViewModel
import id.a3.feature.laundry.presentation.LaundryScanViewModel
import id.a3.feature.laundry.repository.DefaultLaundryOrderRepository

class LaundryViewModelFactory(
    private val context: Context,
    private val printerService: PrinterService? = null,
    private val syncApiService: SyncApiService? = null,
    private val deviceCodeProvider: (() -> String?)? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.build(context)
        val transactionManager = OrderTransactionManager(database)
        val repository = DefaultLaundryOrderRepository(
            masterDataDao = database.masterDataDao(),
            orderDao = database.orderDao(),
            transactionManager = transactionManager,
            customerDao = database.customerDao(),
            syncApiService = syncApiService
        )

        return when {
            modelClass.isAssignableFrom(LaundryOrderViewModel::class.java) -> {
                LaundryOrderViewModel(repository, printerService, deviceCodeProvider = deviceCodeProvider) as T
            }
            modelClass.isAssignableFrom(LaundryScanViewModel::class.java) -> {
                LaundryScanViewModel(repository, printerService) as T
            }
            modelClass.isAssignableFrom(LaundryOrdersViewModel::class.java) -> {
                LaundryOrdersViewModel(repository, printerService, managerApprover = ManagerApproverProvider.get()) as T
            }
            modelClass.isAssignableFrom(id.a3.feature.laundry.presentation.LaundryHomeViewModel::class.java) -> {
                id.a3.feature.laundry.presentation.LaundryHomeViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
