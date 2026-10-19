package id.a3.feature.shift

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.printer.PrinterService
import id.a3.feature.shift.presentation.CloseShiftViewModel
import id.a3.feature.shift.presentation.ShiftViewModel
import id.a3.feature.shift.repository.DefaultShiftRepository

class ShiftViewModelFactory(
    private val context: Context,
    private val printerService: PrinterService? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.build(context)
        val transactionManager = OrderTransactionManager(database)
        val repository = DefaultShiftRepository(
            shiftDao = database.shiftDao(),
            transactionManager = transactionManager
        )

        return when {
            modelClass.isAssignableFrom(ShiftViewModel::class.java) -> {
                ShiftViewModel(repository) as T
            }
            modelClass.isAssignableFrom(CloseShiftViewModel::class.java) -> {
                CloseShiftViewModel(repository, printerService) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
