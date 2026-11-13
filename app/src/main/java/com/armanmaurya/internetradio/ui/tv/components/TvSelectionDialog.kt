package com.armanmaurya.internetradio.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

class TvSelectionOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val trailing: (@Composable () -> Unit)? = null
)

/**
 * Single-choice dialog built from TV list items: radio on the left, optional trailing content
 * (e.g. a sort-direction arrow). Focus starts on the selected option; choosing one closes it.
 */
@Composable
fun TvSelectionDialog(
    title: String,
    options: List<TvSelectionOption>,
    onDismiss: () -> Unit
) {
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    val selectedFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            selectedFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(420.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                )
                options.forEachIndexed { index, option ->
                    ListItem(
                        selected = false,
                        onClick = {
                            option.onClick()
                            onDismiss()
                        },
                        headlineContent = { Text(option.label) },
                        leadingContent = { RadioButton(selected = option.selected, onClick = null) },
                        trailingContent = option.trailing,
                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier)
                    )
                }
            }
        }
    }
}
