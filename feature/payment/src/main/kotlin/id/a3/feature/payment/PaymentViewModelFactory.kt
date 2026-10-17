package id.a3.feature.payment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.printer.PrinterService
import id.a3.core.printer.connection.DefaultPrinterService
import id.a3.feature.payment.presentation.PaymentViewModel
import id.a3.feature.payment.repository.DefaultPaymentRepository

class PaymentViewModelFactory(
    private val context: Context,
    private val printerService: PrinterService? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaymentViewModel::class.java)) {
            val database = AppDatabase.build(context)
            val transactionManager = OrderTransactionManager(database)
            val repository = DefaultPaymentRepository(transactionManager)
            val actualPrinterService = printerService ?: DefaultPrinterService(context).apply {
                autoConnectPairedPrinter()
            }
            return PaymentViewModel(repository, actualPrinterService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
