package com.cadence.player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.cadence.player.R
import com.cadence.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Owns the native audio engine, the playback queue, the MediaSession
 * (lock-screen controls), and the foreground notification. This is the
 * one process-wide source of truth for "what's playing right now" -
 * PlayerConnection (bound from the UI) just mirrors this service's
 * state as StateFlows.
 */
class PlaybackService : Service() {

    private val engine = NativeAudioEngine()
    private lateinit var mediaSession: MediaSessionCompat

    private var originalQueue: List<Song> = emptyList()
    private var activeQueue: MutableList<Song> = mutableListOf()
    private var currentIndex: Int = -1

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

    private val scope = CoroutineScope(Dispatchers.Main)
    private var pollJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }
    private val localBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "CadenceSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { this@PlaybackService.play() }
                override fun onPause() { this@PlaybackService.pause() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { previous() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
            })
            isActive = true
        }
        startPolling()
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
        }
        return START_NOT_STICKY
    }

    // ---- Queue control, called by PlayerConnection ----

    fun playQueue(songs: List<Song>, startSong: Song?) {
        originalQueue = songs
        activeQueue = songs.toMutableList()
        currentIndex = startSong?.let { activeQueue.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        if (_shuffleOn.value) reshuffleFromCurrent()
        _currentQueue.value = activeQueue
        loadCurrentAndPlay()
    }

    fun playQueueItem(song: Song) {
        val idx = activeQueue.indexOf(song)
        if (idx >= 0) {
            currentIndex = idx
            loadCurrentAndPlay()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun play() {
        if (_currentSong.value == null) return
        engine.play()
        _isPlaying.value = true
        updateSessionAndNotification()
    }

    fun pause() {
        engine.pause()
        _isPlaying.value = false
        updateSessionAndNotification()
    }

    fun seekTo(ms: Long) {
        engine.seekTo(ms)
        _positionMs.value = ms
        updateSessionAndNotification()
    }

    fun skipForward(ms: Long = 15_000) {
        val target = (engine.getPositionMs() + ms).coerceAtMost(engine.getDurationMs())
        seekTo(target)
    }

    fun skipBackward(ms: Long = 15_000) {
        val target = (engine.getPositionMs() - ms).coerceAtLeast(0)
        seekTo(target)
    }

    fun next() {
        if (currentIndex < activeQueue.size - 1) {
            currentIndex++
            loadCurrentAndPlay()
        } else if (_repeatMode.value == RepeatMode.ALL && activeQueue.isNotEmpty()) {
            currentIndex = 0
            loadCurrentAndPlay()
        } else {
            pause()
        }
    }

    fun previous() {
        if (engine.getPositionMs() > 3_000 || currentIndex == 0) {
            seekTo(0)
        } else {
            currentIndex--
            loadCurrentAndPlay()
        }
    }

    fun toggleShuffle() {
        val newVal = !_shuffleOn.value
        _shuffleOn.value = newVal
        if (newVal) {
            reshuffleFromCurrent()
        } else {
            val current = activeQueue.getOrNull(currentIndex)
            activeQueue = originalQueue.toMutableList()
            currentIndex = current?.let { activeQueue.indexOf(it) } ?: currentIndex
        }
        _currentQueue.value = activeQueue
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    private fun reshuffleFromCurrent() {
        if (activeQueue.isEmpty()) return
        val played = activeQueue.subList(0, (currentIndex + 1).coerceAtMost(activeQueue.size))
        val remaining = if (currentIndex + 1 < activeQueue.size) {
            activeQueue.subList(currentIndex + 1, activeQueue.size).shuffled()
        } else emptyList()
        activeQueue = (played + remaining).toMutableList()
    }

    private fun loadCurrentAndPlay() {
        val song = activeQueue.getOrNull(currentIndex) ?: return
        val opened = engine.open(applicationContext, song.uri)
        if (!opened) return
        _currentSong.value = song
        _durationMs.value = engine.getDurationMs()
        _positionMs.value = 0L
        engine.play()
        _isPlaying.value = true
        startForeground(NOTIFICATION_ID, buildNotification())
        updateSessionAndNotification()
    }

    // ---- Background polling: position updates + auto-advance on track end ----

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                if (_currentSong.value != null) {
                    _positionMs.value = engine.getPositionMs()
                    if (engine.hasReachedEnd()) {
                        if (_repeatMode.value == RepeatMode.ONE) {
                            seekTo(0)
                            engine.play()
                        } else {
                            next()
                        }
                    }
                }
                delay(400)
            }
        }
    }

    // ---- MediaSession + notification ----

    private fun updateSessionAndNotification() {
        val song = _currentSong.value
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song?.title ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song?.album ?: "")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, _durationMs.value)
                .build()
        )
        val state = if (_isPlaying.value) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, _positionMs.value, 1f)
                .build()
        )
        if (song != null) {
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(_currentSong.value?.title ?: "Cadence")
        .setContentText(_currentSong.value?.artist ?: "")
        .setSmallIcon(R.drawable.ic_notification)
        .setOnlyAlertOnce(true)
        .setOngoing(_isPlaying.value)
        .addAction(R.drawable.ic_prev, "Previous", actionPendingIntent(ACTION_PREV))
        .addAction(
            if (_isPlaying.value) R.drawable.ic_pause else R.drawable.ic_play,
            "Play/Pause",
            actionPendingIntent(ACTION_PLAY_PAUSE)
        )
        .addAction(R.drawable.ic_next, "Next", actionPendingIntent(ACTION_NEXT))
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        .build()

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Music playback controls" }
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        mediaSession.release()
        engine.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!_isPlaying.value) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val CHANNEL_ID = "cadence_playback"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.cadence.player.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.cadence.player.action.NEXT"
        const val ACTION_PREV = "com.cadence.player.action.PREV"
    }
}
