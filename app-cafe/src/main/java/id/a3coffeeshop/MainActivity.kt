package id.a3coffeeshop

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.a3.core.auth.AuthManagerFactory
import id.a3.core.auth.AuthResult
import id.a3.core.auth.DeviceCheckResult
import id.a3.core.auth.DeviceManager
import id.a3.core.auth.DeviceManagerFactory
import id.a3.core.auth.DeviceRegisterResult
import id.a3.core.auth.model.UserDto
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.security.DefaultManagerApprover
import id.a3.core.data.security.ManagerApproverProvider
import id.a3.core.data.local.entity.SyncStateEntity
import id.a3.core.domain.device.DeviceStatus
import id.a3.core.domain.monitoring.AppCrashManager
import id.a3.core.printer.PrinterStatus
import id.a3.core.printer.connection.DefaultPrinterService
import id.a3.core.sync.BootstrapResult
import id.a3.core.sync.DeltaResult
import id.a3.core.sync.DeviceHeartbeatManager
import id.a3.core.sync.DeviceHeartbeatWorker
import id.a3.core.sync.MasterDataSyncManager
import id.a3.core.sync.OutboxSyncManager
import id.a3.core.sync.OutboxSyncWorker
import id.a3.core.sync.SyncSender
import id.a3.core.sync.api.SyncApiServiceFactory
import androidx.compose.ui.text.font.FontWeight
import id.a3.core.ui.component.A3Dialog
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.CashierAccountItem
import id.a3.core.ui.component.CashierLoginDialog
import id.a3.core.ui.component.DeviceRegistrationScreen
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.component.SyncStatusDialog
import id.a3.core.ui.theme.A3Theme
import id.a3.core.ui.theme.CafeTheme
import id.a3.feature.shift.ShiftViewModelFactory
import id.a3.feature.shift.presentation.ShiftViewModel
import id.a3coffeeshop.navigation.CafeDestinations
import id.a3coffeeshop.navigation.CafeNavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

        // 3. Inisialisasi manajer sinkronisasi outbox & master data (SYN-03, SYN-05, F12, F43)
        val authManager = AuthManagerFactory.getOrCreate(this, CafeAppConfig)

        val credentialProvider: () -> String? = {
            deviceManager.cachedCredential ?: runCatching {
                runBlocking { deviceManager.getDeviceCredential() }
            }.getOrNull()
        }

        val tokenProvider: () -> String? = {
            runCatching {
                runBlocking { authManager.getAccessToken() }
            }.getOrNull()
        }

        val syncApiService = SyncApiServiceFactory.create(
            appConfig = CafeAppConfig,
            deviceCredentialProvider = credentialProvider,
            authTokenProvider = tokenProvider,
            authManager = authManager
        )
        val syncSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = syncApiService,
            appId = CafeAppConfig.appId.value,
            appVersion = CafeAppConfig.appVersion,
            currentDeviceIdProvider = { deviceManager.cachedDeviceId ?: runBlocking { deviceManager.getDeviceId() } },
            isDemoModeProvider = { deviceManager.isDemoMode() },
            currentShiftIdProvider = { runBlocking { database.shiftDao().getActiveShift()?.id ?: database.shiftDao().getLatestShift()?.id } },
            currentDeviceCodeProvider = { deviceManager.cachedDeviceCode ?: runBlocking { deviceManager.getDeviceCode() } },
            onDeviceCodeLearned = { code -> deviceManager.saveDeviceCode(code) },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao(),
            syncStateDao = database.syncStateDao(),
            cashOutDao = database.cashOutDao(),
            filesDirProvider = { filesDir },
            shiftDao = database.shiftDao(),
            customerDao = database.customerDao()
        )
        val outboxSyncManager = OutboxSyncManager(
            syncSender = syncSender,
            outboxDao = database.outboxDao(),
            scope = lifecycleScope
        )
        OutboxSyncWorker.syncManager = outboxSyncManager
        OutboxSyncWorker.schedulePeriodic(this, intervalMinutes = 15)

        val masterApiService = SyncApiServiceFactory.createMasterApiService(
            appConfig = CafeAppConfig,
            deviceCredentialProvider = credentialProvider,
            authTokenProvider = tokenProvider,
            authManager = authManager
        )
        val masterDataSyncManager = MasterDataSyncManager(
            syncStateDao = database.syncStateDao(),
            masterDataDao = database.masterDataDao(),
            apiService = masterApiService,
            appId = CafeAppConfig.appId.value,
            appVersion = CafeAppConfig.appVersion,
            userDao = database.userDao()
        )

        // Persetujuan PIN manager memakai akun dari server (tidak ada PIN bawaan).
        ManagerApproverProvider.install(
            DefaultManagerApprover(database.userDao(), { deviceManager.getDeviceCredential() })
        )

        var masterDataSyncJob: Job? = null

        lifecycleScope.launch {
            deviceManager.initialize()
            if (!deviceManager.isDemoMode()) {
                if (deviceManager.deviceStatus.value == DeviceStatus.PENDING) {
                    deviceManager.checkStatus()
                }
                if (deviceManager.deviceStatus.value == DeviceStatus.ACTIVE) {
                    DeviceHeartbeatWorker.enqueueImmediate(this@MainActivity)
                }
            }
            deviceManager.deviceStatus.collect { status ->
                if (status == DeviceStatus.ACTIVE && !deviceManager.isDemoMode()) {
                    outboxSyncManager.startForegroundSyncLoop()
                    masterDataSyncJob?.cancel()
                    masterDataSyncJob = launch(Dispatchers.IO) {
                        while (isActive) {
                            try {
                                val token = authManager.getAccessToken()
                                if (!token.isNullOrBlank()) {
                                    masterDataSyncManager.syncConfig()
                                    val isDone = database.syncStateDao().get(MasterDataSyncManager.KEY_BOOTSTRAP_DONE) == "true"
                                    val optionsCount = database.masterDataDao().observeAllActiveMenuOptions().first().size
                                    if (!isDone || optionsCount == 0) {
                                        if (optionsCount == 0) {
                                            database.syncStateDao().delete(MasterDataSyncManager.KEY_BOOTSTRAP_CURSOR)
                                        }
                                        val bootResult = masterDataSyncManager.syncBootstrap()
                                        if (bootResult is BootstrapResult.Interrupted && bootResult.error.contains("401")) {
                                            val refreshed = authManager.refreshToken(deviceManager.getDeviceCredential())
                                            if (!refreshed) {
                                                authManager.logout()
                                            }
                                        }
                                    } else {
                                        val deltaResult = masterDataSyncManager.syncDelta()
                                        if (deltaResult is DeltaResult.Error && deltaResult.code == 401) {
                                            val refreshed = authManager.refreshToken(deviceManager.getDeviceCredential())
                                            if (!refreshed) {
                                                authManager.logout()
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("MasterDataSync", "Master data sync error: ${e.message}")
                            }
                            delay(30_000L) // Polling pembaruan menu web setiap 30 detik
                        }
                    }
                } else {
                    outboxSyncManager.stopForegroundSyncLoop()
                    masterDataSyncJob?.cancel()
                    masterDataSyncJob = null
                }
            }
        }

        setContent {
            CafeTheme {
                val coroutineScope = rememberCoroutineScope()
                val isDeviceInitialized by deviceManager.isInitialized.collectAsState()
                val deviceStatus by deviceManager.deviceStatus.collectAsState()
                val printerStatus by printerService.status.collectAsState()
                val pendingSyncCount by database.outboxDao().observePendingCount().collectAsState(initial = 0)
                val failedSyncCount by database.outboxDao().observeFailedCount().collectAsState(initial = 0)
                val lastFailedError by database.outboxDao().observeLastFailedError().collectAsState(initial = null)
                val isSyncing by outboxSyncManager.isSyncing.collectAsState()
                var showSyncDialog by remember { mutableStateOf(false) }

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

                if (!isDeviceInitialized) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(A3Theme.colors.canvas),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = A3Theme.colors.accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else if (deviceStatus != DeviceStatus.ACTIVE) {
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
                                registeredDeviceId = deviceManager.getDeviceId()
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
                        onDebugBypassClick = if (BuildConfig.DEBUG) {
                            {
                                coroutineScope.launch {
                                    if (deviceStatus == DeviceStatus.REVOKED) {
                                        deviceManager.resetDeviceForDebug()
                                    } else {
                                        deviceManager.activateForDebug(CafeAppConfig.appName)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onRegenerateDeviceId = {
                            coroutineScope.launch {
                                registeredDeviceId = deviceManager.regenerateDeviceId()
                            }
                        }
                    )
                } else {
                    val authManager = remember { AuthManagerFactory.getOrCreate(this@MainActivity, CafeAppConfig) }
                    val isUnlocked by authManager.isUnlocked.collectAsState()
                    var currentUser by remember { mutableStateOf<UserDto?>(null) }
                    var accountsList by remember { mutableStateOf<List<CashierAccountItem>>(emptyList()) }
                    var isLoginLoading by remember { mutableStateOf(false) }
                    var loginErrorMessage by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(deviceStatus, isUnlocked) {
                        if (deviceStatus == DeviceStatus.ACTIVE && !deviceManager.isDemoMode()) {
                            authManager.initialize()
                            val cachedUser = authManager.getCurrentUser()
                            currentUser = cachedUser
                            if (!authManager.isUnlocked.value) {
                                isLoginLoading = true
                                val accs = authManager.getAccounts(deviceManager.getDeviceCredential())
                                accountsList = if (accs.isNotEmpty()) {
                                    accs.map { CashierAccountItem(it.id, it.name, it.role) }
                                } else if (cachedUser != null) {
                                    listOf(CashierAccountItem(cachedUser.id, cachedUser.name, cachedUser.role))
                                } else {
                                    listOf(CashierAccountItem("2", "Kasir", "kasir"))
                                }
                                isLoginLoading = false
                            }
                        }
                    }

                    if (!isUnlocked && !deviceManager.isDemoMode()) {
                        CashierLoginDialog(
                            accounts = accountsList,
                            isLoading = isLoginLoading,
                            errorMessage = loginErrorMessage,
                            onLoginClick = { userId, pin ->
                                coroutineScope.launch {
                                    isLoginLoading = true
                                    loginErrorMessage = null
                                    val result = authManager.loginWithPinOnline(
                                        userId = userId,
                                        pin = pin,
                                        deviceCredential = deviceManager.getDeviceCredential()
                                    )
                                    isLoginLoading = false
                                    if (result is AuthResult.Success) {
                                        currentUser = result.user
                                        deviceManager.initialize()
                                        launch(Dispatchers.IO) {
                                            try {
                                                database.syncStateDao().delete(MasterDataSyncManager.KEY_BOOTSTRAP_CURSOR)
                                                masterDataSyncManager.syncBootstrap()
                                            } catch (e: Exception) {
                                                Log.w("MasterDataSync", "Bootstrap error: ${e.message}")
                                            }
                                        }
                                    } else if (result is AuthResult.Error && (result.code == "NETWORK_ERROR" || result.code.startsWith("HTTP_5") || result.code == "HTTP_408")) {
                                        // Mode Offline: verifikasi PIN lokal tanpa koneksi internet
                                        val isOfflineValid = authManager.unlockWithPinOffline(pin)
                                        if (isOfflineValid) {
                                            currentUser = authManager.getCurrentUser() ?: accountsList.firstOrNull { it.id == userId }?.let {
                                                UserDto(id = it.id, name = it.name, role = it.role)
                                            } ?: UserDto(id = userId, name = "Kasir", role = "kasir")
                                        } else {
                                            loginErrorMessage = "Mode Offline: PIN tidak cocok dengan data tersimpan di tablet"
                                        }
                                    } else if (result is AuthResult.Error) {
                                        loginErrorMessage = result.message
                                    } else if (result is AuthResult.AccountLocked) {
                                        loginErrorMessage = result.message
                                    }
                                }
                            }
                        )
                    }

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()

                    var showProfileDialog by remember { mutableStateOf(false) }

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
                        cashierName = currentUser?.name ?: activeShift?.cashierName ?: "Kasir",
                        shiftInfo = if (activeShift != null) "Shift Aktif" else "Belum Buka Shift",
                        syncStatus = syncStatus,
                        pendingSyncCount = pendingSyncCount,
                        failedSyncCount = failedSyncCount,
                        onSyncClick = { showSyncDialog = true },
                        onProfileClick = { showProfileDialog = true },
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
                            deviceId = registeredDeviceId.ifBlank { "dev-cafe-1" },
                            deviceCodeProvider = { deviceManager.cachedDeviceCode ?: runBlocking { deviceManager.getDeviceCode() } },
                            cashierName = currentUser?.name ?: activeShift?.cashierName ?: "Kasir",
                            cashierId = currentUser?.id ?: activeShift?.cashierId ?: "2"
                        )
                    }

                    SyncStatusDialog(
                        isOpen = showSyncDialog,
                        syncStatus = syncStatus,
                        pendingCount = pendingSyncCount,
                        failedCount = failedSyncCount,
                        lastError = lastFailedError,
                        isSyncing = isSyncing,
                        onDismiss = { showSyncDialog = false },
                        onRetryClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val currentDevId = deviceManager.getDeviceId()
                                val fallbackShiftId = database.shiftDao().getActiveShift()?.id ?: database.shiftDao().getLatestShift()?.id

                                // 1. Tarik konfigurasi terbaru dari server
                                try {
                                    masterDataSyncManager.syncConfig()
                                } catch (_: Exception) {}

                                // 2. Jika ada error format kode perangkat (misal C8-NNNN), simpan kode tersebut ke DeviceManager
                                lastFailedError?.let { err ->
                                    Regex("""display_number harus berformat\s+([A-Za-z0-9]+)-""", RegexOption.IGNORE_CASE)
                                        .find(err)?.groupValues?.getOrNull(1)?.let { extractedCode ->
                                            deviceManager.saveDeviceCode(extractedCode)
                                        }

                                    // Jika terdapat penolakan total mismatch, inferensikan pajak ke sync_state
                                    if (err.contains("total hitung", ignoreCase = true) || err.contains("TOTAL_MISMATCH", ignoreCase = true)) {
                                        val serverTotal = Regex("""(?:total\s+hitung(?:\s+ulang)?|subtotal\s+hitung(?:\s+ulang)?|total\s+mismatch)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                                            .find(err)?.groupValues?.getOrNull(1)?.toLongOrNull()
                                        val receivedTotal = Regex("""diterima\s+(\d+)""", RegexOption.IGNORE_CASE)
                                            .find(err)?.groupValues?.getOrNull(1)?.toLongOrNull()
                                        if (serverTotal != null && receivedTotal != null && receivedTotal > 0L && serverTotal > receivedTotal) {
                                            val inferredPercent = (((serverTotal - receivedTotal) * 100L) / receivedTotal).toInt()
                                            if (inferredPercent in 1..99) {
                                                database.syncStateDao().set(SyncStateEntity(key = "tax_percent", value = inferredPercent.toString()))
                                            }
                                        }
                                    }
                                }

                                database.outboxDao().retryAllFailedWithDeviceId(currentDevId, fallbackShiftId)
                                outboxSyncManager.triggerSync()
                            }
                        },
                        onSyncNowClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val currentDevId = deviceManager.getDeviceId()
                                val fallbackShiftId = database.shiftDao().getActiveShift()?.id

                                try {
                                    masterDataSyncManager.syncConfig()
                                } catch (_: Exception) {}

                                // Selaraskan deviceId dan shiftId pada semua antrean outbox yang belum tersinkron
                                if (currentDevId.isNotBlank()) {
                                    database.outboxDao().updateAllUnsyncedDeviceId(currentDevId)
                                }
                                if (!fallbackShiftId.isNullOrBlank()) {
                                    database.outboxDao().updateMissingShiftId(fallbackShiftId)
                                }

                                database.outboxDao().retryAllFailedWithDeviceId(currentDevId, fallbackShiftId)

                                outboxSyncManager.triggerSync()
                            }
                        }
                    )

                    if (showProfileDialog) {
                        val roleDisplay = when (currentUser?.role?.lowercase()) {
                            "kasir" -> "Kasir"
                            "manager" -> "Manager"
                            "owner" -> "Owner"
                            else -> currentUser?.role ?: "Pengguna"
                        }
                        val cashierDisplayName = currentUser?.name ?: activeShift?.cashierName ?: "Kasir"
                        A3Dialog(
                            onDismissRequest = { showProfileDialog = false },
                            title = "Profil Pengguna",
                            body = @Composable {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Nama: $cashierDisplayName",
                                        style = A3Theme.typography.body,
                                        color = A3Theme.colors.ink,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Peran: $roleDisplay",
                                        style = A3Theme.typography.body,
                                        color = A3Theme.colors.inkMuted
                                    )
                                    Text(
                                        text = if (activeShift != null) "Status Shift: Aktif" else "Status Shift: Belum Buka Shift",
                                        style = A3Theme.typography.bodySmall,
                                        color = if (activeShift != null) A3Theme.colors.status.success.text else A3Theme.colors.inkMuted
                                    )
                                }
                            },
                            confirmLabel = "Ganti Akun / Keluar",
                            onConfirm = {
                                coroutineScope.launch {
                                    showProfileDialog = false
                                    authManager.logout()
                                }
                            },
                            dismissLabel = "Tutup",
                            onDismiss = { showProfileDialog = false }
                        )
                    }
                }
            }
        }
    }
}
