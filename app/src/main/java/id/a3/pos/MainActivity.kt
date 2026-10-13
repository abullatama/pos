package id.a3.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.a3.pos.kiosk.KioskSpikeScreen
import id.a3.pos.printer.PrinterManager
import id.a3.pos.printer.PrinterSpikeScreen
import id.a3.pos.scanner.QrScannerSpikeScreen
import id.a3.pos.sync.SyncSpikeScreen
import id.a3.pos.ui.theme.A3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            A3Theme {
                val printerManager = remember { PrinterManager(applicationContext) }
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("F01: Printer") },
                                icon = { Text("🖨️") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("F02: Sync") },
                                icon = { Text("🔄") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                label = { Text("F03: Kiosk") },
                                icon = { Text("🔒") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                label = { Text("F04: Scan QR") },
                                icon = { Text("📷") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> PrinterSpikeScreen(
                            printerManager = printerManager,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> SyncSpikeScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> KioskSpikeScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                        3 -> QrScannerSpikeScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
