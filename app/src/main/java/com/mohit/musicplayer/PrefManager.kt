package com.mohit.musicplayer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PrefManager {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor : SharedPreferences.Editor

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(
            "Music Player App",
            Context.MODE_PRIVATE
        )
        editor = sharedPreferences.edit()
    }

    fun putFavoriteSong(favSong: String){
        val favs = getFavoriteSong().toMutableList()
        favs.add(favSong)
        val gson = Gson()
        val songsJson = gson.toJson(favs)
        editor.putString("favSongs", songsJson)
        editor.apply()
    }

    fun removeFavoriteSong(favSong: String) {
        val favs = getFavoriteSong().toMutableList()
        if (favs.remove(favSong)) {
            val gson = Gson()
            val songsJson = gson.toJson(favs)
            editor.putString("favSongs", songsJson)
            editor.apply()
        }
    }

    fun isFavoriteSong(favSong: String): Boolean {
        val favs = getFavoriteSong()
        return favs.contains(favSong)
    }

    fun getFavoriteSong():List<String>{
        val songsJson = sharedPreferences.getString("favSongs",
            null
        )
        if (songsJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(songsJson, type)
        }else{
            return listOf()
        }
    }

    fun setLastPlayedSong(songName: String){
        editor.putString("lastPlayedSong", songName)
        editor.apply()
    }

    fun getLastPlayedSong(): String {
        return sharedPreferences.getString("lastPlayedSong","")!!
    }

    fun setLastPlayedSongDuration(songName: Int){
        editor.putInt("lastPlayedSongDuration", songName)
        editor.apply()
    }

    fun getLastPlayedSongDuration(): Int {
        return sharedPreferences.getInt("lastPlayedSongDuration",-1)
    }

    fun setShouldRestore(shouldRestore: Boolean){
        editor.putBoolean("shouldRestore", shouldRestore)
        editor.apply()
    }

    fun getShouldRestore(): Boolean {
        return sharedPreferences.getBoolean("shouldRestore",false)
    }

    fun setWasClicked(wasClicked: Boolean){
        editor.putBoolean("wasClicked", wasClicked)
        editor.apply()
    }

    fun getWasClicked(): Boolean {
        return sharedPreferences.getBoolean("wasClicked",false)
    }


}