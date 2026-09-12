package com.cadence.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.cadence.player.data.MusicRepository
import com.cadence.player.data.Song
import com.cadence.player.ui.screens.LibraryScreen
import com.cadence.player.ui.screens.NowPlayingScreen
import com.cadence.player.ui.screens.PermissionScreen
import com.cadence.player.ui.theme.CadenceTheme
import com.cadence.player.ui.theme.ThemeManager
import com.cadence.player.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MusicApplication
        app.playerConnection.bind()

        setContent {
            var themeMode by remember { mutableStateOf(ThemeManager.getThemeMode(this)) }
            var permissionGranted by remember { mutableStateOf(hasAudioPermission()) }
            var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
            var showNowPlaying by remember { mutableStateOf(false) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                permissionGranted = granted
            }

            // Also ask for notification permission on Android 13+ so the
            // playback notification (needed for background playback controls)
            // can actually show. Not fatal if denied - playback still works,
            // the user just won't see a lock-screen notification.
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(permissionGranted) {
                if (permissionGranted) {
                    songs = MusicRepository.getAllSongs(this@MainActivity)
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val connection = app.playerConnection
            val currentSong by connection.currentSong.collectAsState()
            val isPlaying by connection.isPlaying.collectAsState()
            val positionMs by connection.positionMs.collectAsState()
            val durationMs by connection.durationMs.collectAsState()
            val queue by connection.currentQueue.collectAsState()
            val shuffleOn by connection.shuffleOn.collectAsState()
            val repeatMode by connection.repeatMode.collectAsState()

            CadenceTheme(themeMode = themeMode) {
                if (!permissionGranted) {
                    PermissionScreen(onGrantClick = { permissionLauncher.launch(audioPermission()) })
                } else {
                    Crossfade(targetState = showNowPlaying, label = "screen") { nowPlaying ->
                        if (nowPlaying && currentSong != null) {
                            BackHandler { showNowPlaying = false }
                            NowPlayingScreen(
                                song = currentSong!!,
                                queue = queue,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                shuffleOn = shuffleOn,
                                repeatMode = repeatMode,
                                onClose = { showNowPlaying = false },
                                onPlayPause = { connection.togglePlayPause() },
                                onNext = { connection.next() },
                                onPrevious = { connection.previous() },
                                onSeek = { connection.seekTo(it) },
                                onSkipForward = { connection.skipForward() },
                                onSkipBackward = { connection.skipBackward() },
                                onToggleShuffle = { connection.toggleShuffle() },
                                onCycleRepeat = { connection.cycleRepeatMode() },
                                onQueueItemClick = { song -> connection.playQueueItem(song) }
                            )
                        } else {
                            LibraryScreen(
                                songs = songs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onSongClick = { song ->
                                    connection.playQueue(songs, song)
                                    showNowPlaying = true
                                },
                                onMiniPlayerClick = { showNowPlaying = true },
                                onPlayPause = { connection.togglePlayPause() },
                                onNext = { connection.next() },
                                onThemeToggle = {
                                    themeMode = ThemeManager.next(themeMode)
                                    ThemeManager.setThemeMode(this@MainActivity, themeMode)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        (application as MusicApplication).playerConnection.unbind()
        super.onDestroy()
    }
}
