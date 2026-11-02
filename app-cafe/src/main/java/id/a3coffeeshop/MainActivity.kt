package id.a3coffeeshop

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
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
import id.a3.core.domain.monitoring.AppCrashManager
import id.a3.core.printer.PrinterStatus
import id.a3.core.printer.connection.DefaultPrinterService
import id.a3.core.sync.DeviceHeartbeatManager
import id.a3.core.sync.DeviceHeartbeatWorker
import id.a3.core.sync.OutboxSyncManager
import id.a3.core.sync.OutboxSyncWorker
import id.a3.core.sync.SyncSender
import id.a3.core.sync.api.SyncApiServiceFactory
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.DeviceRegistrationScreen
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.theme.CafeTheme
import id.a3.feature.shift.ShiftViewModelFactory
import id.a3.feature.shift.presentation.ShiftViewModel
import id.a3coffeeshop.navigation.CafeDestinations
import id.a3coffeeshop.navigation.CafeNavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var deviceManager: DeviceManager
    private lateinit var printerService: DefaultPrinterService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Inisialisasi pengawas crash terpusat dengan sanitasi data sensitif (OPS-01)
        AppCrashManager.installUncaughtExceptionHandler()

        printerService = DefaultPrinterService(this)

        deviceManager = DeviceManagerFactory.getOrCreate(
            context = this,
            appConfig = CafeAppConfig
        )

        // 2. Inisialisasi manajer detak jantung perangkat & jadwalkan worker periodik (F41)
        val heartbeatManager = DeviceHeartbeatManager.create(
            context = this,
            deviceManager = deviceManager,
            printerStatusProvider = {
                when (printerService.status.value) {
                    is PrinterStatus.Connected -> "ok"
                    is PrinterStatus.Disconnected -> "offline"
                    is PrinterStatus.Error -> "error"
                    is PrinterStatus.Connecting -> "unknown"
                }
            }
        )
        DeviceHeartbeatWorker.heartbeatManager = heartbeatManager
        DeviceHeartbeatWorker.schedulePeriodic(this, intervalMinutes = 15)

        val database = AppDatabase.getInstance(this)

        // 3. Inisialisasi manajer sinkronisasi outbox & jadwalkan loop (SYN-03, SYN-05, F43)
        val syncApiService = SyncApiServiceFactory.create(
            appConfig = CafeAppConfig,
            deviceCredentialProvider = { deviceManager.cachedCredential },
            authTokenProvider = { deviceManager.cachedAuthToken }
        )
        val syncSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = syncApiService,
            appId = CafeAppConfig.appId.value,
            appVersion = CafeAppConfig.appVersion
        )
        val outboxSyncManager = OutboxSyncManager(
            syncSender = syncSender,
            outboxDao = database.outboxDao(),
            scope = lifecycleScope
        )
        OutboxSyncWorker.syncManager = outboxSyncManager
        OutboxSyncWorker.schedulePeriodic(this, intervalMinutes = 15)

        lifecycleScope.launch {
            deviceManager.initialize()
            if (!deviceManager.isDemoMode()) {
                if (deviceManager.deviceStatus.value == DeviceStatus.ACTIVE || deviceManager.deviceStatus.value == DeviceStatus.PENDING) {
                    deviceManager.checkStatus()
                    if (deviceManager.deviceStatus.value == DeviceStatus.ACTIVE) {
                        DeviceHeartbeatWorker.enqueueImmediate(this@MainActivity)
                    }
                }
            }
            deviceManager.deviceStatus.collect { status ->
                if (status == DeviceStatus.ACTIVE && !deviceManager.isDemoMode()) {
                    outboxSyncManager.startForegroundSyncLoop()
                } else {
                    outboxSyncManager.stopForegroundSyncLoop()
                }
            }
        }

        setContent {
            CafeTheme {
                val coroutineScope = rememberCoroutineScope()
                val deviceStatus by deviceManager.deviceStatus.collectAsState()
                val printerStatus by printerService.status.collectAsState()
                val pendingSyncCount by database.outboxDao().observePendingCount().collectAsState(initial = 0)
                val failedSyncCount by database.outboxDao().observeFailedCount().collectAsState(initial = 0)

                val syncStatus = when {
                    failedSyncCount > 0 -> SyncStatus.ERROR
                    pendingSyncCount > 0 -> SyncStatus.PENDING
                    else -> SyncStatus.SYNCED
                }

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
                    var deviceNameInput by remember { mutableStateOf(CafeAppConfig.appName) }
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
                        businessUnitName = CafeAppConfig.appName,
                        onDebugBypassClick = {
                            coroutineScope.launch {
                                if (deviceStatus == DeviceStatus.REVOKED) {
                                    deviceManager.resetDeviceForDebug()
                                } else {
                                    deviceManager.activateForDebug(CafeAppConfig.appName)
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

                    val cafeNavItems = remember(isShiftActive) {
                        listOf(
                            NavItem(CafeDestinations.KASIR, "Kasir", Icons.Outlined.PointOfSale, enabled = isShiftActive),
                            NavItem(CafeDestinations.PESANAN, "Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong, enabled = isShiftActive),
                            NavItem(CafeDestinations.HABIS, "Menu Habis", Icons.Outlined.Block, enabled = isShiftActive),
                            NavItem(CafeDestinations.KAS_KELUAR, "Kas", Icons.Outlined.Payments, enabled = isShiftActive),
                            NavItem(CafeDestinations.SHIFT, "Shift", Icons.Outlined.AccessTime, enabled = true)
                        )
                    }

                    val defaultStartRoute = if (isShiftActive) CafeDestinations.KASIR else CafeDestinations.SHIFT
                    val currentRoute = navBackStackEntry?.destination?.route ?: defaultStartRoute
                    val isAdminSelected = currentRoute == CafeDestinations.ADMIN

                    // Auto-redirect ke layar SHIFT jika shift belum dibuka atau sudah ditutup
                    LaunchedEffect(isShiftActive) {
                        if (!isShiftActive && currentRoute != CafeDestinations.SHIFT && currentRoute != CafeDestinations.ADMIN) {
                            navController.navigate(CafeDestinations.SHIFT) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    }

                    val selectedIndex = remember(currentRoute, cafeNavItems) {
                        cafeNavItems.indexOfFirst { it.id == currentRoute }
                    }

                    val isFocusFlowActive = remember(currentRoute) {
                        currentRoute == CafeDestinations.PEMBAYARAN
                    }

                    AppShell(
                        businessName = "AA COFFEESHOP",
                        monogramText = "AA",
                        cashierName = activeShift?.cashierName ?: "Kasir",
                        shiftInfo = if (activeShift != null) null else "Belum Buka Shift",
                        syncStatus = syncStatus,
                        pendingSyncCount = pendingSyncCount,
                        isPrinterConnected = printerStatus is PrinterStatus.Connected,
                        isPrinterConnecting = printerStatus is PrinterStatus.Connecting,
                        onPrinterClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                printerService.checkConnection()
                            }
                        },
                        onAdminClick = {
                            if (currentRoute != CafeDestinations.ADMIN) {
                                navController.navigate(CafeDestinations.ADMIN) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        isAdminSelected = isAdminSelected,
                        navItems = cafeNavItems,
                        selectedNavIndex = selectedIndex,
                        onNavItemSelected = { index ->
                            val item = cafeNavItems[index]
                            if (item.enabled && (isShiftActive || item.id == CafeDestinations.SHIFT)) {
                                if (currentRoute == CafeDestinations.ADMIN) {
                                    navController.popBackStack(CafeDestinations.ADMIN, inclusive = true)
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
                        CafeNavHost(
                            navController = navController,
                            paddingValues = paddingValues,
                            printerService = printerService,
                            shiftViewModel = shiftViewModel,
                            startDestination = defaultStartRoute,
                            deviceId = registeredDeviceId.ifBlank { "dev-cafe-1" }
                        )
                    }
                }
            }
        }
    }
}
