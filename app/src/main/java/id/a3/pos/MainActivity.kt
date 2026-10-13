package id.a3.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import id.a3.pos.ui.theme.A3Theme

import androidx.compose.runtime.remember
import id.a3.pos.printer.PrinterManager
import id.a3.pos.printer.PrinterSpikeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            A3Theme {
                val printerManager = remember { PrinterManager(applicationContext) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PrinterSpikeScreen(
                        printerManager = printerManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}