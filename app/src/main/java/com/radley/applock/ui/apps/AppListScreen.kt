package com.radley.applock.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radley.applock.ui.components.AppIcon
import com.radley.applock.ui.main.AppListUiState
import com.radley.applock.ui.main.AppRow
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Bone
import com.radley.applock.ui.theme.Clay
import com.radley.applock.ui.theme.Cocoa
import com.radley.applock.ui.theme.Ink
import com.radley.applock.ui.theme.Slate
import com.radley.applock.ui.theme.Surface1

@Composable
fun AppListScreen(
    state: AppListUiState,
    query: String,
    showLockedOnly: Boolean,
    lockedCount: Int,
    iconProvider: (String) -> android.graphics.drawable.Drawable?,
    onQueryChange: (String) -> Unit,
    onShowLockedOnlyChange: (Boolean) -> Unit,
    onToggleLock: (String, Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search apps", color = Slate) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Ash) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Clay,
                unfocusedBorderColor = Surface1,
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1,
                cursorColor = Clay,
                focusedTextColor = Bone,
                unfocusedTextColor = Bone,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showLockedOnly,
                onClick = { onShowLockedOnlyChange(false) },
                label = { Text("All apps") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Cocoa,
                    selectedLabelColor = Bone,
                    containerColor = Surface1,
                    labelColor = Ash,
                ),
                border = null,
            )
            FilterChip(
                selected = showLockedOnly,
                onClick = { onShowLockedOnlyChange(true) },
                label = { Text(if (lockedCount > 0) "Locked · $lockedCount" else "Locked") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Cocoa,
                    selectedLabelColor = Bone,
                    containerColor = Surface1,
                    labelColor = Ash,
                ),
                border = null,
            )
        }

        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Clay)
            }
            return@Column
        }

        if (state.suggested.isEmpty() && state.others.isEmpty()) {
            EmptyState(
                title = if (showLockedOnly) "Nothing locked yet" else "No apps match",
                subtitle = if (showLockedOnly) {
                    "Switch to All apps and flip the toggle on anything you want protected."
                } else {
                    "Try a different search."
                },
            )
            return@Column
        }

        LazyColumn(contentPadding = contentPadding) {
            if (state.suggested.isNotEmpty()) {
                item { SectionHeader("Suggested", "Apps most people protect") }
                items(state.suggested, key = { it.app.packageName }) { row ->
                    AppRowItem(row, iconProvider, onToggleLock)
                }
            }

            if (state.others.isNotEmpty()) {
                item { SectionHeader("All apps", null) }
                items(state.others, key = { it.app.packageName }) { row ->
                    AppRowItem(row, iconProvider, onToggleLock)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Clay,
            fontWeight = FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Slate)
        }
    }
}

@Composable
private fun AppRowItem(
    row: AppRow,
    iconProvider: (String) -> android.graphics.drawable.Drawable?,
    onToggleLock: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = row.app.packageName, iconProvider = iconProvider)

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Bone,
                maxLines = 1,
            )
            Text(
                text = row.app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                maxLines = 1,
            )
        }

        Switch(
            checked = row.isLocked,
            onCheckedChange = { onToggleLock(row.app.packageName, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bone,
                checkedTrackColor = Clay,
                uncheckedThumbColor = Slate,
                uncheckedTrackColor = Surface1,
                uncheckedBorderColor = Slate,
            ),
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Bone)
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
