package com.radley.applock.ui.intruders

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radley.applock.data.IntruderEvent
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Bone
import com.radley.applock.ui.theme.Ember
import com.radley.applock.ui.theme.Slate
import com.radley.applock.ui.theme.Surface1
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntruderLogScreen(
    events: List<IntruderEvent>,
    photoFile: (String) -> File,
    onDelete: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var pendingDelete by remember { mutableStateOf<IntruderEvent?>(null) }
    var viewing by remember { mutableStateOf<IntruderEvent?>(null) }

    viewing?.let { event ->
        IntruderPhotoViewer(
            event = event,
            photoFile = photoFile,
            onDismiss = { viewing = null },
            onDelete = {
                onDelete(event.id)
                viewing = null
            },
        )
    }

    if (events.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No intruders", style = MaterialTheme.typography.titleMedium, color = Bone)
            Spacer(Modifier.height(8.dp))
            Text(
                "If someone gets your PIN wrong too many times, their photo shows up here along with the app they were trying to open.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(events, key = { it.id }) { event ->
            IntruderCard(
                event = event,
                photoFile = photoFile,
                onClick = { viewing = event },
                onLongPress = { pendingDelete = event },
            )
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Surface1,
            title = { Text("Delete this photo?", color = Bone) },
            text = {
                Text(
                    "Removes the photo and its log entry permanently.",
                    color = Ash,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(event.id)
                    pendingDelete = null
                }) { Text("Delete", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = Ash) }
            },
        )
    }
}

@Composable
private fun IntruderCard(
    event: IntruderEvent,
    photoFile: (String) -> File,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Decoded once per event; the grid recomposes on scroll and decoding a full-resolution
    // JPEG each time would stutter badly.
    val bitmap = remember(event.id) {
        runCatching {
            val file = photoFile(event.photoFileName)
            if (!file.exists()) return@runCatching null
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Slate.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Photo taken after ${event.failedAttempts} wrong attempts",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("Photo missing", style = MaterialTheme.typography.labelSmall, color = Slate)
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = event.targetLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = Bone,
                maxLines = 1,
            )
            Text(
                text = formatTimestamp(event.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = Ash,
            )
            Text(
                text = "${event.failedAttempts} wrong attempts",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
