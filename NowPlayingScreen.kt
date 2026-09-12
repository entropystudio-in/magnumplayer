package com.cadence.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cadence.player.data.Song
import com.cadence.player.playback.RepeatMode
import com.cadence.player.util.formatDuration

@Composable
fun NowPlayingScreen(
    song: Song,
    queue: List<Song>,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onQueueItemClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ExpandMore, contentDescription = "Close")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )

        val sliderValue = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { fraction ->
                if (durationMs > 0) onSeek((fraction * durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SkipSecondsButton(
                icon = Icons.Filled.Replay,
                label = "15",
                contentDescription = "Back 15 seconds",
                onClick = onSkipBackward
            )
            Box(modifier = Modifier.size(48.dp))
            SkipSecondsButton(
                icon = Icons.Filled.FastForward,
                label = "15",
                contentDescription = "Forward 15 seconds",
                onClick = onSkipForward
            )
        }

        Text(
            text = "Up next",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(queue, key = { it.id }) { queuedSong ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQueueItemClick(queuedSong) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = queuedSong.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (queuedSong.id == song.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = queuedSong.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatDuration(queuedSong.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipSecondsButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp))
        Text(
            text = "${label}s",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}
