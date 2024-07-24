package com.mohit.musicplayer

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mohit.musicplayer.ExtensionsUtil.formatTime
import com.mohit.musicplayer.ExtensionsUtil.loadAlbumArtIntoImageView
import com.mohit.musicplayer.ExtensionsUtil.rotateInfinity
import com.mohit.musicplayer.ExtensionsUtil.setOnClickThrottleBounceListener
import com.mohit.musicplayer.ExtensionsUtil.visible
import com.mohit.musicplayer.databinding.FragmentDeviceSongsBinding


class DeviceSongsFragment : Fragment() {

    lateinit var binding: FragmentDeviceSongsBinding
    private lateinit var recyclerView: RecyclerView
    private lateinit var songAdapter: SongAdapter
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDeviceSongsBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViews()

        if (PrefManager.getShouldRestore()){
            Log.d("restoreState", "restoring")
            PrefManager.setShouldRestore(false)
            viewModel.restoreState()
        }

    }

    private fun setUpViews() {

        binding.songPreview.setOnClickThrottleBounceListener {
            val currentPosition = viewModel.songList.value?.indexOfFirst { it.isPlaying } ?: return@setOnClickThrottleBounceListener
            val song=viewModel.songList.value?.get(currentPosition) ?: return@setOnClickThrottleBounceListener
            val bundle = Bundle()
            bundle.putString("library", "Device Songs")
            bundle.putString("song_path", song.data )
            findNavController().navigate(R.id.flow_device_songs_to_music_payer, bundle)
        }

        viewModel.songList.observe(viewLifecycleOwner, Observer { songs ->
            songs?.let {
                setupRecyclerView(it)
                restoreSongDetails()
            }
        })

        viewModel.progress.observe(viewLifecycleOwner, Observer { (progress, position) ->
//            Log.d("MainActivity", "Received progress: $progress at position $position")
            songAdapter.updateProgress(progress, position)
            updateSongProgress(progress)

            if (progress>=100){
                handleNextSong()
            }
        })



        viewModel.isPlaying.observe(viewLifecycleOwner, Observer { (isPlaying, songPosition) ->
            Log.d("MainActivity", isPlaying.toString())
            Log.d("MainActivity", songPosition.toString())

            if (songPosition != -1) {
                songAdapter.updatePlayPauseState(songPosition, isPlaying)
            }

            binding.icPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            binding.songPreview.visible()
        })

        viewModel.updatePalyingSong.observe(viewLifecycleOwner, Observer { (songPosition, isPlaying,) ->
            if (PrefManager.getWasClicked()) {
                songAdapter.updatePlayingSong(songPosition, isPlaying)
                val song = viewModel.songList.value?.get(songPosition) ?: return@Observer
                PrefManager.setWasClicked(false)
                setSongPreview(song)
                viewModel.startPlayingSong(song)
            }
        })

        viewModel.requestPermission(requireContext())
    }
    private fun setupRecyclerView(songs: List<Song>) {
        recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        songAdapter = SongAdapter(songs, requireContext()) { song ->
            Log.d("clicked", song.toString())
            viewModel.handleSongClick(song)
            PrefManager.setWasClicked(true)
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
        loadAlbumArtIntoImageView(requireContext(),binding.songImage,song.albumArt)
        binding.songImage.rotateInfinity(requireContext())
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

}