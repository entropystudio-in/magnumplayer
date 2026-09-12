package com.cadence.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans MediaStore for every audio file visible on the device's storage
 * (internal storage + SD card if present). This is a read-only query -
 * it never re-encodes or compresses anything; files are played exactly
 * as they exist on disk.
 */
object MusicRepository {

    suspend fun getAllSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown title"
                val artist = cursor.getString(artistCol) ?: "Unknown artist"
                val album = cursor.getString(albumCol) ?: "Unknown album"
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)

                val songUri = ContentUris.withAppendedId(collection, id)
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = songUri,
                        albumArtUri = artUri
                    )
                )
            }
        }

        songs
    }
}
