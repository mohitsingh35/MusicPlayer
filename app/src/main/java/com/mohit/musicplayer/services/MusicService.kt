package com.mohit.musicplayer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mohit.musicplayer.utils.PrefManager
import com.mohit.musicplayer.R

class MusicService : Service() {

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var songName:String ="Song Name"
    private var songArtist:String="Song Artist"
    private var currentlyPlayingPosition:Int=-1

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val songPath = intent.getStringExtra("song_path")
                songName = intent.getStringExtra("song_title")!!
                songArtist = intent.getStringExtra("song_artist")!!
                currentlyPlayingPosition = intent.getIntExtra("song_position", -1)
                val startFrom = intent.getIntExtra("start_from", 0)

                Log.d("MusicService", "Playing song: $songPath")
                Log.d("MusicService", "Playing song: $songName")
                Log.d("MusicService", "Playing song: $songArtist")

                if (songPath != null) {
                    playSong(songPath, songName, songArtist, startFrom)
                }
            }
            ACTION_PAUSE -> {
                if (isPlaying) {
                    pauseSong()
                } else {
                    resumeSong()
                }
                updateNotificationControls()
            }
            ACTION_STOP -> {
                stopSong()
            }
            ACTION_SEEK -> {
                val seekPosition = intent.getIntExtra("seek_position", 0)
                seekTo(seekPosition)
            }
        }
        return START_STICKY
    }


    private val handler = Handler()
    private fun updateNotificationProgress(progress: Int) {
        val remoteViews = RemoteViews(packageName, R.layout.notification_music_player).apply {
            setProgressBar(R.id.notification_progress, 100, progress, false)
            setTextViewText(R.id.notification_title, songName)
            setTextViewText(R.id.notification_artist, songArtist)
            setImageViewResource(R.id.notification_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            setOnClickPendingIntent(R.id.notification_play_pause, getPendingIntent(ACTION_PAUSE))
            setOnClickPendingIntent(R.id.notification_stop, getPendingIntent(ACTION_STOP))
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setCustomContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun broadcastProgress(progress: Int) {
        val progressUpdateIntent = Intent("PROGRESS_UPDATE").apply {
            putExtra("progress", progress)
            putExtra("songPosition", currentlyPlayingPosition)

        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(progressUpdateIntent)
    }

    private fun broadcastTimestamp() {
        mediaPlayer?.let {
            val currentTimestamp = it.currentPosition
            val timestampIntent = Intent("TIMESTAMP_UPDATE").apply {
                putExtra("timestamp", currentTimestamp)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(timestampIntent)
        }
    }


    private val updateProgressTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                val progress = (it.currentPosition * 100) / it.duration
                Log.d("MusicService", "Current progress: $progress")
                broadcastProgress(progress)
                updateNotificationProgress(progress)
                broadcastTimestamp()
                handler.postDelayed(this, 1000)
            }
        }
    }


    private fun onSongCompleted() {
        val intent = Intent("SONG_COMPLETED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun playSong(songPath: String, songTitle: String, songArtist: String, startFrom: Int = 0) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(songPath)
            prepare()
            start()
            seekTo(startFrom)
            setOnCompletionListener {
                onSongCompleted()
            }
        }
        isPlaying = true
        buildNotification(songTitle, songArtist)
        handler.post(updateProgressTask)
    }

    private fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        sendStateChangeBroadcast()
    }


    private fun stopSong() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        stopForeground(true)
        stopSelf()
        sendStateChangeBroadcast()
        PrefManager.setLastPlayedSongDuration(mediaPlayer!!.currentPosition)

    }

    private fun pauseSong() {
        mediaPlayer?.let {
            it.pause()
            isPlaying = false
            PrefManager.setLastPlayedSongDuration(it.currentPosition)
            sendStateChangeBroadcast()
        }
    }


    private fun resumeSong() {
        mediaPlayer?.start()
        isPlaying = true
        sendStateChangeBroadcast()
    }

    private fun buildNotification(songTitle: String, artistName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val remoteViews = RemoteViews(packageName, R.layout.notification_music_player).apply {
            setTextViewText(R.id.notification_title, songTitle)
            setTextViewText(R.id.notification_artist, artistName)
            setImageViewResource(R.id.notification_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            setOnClickPendingIntent(R.id.notification_play_pause, getPendingIntent(ACTION_PAUSE))
            setOnClickPendingIntent(R.id.notification_stop, getPendingIntent(ACTION_STOP))
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setCustomContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationControls() {
        val remoteViews = RemoteViews(packageName, R.layout.notification_music_player).apply {
            setImageViewResource(R.id.notification_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setCustomContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "Music_Player_Channel"
        const val ACTION_SEEK = "ACTION_SEEK"
        const val NOTIFICATION_ID = 1

    }

    private fun sendStateChangeBroadcast() {
        val intent = Intent("STATE_CHANGE").apply {
            putExtra("isPlaying", isPlaying)
            putExtra("songPosition", currentlyPlayingPosition)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


}
