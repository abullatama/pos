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

    fun setTarget(target: PrinterConnectionTarget) {
        activeTarget = target
        when (target) {
            is PrinterConnectionTarget.Bluetooth -> {
                _status.value = PrinterStatus.Connected(target.name, target.address)
            }
            is PrinterConnectionTarget.Usb -> {
                _status.value = PrinterStatus.Connected(target.name, target.address)
            }
        }
    }

    override fun isConnected(): Boolean {
        return _status.value is PrinterStatus.Connected
    }

    override suspend fun print(bytes: ByteArray): Result<Unit> {
        val target = activeTarget ?: return Result.failure(IllegalStateException("Belum ada printer yang dipilih."))
        return when (target) {
            is PrinterConnectionTarget.Bluetooth -> printBluetooth(target.device, bytes)
            is PrinterConnectionTarget.Usb -> printUsb(target.device, bytes)
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> {
        val drawerCmd = EscPosBuilder().openCashDrawer().build()
        return print(drawerCmd)
    }

    @SuppressLint("MissingPermission")
    private suspend fun printBluetooth(device: BluetoothDevice, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            try {
                _status.value = PrinterStatus.Connecting
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                outputStream = socket.outputStream

                outputStream.write(data)
                outputStream.flush()

                // Jeda agar buffer serial printer selesai dicetak sebelum socket ditutup
                delay(1500)
                _status.value = PrinterStatus.Connected(device.name ?: "Bluetooth", device.address)
                Result.success(Unit)
            } catch (e: Exception) {
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
