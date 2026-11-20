package com.armanmaurya.internetradio.ui.tv.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R

/**
 * Search row for the TV import and export lists. It is a normal row until OK is pressed, then
 * turns into a text field for the on-screen keyboard; Back, the keyboard's Search key or moving
 * away returns it to a row that shows the query. Same pattern as the search in the home drawer.
 */
@Composable
internal fun TvSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }

    if (isEditing) {
        val fieldFocus = remember { FocusRequester() }
        var hasGainedFocus by remember { mutableStateOf(false) }
        /** [refocus] puts focus back on the row; not when the user moved focus somewhere else. */
        fun stopEditing(refocus: Boolean) {
            returnFocus = refocus
            isEditing = false
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.general_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { stopEditing(refocus = true) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.border,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .focusRequester(fieldFocus)
                .onFocusChanged { state ->
                    if (state.isFocused) hasGainedFocus = true
                    else if (hasGainedFocus && isEditing) stopEditing(refocus = false)
                }
        )
        LaunchedEffect(Unit) {
            try {
                fieldFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
        BackHandler { stopEditing(refocus = true) }
    } else {
        ListItem(
            selected = false,
            onClick = { isEditing = true },
            headlineContent = { Text(query.ifEmpty { stringResource(R.string.general_search) }, maxLines = 1) },
            leadingContent = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
            scale = ListItemDefaults.scale(focusedScale = 1.02f),
            modifier = modifier.fillMaxWidth().focusRequester(rowFocus)
        )
    }

    // Closing the keyboard puts focus back on the row, not wherever the list happens to be.
    LaunchedEffect(isEditing, returnFocus) {
        if (!isEditing && returnFocus) {
            returnFocus = false
            try {
                rowFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
}
