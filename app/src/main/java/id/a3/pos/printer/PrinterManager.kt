package id.a3.pos.printer

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

sealed interface PrinterConnection {
    data class Bluetooth(val device: BluetoothDevice) : PrinterConnection {
        @SuppressLint("MissingPermission")
        override val name: String = device.name ?: "Unknown Bluetooth"
        override val address: String = device.address
    }

    data class Usb(val device: UsbDevice) : PrinterConnection {
        override val name: String = device.productName ?: "USB Printer (${device.deviceId})"
        override val address: String = "USB:${device.vendorId}:${device.productId}"
    }

    val name: String
    val address: String
}

class PrinterManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Standard SPP UUID
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedBluetoothPrinters(): List<PrinterConnection.Bluetooth> {
        val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
        return bonded.map { PrinterConnection.Bluetooth(it) }
    }

    fun getConnectedUsbPrinters(): List<PrinterConnection.Usb> {
        val deviceList = usbManager.deviceList
        val printers = mutableListOf<PrinterConnection.Usb>()
        for ((_, device) in deviceList) {
            var isPrinter = device.deviceClass == UsbConstants.USB_CLASS_PRINTER
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER ||
                    intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                ) {
                    isPrinter = true
                    break
                }
            }
            if (isPrinter) {
                printers.add(PrinterConnection.Usb(device))
            }
        }
        return printers
    }

    @SuppressLint("MissingPermission")
    suspend fun printBluetooth(device: BluetoothDevice, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            try {
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                outputStream = socket.outputStream

                // Kirim seluruh data langsung seperti pengujian awal yang berhasil
                outputStream.write(data)
                outputStream.flush()

                // Berikan jeda agar buffer serial printer selesai dicetak sebelum socket ditutup
                delay(1500)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }

    suspend fun printUsb(device: UsbDevice, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            var connection: UsbDeviceConnection? = null
            var usbInterface: UsbInterface? = null
            try {
                if (!usbManager.hasPermission(device)) {
                    return@withContext Result.failure(SecurityException("Tidak ada izin USB untuk perangkat ini."))
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
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Gagal mengirim data bulk transfer: $transferred"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try {
                    usbInterface?.let { connection?.releaseInterface(it) }
                } catch (_: Exception) {}
                try { connection?.close() } catch (_: Exception) {}
            }
        }
}
