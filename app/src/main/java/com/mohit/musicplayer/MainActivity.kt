package com.mohit.musicplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mohit.musicplayer.ExtensionsUtil.loadAlbumArtIntoImageView
import com.mohit.musicplayer.ExtensionsUtil.rotateInfinity
import com.mohit.musicplayer.ExtensionsUtil.setOnClickThrottleBounceListener
import com.mohit.musicplayer.ExtensionsUtil.visible
import com.mohit.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        PrefManager.initialize(this)

        viewModel.songList.observe(this, Observer { songs ->
            songs?.let {
                setupRecyclerView(it)
                restoreSongDetails()
                if (savedInstanceState == null) {
                    setSongFromCache()
                }

            }
        })

        viewModel.progress.observe(this, Observer { (progress, position) ->
            Log.d("MainActivity", "Received progress: $progress at position $position")
            songAdapter.updateProgress(progress, position)
            updateSongProgress(progress)
        })

        viewModel.isPlaying.observe(this, Observer { (isPlaying, songPosition) ->
            Log.d("MainActivity", isPlaying.toString())
            Log.d("MainActivity", songPosition.toString())

            if (songPosition != -1) {
                songAdapter.updatePlayPauseState(songPosition, isPlaying)
            }

            binding.icPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            binding.songPreview.visible()
        })

        viewModel.songCompleted.observe(this, Observer {
            handleNextSong()
        })

        viewModel.updatePalyingSong.observe(this, Observer { (songPosition, isPlaying,) ->
            songAdapter.updatePlayingSong(songPosition, isPlaying)
            val song=viewModel.songList.value?.get(songPosition) ?: return@Observer
            setSongPreview(song)
            viewModel.startPlayingSong(song)
        })

        viewModel.requestPermission(this)
    }

    private fun setupRecyclerView(songs: List<Song>) {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        songAdapter = SongAdapter(songs, this) { song ->
            Log.d("clicked", song.toString())
            viewModel.handleSongClick(song)
        }
        recyclerView.adapter = songAdapter
    }

    private fun handleNextSong() {
        val currentPosition = viewModel.songList.value?.indexOfFirst { it.isPlaying } ?: return
        if (currentPosition != -1) {
            songAdapter.updatePlayingSong(currentPosition, false)
            val nextPosition = (currentPosition + 1) % (viewModel.songList.value?.size ?: 1)
            songAdapter.updatePlayingSong(nextPosition, true)
            val song=viewModel.songList.value?.get(nextPosition) ?: return
            viewModel.startPlayingSong(song)
            setSongPreview(song)
        }
    }

    private fun setSongFromCache() {
        val songList = viewModel.songList.value ?: return
        if (PrefManager.getLastPlayedSong().isNotEmpty()) {
            val lastPlayedSongData = PrefManager.getLastPlayedSong()
            val songIndex = songList.indexOfFirst { it.data == lastPlayedSongData }

            if (songIndex != -1) {
                val song = songList[songIndex]
                val lastPlayedDuration = PrefManager.getLastPlayedSongDuration()
                song.isPlaying = true
                song.isPaused=false
                setSongPreview(song)
                viewModel.startPlayingSong(song, lastPlayedDuration)
                viewModel.pausePlayingSong(song)
                songAdapter.updatePlayPauseState(songIndex,true)
            }
        }
    }

    private fun restoreSongDetails() {
        val songList = viewModel.songList.value ?: return
        if (PrefManager.getLastPlayedSong().isNotEmpty()) {
            val lastPlayedSongData = PrefManager.getLastPlayedSong()
            val songIndex = songList.indexOfFirst { it.data == lastPlayedSongData }

            if (songIndex != -1) {
                val song = songList[songIndex]
                song.isPlaying = true
                song.isPaused=false
                setSongPreview(song)
            }
        }
    }

    private fun updateSongProgress(progress: Int) {
        binding.progressHorizontal.progress = progress
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

        if (song.isPlaying){
            binding.icPlayPause.setImageResource(R.drawable.ic_pause)
        }else{
            binding.icPlayPause.setImageResource(R.drawable.ic_play_arrow)
        }

        binding.icPlayPause.setOnClickThrottleBounceListener{
            viewModel.pausePlayingSong(song)
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
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }
}
