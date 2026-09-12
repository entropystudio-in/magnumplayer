package com.cadence.player.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.cadence.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide singleton that binds to PlaybackService and mirrors its
 * StateFlows, so the Compose UI always has something to collect (with
 * sane defaults) even before the service connection completes.
 *
 * All actual playback state and logic lives in PlaybackService - this
 * class is just the UI-facing pipe to it.
 */
class PlayerConnection(private val appContext: Context) {

    private var service: PlaybackService? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectJob: Job? = null

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffleOn = MutableStateFlow(false)
    val shuffleOn: StateFlow<Boolean> = _shuffleOn.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? PlaybackService.LocalBinder ?: return
            val svc = localBinder.getService()
            service = svc

            collectJob?.cancel()
            collectJob = scope.launch {
                launch { svc.currentQueue.collect { _currentQueue.value = it } }
                launch { svc.currentSong.collect { _currentSong.value = it } }
                launch { svc.isPlaying.collect { _isPlaying.value = it } }
                launch { svc.positionMs.collect { _positionMs.value = it } }
                launch { svc.durationMs.collect { _durationMs.value = it } }
                launch { svc.shuffleOn.collect { _shuffleOn.value = it } }
                launch { svc.repeatMode.collect { _repeatMode.value = it } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collectJob?.cancel()
            service = null
        }
    }

    fun bind() {
        val intent = Intent(appContext, PlaybackService::class.java)
        appContext.startService(intent)
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        collectJob?.cancel()
        runCatching { appContext.unbindService(connection) }
    }

    fun playQueue(songs: List<Song>, startSong: Song? = null) {
        service?.playQueue(songs, startSong)
    }

    fun togglePlayPause() = service?.togglePlayPause()
    fun seekTo(ms: Long) = service?.seekTo(ms)
    fun skipForward(ms: Long = 15_000) = service?.skipForward(ms)
    fun skipBackward(ms: Long = 15_000) = service?.skipBackward(ms)
    fun next() = service?.next()
    fun previous() = service?.previous()
    fun toggleShuffle() = service?.toggleShuffle()
    fun cycleRepeatMode() = service?.cycleRepeatMode()
    fun playQueueItem(song: Song) = service?.playQueueItem(song)
}
