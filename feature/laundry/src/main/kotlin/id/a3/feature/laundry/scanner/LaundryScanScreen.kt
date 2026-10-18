package id.a3.feature.laundry.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Card
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.laundry.presentation.LaundryScanUiState
import id.a3.feature.laundry.presentation.LaundryScanViewModel
import java.util.concurrent.Executors

/**
 * Layar L07: Scan Tag Cucian & Pembaruan Status (Pola Penuh Fokus).
 *
 * Mengarahkan kamera tablet ke QR code nota tag thermal, atau menerima input manual nomor nota (LDR-08).
 * Menampilkan detail order, status cucian saat ini, dan tombol aksi transisi status satu arah:
 * - Diterima -> Diproses
 * - Diproses -> Siap
 * Serta tombol cetak tag cepat ke printer Bluetooth.
 */
@Composable
fun LaundryScanScreen(
    viewModel: LaundryScanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                A3Card(modifier = Modifier.fillMaxWidth(0.55f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = "Akses Kamera Diperlukan",
                            color = colors.ink,
                            style = A3Theme.typography.title,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aplikasi membutuhkan izin kamera untuk memindai kode QR pada nota tag pakaian pelanggan Athia Laundry House.",
                            color = colors.inkMuted,
                            style = A3Theme.typography.body,
                            textAlign = TextAlign.Center
                        )
                        A3Button(
                            label = "Berikan Izin Kamera",
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            fillWidth = true
                        )
                    }
                }
            }
        } else {
            // 1. Kamera Viewfinder (CameraX + ZXing)
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { resultText ->
                                    // Pindai hanya jika belum ada order aktif yang sedang ditampilkan
                                    if (uiState.scannedOrder == null && !uiState.isSearching) {
                                        viewModel.onScanResult(resultText)
                                    }
                                })
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                (ctx as androidx.lifecycle.LifecycleOwner),
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(Unit) {
                onDispose {
                    cameraExecutor.shutdown()
                }
            }

            // 2. Bingkai Pembidik QR Membulat (Velvet Rose Athia)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(3.dp, colors.accent, RoundedCornerShape(24.dp))
                )
            }

            // 3. Bilah Pencarian Manual di Atas (Fallback bila kertas sobek/pudar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(460.dp)
                        .clip(shapes.buttonShape)
                        .background(colors.surface.copy(alpha = 0.95f))
                        .border(1.dp, colors.border, shapes.buttonShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        A3TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = "Ketik nomor nota (cth. L1-0001)...",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        A3Button(
                            label = "Cari",
                            variant = ButtonVariant.PRIMARY,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.searchByManualQuery()
                            },
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }

            // 4. Panel Hasil Pindai / Detail Cucian
            AnimatedVisibility(
                visible = uiState.scannedOrder != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
            ) {
                uiState.scannedOrder?.let { orderWithLines ->
                    ScannedOrderCard(
                        orderWithLines = orderWithLines,
                        uiState = uiState,
                        onAdvanceStatus = viewModel::advanceStatus,
                        onPrintTag = viewModel::printLaundryTag,
                        onClear = viewModel::clearScannedOrder
                    )
                }
            }

            // 5. Pesan Notifikasi Sukses / Error
            if (uiState.statusMessage != null || uiState.errorMessage != null) {
                val isSuccess = uiState.statusMessage != null
                val messageText = uiState.statusMessage ?: uiState.errorMessage.orEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(shapes.buttonShape)
                            .background(if (isSuccess) colors.status.success.background else colors.status.danger.background)
                            .border(1.dp, if (isSuccess) colors.status.success.text else colors.status.danger.text, shapes.buttonShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSuccess) colors.status.success.text else colors.status.danger.text,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = messageText,
                            style = A3Theme.typography.body,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSuccess) colors.status.success.text else colors.status.danger.text
                        )
                        IconButton(
                            onClick = viewModel::dismissMessage,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = if (isSuccess) colors.status.success.text else colors.status.danger.text,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedOrderCard(
    orderWithLines: OrderWithLines,
    uiState: LaundryScanUiState,
    onAdvanceStatus: () -> Unit,
    onPrintTag: () -> Unit,
    onClear: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val order = orderWithLines.order
    val customer = uiState.customer

    val (statusLabel, statusToken) = when (order.status.lowercase()) {
        "open", "received" -> "Diterima (Antrean)" to StatusToken.INFO
        "processing" -> "Sedang Diproses / Cuci" to StatusToken.WARNING
        "ready" -> "Siap Diambil di Rak" to StatusToken.SUCCESS
        "picked_up" -> "Sudah Diambil Pelanggan" to StatusToken.NEUTRAL
        else -> order.status.uppercase() to StatusToken.INFO
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(colors.surface)
            .border(1.5.dp, colors.accent, shapes.cardShape)
            .padding(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Display Number + Status Chip + Tombol Tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = order.displayNumber,
                        style = A3Theme.typography.headline,
                        color = colors.ink,
                        fontWeight = FontWeight.Bold
                    )
                    A3StatusChip(label = statusLabel, token = statusToken)
                }

                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = colors.inkMuted
                    )
                }
            }

            // Info Pelanggan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                        color = colors.accent,
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = customer?.name ?: "Pelanggan Athia",
                        style = A3Theme.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    customer?.phone?.let { phone ->
                        Text(
                            text = PhoneNumberUtil.formatDisplay(phone),
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            // Rincian Pakaian yang Sedang Dicuci
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.cardShape)
                    .background(colors.canvas)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Rincian Pakaian:",
                    style = A3Theme.typography.label,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold
                )
                orderWithLines.lines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• ${line.nameSnapshot}",
                            style = A3Theme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink
                        )
                        Text(
                            text = "${line.quantity} item",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                    if (!line.note.isNullOrBlank()) {
                        Text(
                            text = "  Catatan: ${line.note}",
                            style = A3Theme.typography.bodySmall,
                            color = colors.status.warning.text,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Tombol Aksi Transisi Status & Cetak Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                A3Button(
                    label = "Cetak Tag",
                    variant = ButtonVariant.SECONDARY,
                    onClick = onPrintTag,
                    modifier = Modifier.weight(1f)
                )

                if (uiState.canAdvance) {
                    A3Button(
                        label = uiState.nextStatusLabel,
                        variant = ButtonVariant.PRIMARY,
                        enabled = !uiState.isActionLoading,
                        onClick = onAdvanceStatus,
                        modifier = Modifier.weight(2f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(56.dp)
                            .clip(shapes.buttonShape)
                            .background(colors.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (order.status.lowercase() == "ready") "Cucian Siap Diambil" else "Pesanan Selesai",
                            style = A3Theme.typography.button,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
