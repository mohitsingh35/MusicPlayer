package com.mohit.musicplayer.ui.uicomponents.adapters

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mohit.musicplayer.R
import com.mohit.musicplayer.utils.ExtensionsUtil.gone
import com.mohit.musicplayer.utils.ExtensionsUtil.setOnClickThrottleBounceListener
import com.mohit.musicplayer.utils.ExtensionsUtil.visible
import com.mohit.musicplayer.databinding.ItemSongBinding
import com.mohit.musicplayer.models.Song
import com.mohit.musicplayer.services.MusicService
import com.mohit.musicplayer.utils.PrefManager

class SongAdapter(
    private val songs: List<Song>,
    val context: Context,
    private val clickListener: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {

    private var currentlyPlayingPosition: Int? = null
    private var progressMap = mutableMapOf<Int, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.songTitle.text = song.title
        holder.binding.songArtist.text = song.artist
        loadAlbumArtIntoImageView(context, holder.binding.songImage, song.albumArt)

        if (PrefManager.isFavoriteSong(song.data)){
            holder.binding.favs.setImageResource(R.drawable.baseline_favorite_24)
        }else{
            holder.binding.favs.setImageResource(R.drawable.baseline_favorite_border_24)
        }

        holder.binding.favs.setOnClickThrottleBounceListener {
            if (PrefManager.isFavoriteSong(song.data)){
                PrefManager.removeFavoriteSong(song.data)
                holder.binding.favs.setImageResource(R.drawable.baseline_favorite_border_24)
            }
            else{
                PrefManager.putFavoriteSong(song.data)
                holder.binding.favs.setImageResource(R.drawable.baseline_favorite_24)
            }
        }

        holder.binding.root.setOnClickThrottleBounceListener {
            val previousPlayingPosition = currentlyPlayingPosition
            currentlyPlayingPosition = holder.adapterPosition
            notifyItemChanged(previousPlayingPosition ?: -1)
            notifyItemChanged(holder.adapterPosition)
            clickListener(song)
        }

        holder.binding.songPlaying.visibility = if (song.isPlaying) View.VISIBLE else View.GONE
        holder.binding.songNotPlaying.visibility = if (song.isPlaying) View.GONE else View.VISIBLE

        holder.binding.ic.setOnClickThrottleBounceListener {
            val intent = Intent(context, MusicService::class.java).apply {
                action = if (song.isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY
                putExtra("song_path", song.data)
                putExtra("song_title", song.title)
                putExtra("song_artist", song.artist)
                putExtra("song_position", songs.indexOf(song))
            }
            context.startService(intent)
        }

        if (song.isPlaying) {
            val progress = progressMap[position] ?: 0
            holder.binding.circularProgressIndicator.progress = progress
            holder.binding.songTitle.setTextColor(context.resources.getColor(R.color.colorAccent))
            if (!song.isPaused){
                holder.binding.ic.setImageResource(R.drawable.ic_play_arrow)
                holder.binding.animationView.gone()
            }
            else{
                holder.binding.ic.setImageResource(R.drawable.ic_pause)
                holder.binding.animationView.visible()
            }

        } else {
            holder.binding.songTitle.setTextColor(context.resources.getColor(R.color.better_white))
            holder.binding.circularProgressIndicator.progress = 0
            holder.binding.animationView.gone()
        }
    }

    inner class ViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = songs.size

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

    fun updatePlayingSong(position: Int, isPlaying: Boolean) {
        for (i in songs.indices) {
            songs[i].isPlaying = (i == position && isPlaying)
            songs[i].isPaused = (i == position && isPlaying)

        }
        currentlyPlayingPosition = if (isPlaying) position else null
        notifyDataSetChanged()
    }


    fun updateProgress(progress: Int, position: Int) {
        Log.d("SongAdapter", "Updating progress: $progress")
        val previousProgress = progressMap[position] ?: 0
        if (progress != previousProgress) {
            progressMap[position] = progress
            notifyItemChanged(position)
        }
    }



    fun updatePlayPauseState(position: Int, isPlaying: Boolean) {
        songs[position].isPlaying = true
        songs[position].isPaused = isPlaying
        notifyItemChanged(position)
    }
}
