package id.a3.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

/**
 * Keadaan memuat yang menjelaskan apa yang sedang dimuat.
 */
@Composable
fun A3LoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = A3Theme.colors.accent
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = A3Theme.colors.inkMuted,
            style = A3Theme.typography.body,
            textAlign = TextAlign.Center
        )
    }
}
