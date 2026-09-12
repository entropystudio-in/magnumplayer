package com.cadence.player.playback

import android.content.Context
import android.net.Uri

/**
 * Thin Kotlin wrapper over the native (C++/Oboe) audio engine. Each
 * instance owns one native AudioEngine via a handle (a pointer, stored
 * as a Long). Playback state itself lives in PlayerConnection, which
 * polls this class rather than the other way around - see that class
 * for why (keeps the real-time audio callback free of any JNI calls).
 */
class NativeAudioEngine {

    private var handle: Long = nativeCreate()
    private var openFileDescriptor: android.os.ParcelFileDescriptor? = null

    /** Opens [uri] as the current source. Closes any previously open source first. */
    fun open(context: Context, uri: Uri): Boolean {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
        val length = pfd.statSize
        val opened = nativeOpenSource(handle, pfd.fd, 0L, length)
        // The native side dup()s its own copy of the fd during openSource,
        // so it's safe to close ours right away regardless of outcome.
        openFileDescriptor?.close()
        openFileDescriptor = pfd
        return opened
    }

    fun play() = nativePlay(handle)
    fun pause() = nativePause(handle)
    fun seekTo(positionMs: Long) = nativeSeekTo(handle, positionMs)
    fun getPositionMs(): Long = nativeGetPositionMs(handle)
    fun getDurationMs(): Long = nativeGetDurationMs(handle)
    fun isPlaying(): Boolean = nativeIsPlaying(handle)
    fun hasReachedEnd(): Boolean = nativeHasReachedEnd(handle)

    fun release() {
        nativeClose(handle)
        nativeDestroy(handle)
        openFileDescriptor?.close()
        openFileDescriptor = null
    }

    private external fun nativeCreate(): Long
    private external fun nativeOpenSource(handle: Long, fd: Int, offset: Long, length: Long): Boolean
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeSeekTo(handle: Long, positionMs: Long)
    private external fun nativeGetPositionMs(handle: Long): Long
    private external fun nativeGetDurationMs(handle: Long): Long
    private external fun nativeIsPlaying(handle: Long): Boolean
    private external fun nativeHasReachedEnd(handle: Long): Boolean
    private external fun nativeClose(handle: Long)
    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("cadence_audio")
        }
    }
}
