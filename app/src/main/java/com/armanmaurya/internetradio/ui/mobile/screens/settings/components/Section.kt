package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Section(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().then(
                if (isPureBlack) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    MaterialTheme.shapes.medium
                ) else Modifier
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}