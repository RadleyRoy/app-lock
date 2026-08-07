package com.radley.applock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.radley.applock.ui.theme.Slate

/**
 * Renders a package's launcher icon.
 *
 * The Drawable is converted once per package and cached by [remember], because
 * `toBitmap()` rasterises an adaptive icon every time it is called — doing that during a
 * scrolling list recomposition drops frames noticeably.
 */
@Composable
fun AppIcon(
    packageName: String,
    iconProvider: (String) -> android.graphics.drawable.Drawable?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(packageName) {
        runCatching { iconProvider(packageName)?.toBitmap()?.asImageBitmap() }.getOrNull()
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    }
}
