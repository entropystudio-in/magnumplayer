package com.cadence.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cadence.player.data.Song
import com.cadence.player.util.formatDuration

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
