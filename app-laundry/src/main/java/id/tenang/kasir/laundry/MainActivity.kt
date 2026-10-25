package id.tenang.kasir.laundry

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.a3.core.auth.DeviceCheckResult
import id.a3.core.auth.DeviceManager
import id.a3.core.auth.DeviceManagerFactory
import id.a3.core.auth.DeviceRegisterResult
import id.a3.core.data.local.AppDatabase
import id.a3.core.domain.device.DeviceStatus
import id.a3.core.printer.PrinterStatus
import id.a3.core.printer.connection.DefaultPrinterService
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.DeviceRegistrationScreen
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.LaundryTheme
import id.a3.feature.shift.ShiftViewModelFactory
import id.a3.feature.shift.presentation.ShiftViewModel
import id.tenang.kasir.laundry.navigation.LaundryDestinations
import id.tenang.kasir.laundry.navigation.LaundryNavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var deviceManager: DeviceManager
    private lateinit var printerService: DefaultPrinterService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        printerService = DefaultPrinterService(this)

        deviceManager = DeviceManagerFactory.getOrCreate(
            context = this,
            appConfig = LaundryAppConfig
        )
        lifecycleScope.launch {
            deviceManager.initialize()
            if (deviceManager.deviceStatus.value == DeviceStatus.ACTIVE || deviceManager.deviceStatus.value == DeviceStatus.PENDING) {
                deviceManager.checkStatus()
            }
        }

        setContent {
            LaundryTheme {
                val coroutineScope = rememberCoroutineScope()
                val deviceStatus by deviceManager.deviceStatus.collectAsState()
                val printerStatus by printerService.status.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    coroutineScope.launch(Dispatchers.IO) {
                        printerService.checkConnection()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                        )
                    } else {
                        coroutineScope.launch(Dispatchers.IO) {
                            printerService.checkConnection()
                        }
                    }
                }

                var registeredDeviceId by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    registeredDeviceId = deviceManager.getDeviceId()
                }

                if (deviceStatus != DeviceStatus.ACTIVE) {
                    var deviceNameInput by remember { mutableStateOf(LaundryAppConfig.appName) }
                    var regCodeInput by remember { mutableStateOf("") }
                    var isActionLoading by remember { mutableStateOf(false) }
                    var actionErrorMessage by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        deviceManager.getDeviceName()?.let { deviceNameInput = it }
                    }

                    DeviceRegistrationScreen(
                        deviceStatus = deviceStatus,
                        deviceId = registeredDeviceId,
                        deviceName = deviceNameInput,
                        onDeviceNameChange = { deviceNameInput = it },
                        registrationCode = regCodeInput,
                        onRegistrationCodeChange = { regCodeInput = it },
                        onRegisterClick = {
                            coroutineScope.launch {
                                isActionLoading = true
                                actionErrorMessage = null
                                val result = deviceManager.registerDevice(regCodeInput, deviceNameInput)
                                isActionLoading = false
                                if (result is DeviceRegisterResult.Error) {
                                    actionErrorMessage = result.message
                                }
                            }
                        },
                        onCheckStatusClick = {
                            coroutineScope.launch {
                                isActionLoading = true
                                actionErrorMessage = null
                                val result = deviceManager.checkStatus()
                                isActionLoading = false
                                if (result is DeviceCheckResult.Error) {
                                    actionErrorMessage = result.message
                                }
                            }
                        },
                        isLoading = isActionLoading,
                        errorMessage = actionErrorMessage,
                        businessUnitName = LaundryAppConfig.appName,
                        onDebugBypassClick = {
                            coroutineScope.launch {
                                if (deviceStatus == DeviceStatus.REVOKED) {
                                    deviceManager.resetDeviceForDebug()
                                } else {
                                    deviceManager.activateForDebug(LaundryAppConfig.appName)
                                }
                            }
                        }
                    )
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()

                    val shiftViewModel: ShiftViewModel = viewModel(
                        factory = ShiftViewModelFactory(this@MainActivity, printerService)
                    )
                    val shiftUiState by shiftViewModel.uiState.collectAsState()
                    val activeShift = shiftUiState.currentShift
                    val isShiftActive = activeShift != null

                    val laundryNavItems = remember {
                        listOf(
                            NavItem(LaundryDestinations.BERANDA, "Beranda", Icons.Outlined.Home, enabled = true),
                            NavItem(LaundryDestinations.ORDER, "Order Baru", Icons.Outlined.AddCircle, enabled = true),
                            NavItem(LaundryDestinations.CUCIAN, "Cucian", Icons.Outlined.Inventory2, enabled = true),
                            NavItem(LaundryDestinations.KAS_KELUAR, "Kas Keluar", Icons.Outlined.Payments, enabled = true),
                            NavItem(LaundryDestinations.SHIFT, "Shift", Icons.Outlined.AccessTime, enabled = true)
                        )
                    }

                    val defaultStartRoute = if (isShiftActive) LaundryDestinations.BERANDA else LaundryDestinations.SHIFT
                    val currentRoute = navBackStackEntry?.destination?.route ?: defaultStartRoute
                    val isAdminSelected = currentRoute == LaundryDestinations.ADMIN

                    val selectedIndex = remember(currentRoute, laundryNavItems) {
                        laundryNavItems.indexOfFirst { it.id == currentRoute }
                    }

                    val isFocusFlowActive = remember(currentRoute) {
                        currentRoute == LaundryDestinations.PEMBAYARAN
                    }

                    AppShell(
                        businessName = "ATHIA LAUNDRY HOUSE",
                        monogramText = "AL",
                        cashierName = activeShift?.cashierName ?: "Siti Rahma",
                        shiftInfo = activeShift?.let { "Shift Aktif · Kas ${formatRupiah(it.openingCash)}" } ?: "Belum Buka Shift",
                        syncStatus = SyncStatus.SYNCED,
                        pendingSyncCount = 0,
                        isPrinterConnected = printerStatus is PrinterStatus.Connected,
                        isPrinterConnecting = printerStatus is PrinterStatus.Connecting,
                        onPrinterClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                printerService.checkConnection()
                            }
                        },
                        onAdminClick = {
                            if (currentRoute != LaundryDestinations.ADMIN) {
                                navController.navigate(LaundryDestinations.ADMIN) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        isAdminSelected = isAdminSelected,
                        navItems = laundryNavItems,
                        selectedNavIndex = selectedIndex,
                        onNavItemSelected = { index ->
                            val item = laundryNavItems[index]
                            if (item.enabled) {
                                if (currentRoute == LaundryDestinations.ADMIN) {
                                    navController.popBackStack(LaundryDestinations.ADMIN, inclusive = true)
                                }
                                if (navController.currentDestination?.route != item.id) {
                                    navController.navigate(item.id) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        },
                        isFocusFlowActive = isFocusFlowActive
                    ) { paddingValues ->
                        LaundryNavHost(
                            navController = navController,
                            paddingValues = paddingValues,
                            printerService = printerService,
                            shiftViewModel = shiftViewModel,
                            startDestination = defaultStartRoute,
                            deviceId = registeredDeviceId.ifBlank { "dev-laundry-1" }
                        )
                    }
                }
            }
        }
    }
}
