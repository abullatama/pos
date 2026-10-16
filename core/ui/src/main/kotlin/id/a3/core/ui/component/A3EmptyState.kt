package id.a3.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

/**
 * Keadaan kosong yang memberi tahu penyebab dan tindakan berikutnya.
 */
@Composable
fun A3EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = A3Theme.colors.inkMuted,
            modifier = Modifier.height(48.dp)
        )
    }
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon?.invoke()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = A3Theme.colors.ink,
            style = A3Theme.typography.title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = A3Theme.colors.inkMuted,
            style = A3Theme.typography.body,
            textAlign = TextAlign.Center
        )
        action?.let {
            Spacer(modifier = Modifier.height(24.dp))
            it()
        }
    }
}
