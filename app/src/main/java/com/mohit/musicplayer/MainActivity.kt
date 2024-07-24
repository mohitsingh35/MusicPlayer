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

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        PrefManager.initialize(this)
    }

}
