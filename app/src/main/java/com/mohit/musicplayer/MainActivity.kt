package com.mohit.musicplayer

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mohit.musicplayer.ExtensionsUtil.rotateInfinity
import com.mohit.musicplayer.ExtensionsUtil.setOnClickThrottleBounceListener
import com.mohit.musicplayer.ExtensionsUtil.visible
import com.mohit.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var songList: List<Song>
    private lateinit var musicService: MusicService
    private lateinit var recyclerView:RecyclerView
    private var isBound = false
    val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private lateinit var songAdapter: SongAdapter

    companion object {
        private const val REQUEST_CODE_READ_EXTERNAL_STORAGE = 1
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("progress", 0) ?: 0
            val position = intent?.getIntExtra("songPosition", 0) ?: 0

            Log.d("MainActivity", "Received progress: $progress at position $position")
            songAdapter.updateProgress(progress, position)
            updateSongProgress(progress)
        }
    }

    private val songCompletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Song completed")
            handleNextSong()
        }
    }

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isPlaying = intent?.getBooleanExtra("isPlaying", false) ?: false
            val songPosition = intent?.getIntExtra("songPosition", -1) ?: -1
            Log.d("MainActivity", isPlaying.toString())
            Log.d("MainActivity", songPosition.toString())

            if (songPosition != -1) {
                songAdapter.updatePlayPauseState(songPosition, isPlaying)
            }

            binding.icPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        }
    }

    private fun handleNextSong() {
        val currentPosition = songList.indexOfFirst { it.isPlaying }
        if (currentPosition != -1) {
            songAdapter.updatePlayingSong(currentPosition, false)

            val nextPosition = (currentPosition + 1) % songList.size
            songAdapter.updatePlayingSong(nextPosition, true)

            startPlayingSong(songList[nextPosition])
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        PrefManager.initialize(this)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        } else {
            initializePlayer()
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(progressReceiver, IntentFilter("PROGRESS_UPDATE"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(songCompletionReceiver, IntentFilter("SONG_COMPLETED"))
        LocalBroadcastManager.getInstance(this).registerReceiver(stateChangeReceiver, IntentFilter("STATE_CHANGE"))
        setSongFromCache()
    }

    private fun initializePlayer() {
        songList = getAllAudioFromDevice(contentResolver)

        Log.d("songList", songList.toString())

        recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        songAdapter=SongAdapter(songList,this) { song ->
            Log.d("clicked", song.toString())
            handleSongClick(song)
        }
        recyclerView.adapter = songAdapter

        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun handleSongClick(song: Song) {
        val previousPlayingPosition = songList.indexOfFirst { it.isPlaying }
        if (previousPlayingPosition != -1) {
            songAdapter.updatePlayingSong(previousPlayingPosition, false)
        }

        val newPlayingPosition = songList.indexOf(song)
        songAdapter.updatePlayingSong(newPlayingPosition, true)
        startPlayingSong(song)
    }

//    private fun startPlayingSong(song: Song) {
//        val intent = Intent(this, MusicService::class.java).apply {
//            action = MusicService.ACTION_PLAY
//            putExtra("song_path", song.data)
//            putExtra("song_title", song.title)
//            putExtra("song_artist", song.artist)
//            putExtra("song_position", songList.indexOf(song))
//        }
//        startService(intent)
//        setSongPreview(song)
//        PrefManager.setLastPlayedSong(song.data)
//    }

    private fun startPlayingSong(song: Song, startFrom: Int = 0) {
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra("song_path", song.data)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_position", songList.indexOf(song))
            putExtra("start_from", startFrom)
        }
        startService(intent)
        setSongPreview(song)
        PrefManager.setLastPlayedSong(song.data)
        PrefManager.setLastPlayedSongDuration(startFrom)
    }

    private fun setSongFromCache(){
        if (PrefManager.getLastPlayedSong().isNotEmpty()) {
            val lastPlayedSongData = PrefManager.getLastPlayedSong()
            val songIndex = songList.indexOfFirst { it.data == lastPlayedSongData }

            if (songIndex != -1) {
                val song = songList[songIndex]
                val lastPlayedDuration = PrefManager.getLastPlayedSongDuration()
                song.isPlaying = true
                song.isPaused = false
                setSongPreview(song)
                startPlayingSong(song, lastPlayedDuration)
                pausePlayingSong(song)
                songAdapter.updatePlayPauseState(songIndex,true)
            }
        }
    }

    private fun pausePlayingSong(song: Song){
        val intent = Intent(this, MusicService::class.java).apply {
            action = if (song.isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY
            putExtra("song_path", song.data)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_position", songList.indexOf(song))
        }
        startService(intent)
    }



    private fun setSongPreview(song: Song){
        if (PrefManager.getFavoriteSong().contains(song.data)){
            binding.favs.setImageResource(R.drawable.baseline_favorite_24)
        }else{
            binding.favs.setImageResource(R.drawable.baseline_favorite_border_24)
        }
        binding.songPreview.visible()
        loadAlbumArtIntoImageView(this,binding.songImage,song.albumArt)
        binding.songImage.rotateInfinity(this)
        binding.songTitle.text = song.title
        binding.songArtist.text = song.artist

        binding.icPlayPause.setOnClickThrottleBounceListener{
            val intent = Intent(this, MusicService::class.java).apply {
                action = if (song.isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY
                putExtra("song_path", song.data)
                putExtra("song_title", song.title)
                putExtra("song_artist", song.artist)
                putExtra("song_position", songList.indexOf(song))
            }
            startService(intent)
        }
        binding.favs.setOnClickThrottleBounceListener {
            if (PrefManager.isFavoriteSong(song.data)){
                PrefManager.removeFavoriteSong(song.data)
                binding.favs.setImageResource(R.drawable.baseline_favorite_border_24)
            }
            else{
                PrefManager.putFavoriteSong(song.data)
                binding.favs.setImageResource(R.drawable.baseline_favorite_24)
            }
        }
    }

    private fun updateSongProgress(progress: Int){
        binding.progressHorizontal.progress = progress
    }

    private fun loadAlbumArtIntoImageView(context: Context, imageView: ImageView, albumArtPath: String?) {
        if (albumArtPath != null) {
            Glide.with(context)
                .load(albumArtPath)
                .apply(RequestOptions().placeholder(R.drawable.music_record).error(R.drawable.music_record))
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.music_record)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                initializePlayer()
            } else {

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(songCompletionReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateChangeReceiver)

    }
}
