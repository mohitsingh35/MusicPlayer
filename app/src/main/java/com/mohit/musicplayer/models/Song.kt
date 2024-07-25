package com.mohit.musicplayer.models

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

fun getAllAudioFromDevice(contentResolver: ContentResolver): List<Song> {
    val songs = mutableListOf<Song>()
    val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val projection = arrayOf(
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID
    )

    val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, null)

    cursor?.use {
        val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (it.moveToNext()) {
            val title = it.getString(titleColumn)
            val artist = it.getString(artistColumn)
            val data = it.getString(dataColumn)
            val albumId = it.getLong(albumIdColumn)
            val albumArt = getAlbumArt(contentResolver, albumId)
            songs.add(Song(title, artist, data, albumArt))
        }
    }
    return songs
}

fun getAlbumArt(contentResolver: ContentResolver, albumId: Long): String? {
    val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    val cursor: Cursor? = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)

    var albumArt: String? = null
    cursor?.use {
        if (it.moveToFirst()) {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            albumArt = it.getString(dataColumn)
        }
    }
    return albumArt
}

data class Song(val title: String, val artist: String, val data: String, val albumArt: String?, var isPlaying: Boolean = false, var isPaused: Boolean = false)
