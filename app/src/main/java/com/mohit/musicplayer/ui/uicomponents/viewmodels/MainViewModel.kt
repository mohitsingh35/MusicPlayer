package com.mohit.musicplayer.ui.uicomponents.viewmodels

import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mohit.musicplayer.models.Song
import com.mohit.musicplayer.models.getAllAudioFromDevice
import com.mohit.musicplayer.services.MusicService
import com.mohit.musicplayer.utils.PrefManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _songList = MutableLiveData<List<Song>>()
    val songList: LiveData<List<Song>> get() = _songList

    private val _progress = MutableLiveData<Pair<Int, Int>>()
    val progress: LiveData<Pair<Int, Int>> get() = _progress

    private val _songprogress = MutableLiveData<Int>()
    val songprogress: LiveData<Int> get() = _songprogress

    private val _isPlaying = MutableLiveData<Pair<Boolean, Int>>()
    val isPlaying: LiveData<Pair<Boolean, Int>> get() = _isPlaying

    private val _songCompleted = MutableLiveData<Unit>()
    val songCompleted: LiveData<Unit> get() = _songCompleted

    private val _updatePalyingSong= MutableLiveData<Pair<Int, Boolean>>()
    val updatePalyingSong: LiveData<Pair<Int, Boolean>> get() = _updatePalyingSong

    private var musicService: MusicService? = null
    private var isBound = false

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("progress", 0) ?: 0
            val position = intent?.getIntExtra("songPosition", 0) ?: 0
            _progress.value = Pair(progress, position)
        }
    }

    private val songProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("timestamp", 0) ?: 0
            PrefManager.setLastPlayedSongDuration(progress)
            _songprogress.value = progress
        }
    }

    private val songCompletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _songCompleted.value = Unit
        }
    }

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isPlaying = intent?.getBooleanExtra("isPlaying", false) ?: false
            val songPosition = intent?.getIntExtra("songPosition", -1) ?: -1
            _isPlaying.value = Pair(isPlaying, songPosition)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    init {
        val applicationContext = getApplication<Application>().applicationContext
        PrefManager.setShouldRestore(true)
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(progressReceiver, IntentFilter("PROGRESS_UPDATE"))
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(songCompletionReceiver, IntentFilter("SONG_COMPLETED"))
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(stateChangeReceiver, IntentFilter("STATE_CHANGE"))
        LocalBroadcastManager.getInstance(applicationContext)
            .registerReceiver(songProgressReceiver, IntentFilter("TIMESTAMP_UPDATE"))
    }

    fun restoreState() {
        val songList = _songList.value ?: return
        if (PrefManager.getLastPlayedSong().isNotEmpty()) {
            val lastPlayedSongData = PrefManager.getLastPlayedSong()
            val songIndex = songList.indexOfFirst { it.data == lastPlayedSongData }

            if (songIndex != -1) {
                val song = songList[songIndex]
                val lastPlayedDuration = PrefManager.getLastPlayedSongDuration()
                song.isPlaying = true
                song.isPaused = false
                _isPlaying.postValue(Pair(true,songIndex))
                startPlayingSong(song, lastPlayedDuration)
                pausePlayingSong(song)

            }
        }
    }

    fun seekTo(position: Int) {
        val intent = Intent(getApplication(), MusicService::class.java).apply {
            action = MusicService.ACTION_SEEK
            putExtra("seek_position", position)
        }
        getApplication<Application>().startService(intent)
    }

    fun initializePlayer(contentResolver: ContentResolver) {
        _songList.value = getAllAudioFromDevice(contentResolver)
        val intent = Intent(getApplication(), MusicService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun requestPermission(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        } else {
            initializePlayer(context.contentResolver)
        }
    }

    fun handleSongClick(song: Song) {
        val songList = _songList.value ?: return
        val previousPlayingPosition = songList.indexOfFirst { it.isPlaying }
        if (previousPlayingPosition != -1) {
            _updatePalyingSong.postValue(Pair(previousPlayingPosition, false))
        }
        val newPlayingPosition = songList.indexOf(song)
        _updatePalyingSong.postValue(Pair(newPlayingPosition, true))
    }

    fun startPlayingSong(song: Song, startFrom: Int = 0) {
        _isPlaying.postValue(Pair(true, _songList.value?.indexOf(song) ?: -1))
        val intent = Intent(getApplication(), MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra("song_path", song.data)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_position", _songList.value?.indexOf(song))
            putExtra("start_from", startFrom)
        }
        getApplication<Application>().startService(intent)
        PrefManager.setLastPlayedSong(song.data)
        PrefManager.setLastPlayedSongDuration(startFrom)
    }

    fun pausePlayingSong(song: Song){
        val intent = Intent(getApplication(), MusicService::class.java).apply {
            action = if (song.isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY
            putExtra("song_path", song.data)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_position", _songList.value?.indexOf(song))
        }
        getApplication<Application>().startService(intent)
    }


    fun updatePlayingSong(position: Int, isPlaying: Boolean) {
        val songList = _songList.value?.toMutableList() ?: return
        songList[position].isPlaying = isPlaying
        _songList.value = songList
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
        }
        val applicationContext = getApplication<Application>().applicationContext
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(progressReceiver)
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(songCompletionReceiver)
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(stateChangeReceiver)
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(songProgressReceiver)

    }

    companion object {
        private const val REQUEST_CODE_READ_EXTERNAL_STORAGE = 1
    }
}
