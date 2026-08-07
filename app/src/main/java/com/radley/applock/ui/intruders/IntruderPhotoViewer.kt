package com.radley.applock.ui.intruders

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radley.applock.data.IntruderEvent
import com.radley.applock.data.PhotoExporter
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Bone
import com.radley.applock.ui.theme.Clay
import com.radley.applock.ui.theme.Ember
import com.radley.applock.ui.theme.Ink
import com.radley.applock.ui.theme.Slate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Full-screen view of a single capture, with the one export route out of app-private storage.
 */
@Composable
fun IntruderPhotoViewer(
    event: IntruderEvent,
    photoFile: (String) -> File,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Decoded at full size here, unlike the grid thumbnails — this is the one place the photo
    // is meant to be looked at properly.
    val bitmap = remember(event.id) {
        runCatching {
            val file = photoFile(event.photoFileName)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        }.getOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Ash)
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Photo taken after ${event.failedAttempts} wrong attempts",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    )
                } else {
                    Text(
                        "This photo file is missing.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(event.targetLabel, style = MaterialTheme.typography.titleLarge, color = Bone)
                Text(
                    text = "${formatFull(event.timestampMillis)} · ${event.failedAttempts} wrong attempts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            val result = PhotoExporter(context).saveToGallery(
                                source = photoFile(event.photoFileName),
                                displayName = "applock_${event.timestampMillis}.jpg",
                            )
                            status = when (result) {
                                is PhotoExporter.Result.Saved -> "Saved to ${result.location}"
                                is PhotoExporter.Result.Failed -> "Not saved — ${result.reason}"
                            }
                            saving = false
                        }
                    },
                    enabled = bitmap != null && !saving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Clay,
                        contentColor = Bone,
                        disabledContainerColor = Slate.copy(alpha = 0.25f),
                        disabledContentColor = Slate,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (saving) "Saving…" else "Save to Gallery")
                }

                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.startsWith("Saved")) Ash else Ember,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))

                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete this photo", color = Ember)
                }
            }
        }
    }
}

private fun formatFull(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
