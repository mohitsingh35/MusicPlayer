package com.mohit.musicplayer.ui.MainActivity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mohit.musicplayer.utils.PrefManager
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
