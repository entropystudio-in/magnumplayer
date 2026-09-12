package com.cadence.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.player.data.Song
import com.cadence.player.ui.components.MiniPlayer
import com.cadence.player.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onMiniPlayerClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onThemeToggle: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onThemeToggle) {
                        Icon(Icons.Filled.Palette, contentDescription = "Change theme")
                    }
                }
            )
        },
        bottomBar = {
            if (currentSong != null) {
                MiniPlayer(
                    song = currentSong,
                    isPlaying = isPlaying,
                    onClick = onMiniPlayerClick,
                    onPlayPause = onPlayPause,
                    onNext = onNext
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search songs, artists, albums") },
                    singleLine = true
                )
            }

            if (songs.isEmpty()) {
                Text(
                    text = "No audio files found on this device.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == currentSong?.id,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }
    }
}
