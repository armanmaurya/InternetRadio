package com.armanmaurya.internetradio.ui.screens.added

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStationBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, favicon: String, tags: List<String>, country: String, language: String) -> Unit,
    sheetState: SheetState
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var favicon by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    val fieldShape = RoundedCornerShape(16.dp)
    val textFieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp + bottomInset)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.dialog_add_station_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.dialog_add_station_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = fieldShape,
                colors = textFieldColors
            )

            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(text = stringResource(R.string.dialog_add_station_url_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                shape = fieldShape,
                colors = textFieldColors
            )

            TextField(
                value = favicon,
                onValueChange = { favicon = it },
                label = { Text(text = stringResource(R.string.dialog_add_station_thumbnail_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                shape = fieldShape,
                colors = textFieldColors
            )

            TextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(text = "Tags (comma separated)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                shape = fieldShape,
                colors = textFieldColors
            )

            TextField(
                value = country,
                onValueChange = { country = it },
                label = { Text(text = "Country") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                shape = fieldShape,
                colors = textFieldColors
            )

            TextField(
                value = language,
                onValueChange = { language = it },
                label = { Text(text = "Language") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                shape = fieldShape,
                colors = textFieldColors
            )

            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        val tagsList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onConfirm(name, url, favicon, tagsList, country, language)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = fieldShape
            ) {
                Text(text = stringResource(R.string.dialog_add_station_add))
            }
        }
    }
}
