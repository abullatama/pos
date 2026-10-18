package id.a3.core.printer.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import id.a3.core.printer.escpos.EscPosBuilder
import id.a3.core.printer.queue.PrintJobStatus
import id.a3.core.printer.queue.PrintQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

sealed interface PrinterConnectionTarget {
    data class Bluetooth(val device: BluetoothDevice) : PrinterConnectionTarget {
        @SuppressLint("MissingPermission")
        val name: String = device.name ?: "Bluetooth Printer"
        val address: String = device.address
    }

    data class Usb(val device: UsbDevice) : PrinterConnectionTarget {
        val name: String = device.productName ?: "USB Printer (${device.deviceId})"
        val address: String = "USB:${device.vendorId}:${device.productId}"
    }
}

/**
 * Implementasi produksi PrinterService untuk iWare C58BT dan printer ESC/POS 58mm lainnya:
 * Mendukung koneksi Bluetooth SPP (00001101-0000-1000-8000-00805F9B34FB) dan USB Bulk Transfer.
 */
class DefaultPrinterService(
    private val context: Context
) : PrinterService {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    override val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var activeTarget: PrinterConnectionTarget? = null

    fun setTarget(target: PrinterConnectionTarget?) {
        activeTarget = target
        if (target == null) {
            _status.value = PrinterStatus.Disconnected
        }
    }

    /**
     * Otomatis mendeteksi kandidat printer termal yang sudah dipasangkan (paired/bonded)
     * via Bluetooth atau printer yang terpasang via kabel USB OTG tanpa mengubah status menjadi terhubung palsu.
     */
    @SuppressLint("MissingPermission")
    fun autoConnectPairedPrinter(): Boolean {
        // 1. Coba deteksi USB Printer
        try {
            val usbDevices = usbManager.deviceList.values
            for (device in usbDevices) {
                val isPrinter = (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == 7
                }
                if (isPrinter || device.productName?.contains("printer", ignoreCase = true) == true) {
                    setTarget(PrinterConnectionTarget.Usb(device))
                    return true
                }
            }
        } catch (_: Exception) {}

        // 2. Coba deteksi Bluetooth Paired/Bonded Devices
        try {
            val bonded = bluetoothAdapter?.bondedDevices
            android.util.Log.d("DefaultPrinterService", "Scanning bonded devices: ${bonded?.size ?: 0} devices found")
            if (!bonded.isNullOrEmpty()) {
                val printerKeywords = listOf("c58", "rpp", "pos", "printer", "mtp", "bt", "58", "thermal", "iware")
                val matched = bonded.find { dev ->
                    val name = dev.name?.lowercase() ?: ""
                    printerKeywords.any { kw -> name.contains(kw) }
                } ?: bonded.first()

                android.util.Log.d("DefaultPrinterService", "Auto-selected printer candidate: ${matched.name} [${matched.address}]")
                setTarget(PrinterConnectionTarget.Bluetooth(matched))
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultPrinterService", "Error reading bonded devices", e)
        }

        return false
    }

    /**
     * Memverifikasi konektivitas fisik nyata ke printer termal (Bluetooth handshake atau USB ready).
     * Jika printer mati atau tidak dapat dihubungi, status disetel ke Disconnected (Printer Putus).
     */
    @SuppressLint("MissingPermission")
    override suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        if (activeTarget == null) {
            autoConnectPairedPrinter()
        }
        val target = activeTarget
        if (target == null) {
            _status.value = PrinterStatus.Disconnected
            return@withContext false
        }

        when (target) {
            is PrinterConnectionTarget.Usb -> {
                val hasPermission = usbManager.hasPermission(target.device)
                val isAttached = usbManager.deviceList.containsValue(target.device)
                if (isAttached && hasPermission) {
                    _status.value = PrinterStatus.Connected(target.name, target.address)
                    true
                } else {
                    _status.value = PrinterStatus.Disconnected
                    false
                }
            }
            is PrinterConnectionTarget.Bluetooth -> {
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    _status.value = PrinterStatus.Disconnected
                    return@withContext false
                }
                var socket: BluetoothSocket? = null
                try {
                    _status.value = PrinterStatus.Connecting
                    bluetoothAdapter.cancelDiscovery()
                    socket = try {
                        target.device.createRfcommSocketToServiceRecord(sppUuid)
                    } catch (_: Exception) {
                        val m = target.device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        m.invoke(target.device, 1) as BluetoothSocket
                    }
                    socket.connect()
                    _status.value = PrinterStatus.Connected(target.name, target.address)
                    true
                } catch (e: Exception) {
                    android.util.Log.d("DefaultPrinterService", "Printer check connection failed (offline): ${e.message}")
                    _status.value = PrinterStatus.Disconnected
                    false
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    override val queueManager: PrintQueueManager by lazy {
        PrintQueueManager(
            printDirect = { printDirect(it) },
            isConnected = { isConnected() },
            statusFlow = status
        )
    }

    override fun isConnected(): Boolean {
        return _status.value is PrinterStatus.Connected
    }

    suspend fun printDirect(bytes: ByteArray): Result<Unit> {
        if (activeTarget == null) {
            autoConnectPairedPrinter()
        }
        val target = activeTarget ?: return Result.failure(IllegalStateException("Belum ada printer yang terhubung atau dipasangkan."))
        return when (target) {
            is PrinterConnectionTarget.Bluetooth -> printBluetooth(target.device, bytes)
            is PrinterConnectionTarget.Usb -> printUsb(target.device, bytes)
        }
    }

    override suspend fun print(bytes: ByteArray): Result<Unit> {
        val job = queueManager.printOrEnqueue(payload = bytes)
        return if (job.status == PrintJobStatus.FAILED) {
            Result.failure(Exception(job.lastError ?: "Gagal mencetak serial"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> {
        val drawerCmd = EscPosBuilder().openCashDrawer().build()
        return printDirect(drawerCmd)
    }

    @SuppressLint("MissingPermission")
    private suspend fun printBluetooth(device: BluetoothDevice, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            try {
                android.util.Log.d("DefaultPrinterService", "Connecting to ${device.name} [${device.address}] via SPP...")
                _status.value = PrinterStatus.Connecting
                bluetoothAdapter?.cancelDiscovery()

                try {
                    socket = device.createRfcommSocketToServiceRecord(sppUuid)
                    socket.connect()
                } catch (connectException: Exception) {
                    android.util.Log.w("DefaultPrinterService", "Standard SPP failed, trying reflection fallback", connectException)
                    try {
                        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = m.invoke(device, 1) as BluetoothSocket
                        socket.connect()
                    } catch (_: Exception) {
                        throw connectException
                    }
                }

                android.util.Log.d("DefaultPrinterService", "Connected! Writing ${data.size} bytes...")
                outputStream = socket.outputStream

                outputStream.write(data)
                outputStream.flush()

                // Jeda agar buffer serial printer selesai dicetak sebelum socket ditutup
                delay(1500)
                _status.value = PrinterStatus.Connected(device.name ?: "Bluetooth", device.address)
                android.util.Log.d("DefaultPrinterService", "Print successful!")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("DefaultPrinterService", "Print Bluetooth error", e)
                _status.value = PrinterStatus.Error(e.message ?: "Gagal mencetak via Bluetooth")
                Result.failure(e)
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }

    private suspend fun printUsb(device: UsbDevice, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var connection: UsbDeviceConnection? = null
            var usbInterface: UsbInterface? = null
            try {
                if (!usbManager.hasPermission(device)) {
                    return@withContext Result.failure(SecurityException("Tidak ada izin USB untuk perangkat printer."))
                }

                connection = usbManager.openDevice(device)
                    ?: return@withContext Result.failure(IllegalStateException("Gagal membuka koneksi USB."))

                var outEndpoint: UsbEndpoint? = null
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    for (j in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(j)
                        if (ep.direction == UsbConstants.USB_DIR_OUT &&
                            ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                        ) {
                            usbInterface = intf
                            outEndpoint = ep
                            break
                        }
                    }
                    if (outEndpoint != null) break
                }

                val targetEndpoint = outEndpoint
                    ?: return@withContext Result.failure(IllegalStateException("Endpoint Bulk OUT tidak ditemukan."))
                val targetInterface = usbInterface
                    ?: return@withContext Result.failure(IllegalStateException("USB Interface tidak ditemukan."))

                connection.claimInterface(targetInterface, true)

                val transferred = connection.bulkTransfer(targetEndpoint, data, data.size, 5000)
                if (transferred >= 0) {
                    _status.value = PrinterStatus.Connected(device.productName ?: "USB", "USB:${device.deviceId}")
                    Result.success(Unit)
                } else {
                    _status.value = PrinterStatus.Error("Gagal transfer USB ($transferred)")
                    Result.failure(IllegalStateException("Gagal mengirim data bulk transfer: $transferred"))
                }
            } catch (e: Exception) {
                _status.value = PrinterStatus.Error(e.message ?: "Gagal koneksi USB")
                Result.failure(e)
            } finally {
                try {
                    usbInterface?.let { connection?.releaseInterface(it) }
                } catch (_: Exception) {}
                try { connection?.close() } catch (_: Exception) {}
            }
        }
}
