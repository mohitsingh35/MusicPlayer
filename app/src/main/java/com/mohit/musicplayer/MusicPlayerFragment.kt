package com.mohit.musicplayer

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.mohit.musicplayer.ExtensionsUtil.formatTime
import com.mohit.musicplayer.ExtensionsUtil.rotateInfinity
import com.mohit.musicplayer.ExtensionsUtil.setOnClickThrottleBounceListener
import com.mohit.musicplayer.ExtensionsUtil.visible
import com.mohit.musicplayer.databinding.FragmentMusicPlayerBinding


class MusicPlayerFragment : Fragment() {

    lateinit var binding: FragmentMusicPlayerBinding
    private lateinit var viewModel: MainViewModel
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMusicPlayerBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViews()
    }

    private fun setUpViews() {
        binding.animationView.rotateInfinity(requireContext())
        binding.musicRecord.rotateInfinity(requireContext())



        viewModel.songList.observe(viewLifecycleOwner, Observer { songs ->
            songs?.let {
                val data=arguments?.getString("song_path")
                val song=songs.find { it.data==data }!!
                Log.d("songClick", song.title.toString())

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(data)
                    prepare()
                }
                setSong(song)
            }
        })

        viewModel.isPlaying.observe(viewLifecycleOwner, Observer { (isPlaying, songPosition) ->
            Log.d("isPlaying", isPlaying.toString())

            if (isPlaying){
                binding.playPauseBtn.setImageResource(R.drawable.ic_pause)
            }
            else{
                binding.playPauseBtn.setImageResource(R.drawable.ic_play_arrow)
            }
        })

        viewModel.progress.observe(viewLifecycleOwner, Observer { (progress, position) ->
            binding.seekBar.progress=progress
            binding.endTime.text=formatTime(mediaPlayer?.duration!!)
            if (progress>=100){
                handleNextSong()
            }
        })

        viewModel.songprogress.observe(viewLifecycleOwner, Observer {time->
            Log.d("songprogress", time.toString())
            binding.startTime.text= formatTime(time)
        })

        binding.btnBack.setOnClickThrottleBounceListener{
            findNavController().navigate(R.id.flow_music_payer_to_device_songs)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaPlayer?.duration ?: 0
                    val newPosition = (progress * duration) / 100
                    viewModel.seekTo(newPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        binding.playPauseBtn.setOnClickThrottleBounceListener {
            val data=arguments?.getString("song_path")
            val song=viewModel.songList.value?.find { it.data==data }!!
            song.isPlaying=true
            viewModel.pausePlayingSong(song)
        }

        binding.favs.setOnClickThrottleBounceListener {
            val data=arguments?.getString("song_path")
            val song=viewModel.songList.value?.find { it.data==data }!!
            if (PrefManager.isFavoriteSong(song.data)){
                PrefManager.removeFavoriteSong(song.data)
                binding.favs.setImageResource(R.drawable.baseline_favorite_border_24)
            }
            else{
                PrefManager.putFavoriteSong(song.data)
                binding.favs.setImageResource(R.drawable.baseline_favorite_24)
            }
        }


        binding.icNext.setOnClickThrottleBounceListener {
            Log.d("handleNextSong", "called from icNext")
            handleNextSong()
        }

        binding.prev.setOnClickThrottleBounceListener {
            Log.d("handlePrevSong", "called from prev")
            handlePrevSong()
        }

    }

    private fun handleNextSong() {
        Log.d("handleNextSong", "called")
        val currentPosition = viewModel.songList.value?.indexOfFirst { it.isPlaying } ?: return
        if (currentPosition != -1) {
            val nextPosition = (currentPosition + 1) % (viewModel.songList.value?.size ?: 1)
            val song=viewModel.songList.value?.get(nextPosition) ?: return
            viewModel.startPlayingSong(song)
            viewModel.handleSongClick(song)
            PrefManager.setLastPlayedSong(song.data)
            setSong(song)
//            viewModel.updatePlayingSong(nextPosition,true)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.data)
                prepare()
            }

        }
    }

    private fun handlePrevSong() {
        Log.d("handlePrevSong", "called")
        val currentPosition = viewModel.songList.value?.indexOfFirst { it.isPlaying } ?: return
        if (currentPosition != -1) {
            val prevPosition = if (currentPosition > 0) currentPosition - 1 else viewModel.songList.value!!.size - 1
            val song = viewModel.songList.value?.get(prevPosition) ?: return
            viewModel.startPlayingSong(song)
            viewModel.handleSongClick(song)
            PrefManager.setLastPlayedSong(song.data)
            setSong(song)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.data)
                prepare()
            }
        }
    }



    private fun setSong(song: Song){
        binding.libraryName.text= arguments?.getString("library")
        binding.songName.text=song.title
        binding.artistName.text=song.artist
        if (PrefManager.getFavoriteSong().contains(song.data)){
            binding.favs.setImageResource(R.drawable.baseline_favorite_24)
        }else{
            binding.favs.setImageResource(R.drawable.baseline_favorite_border_24)
        }
    }
}