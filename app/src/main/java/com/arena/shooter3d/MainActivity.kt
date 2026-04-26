package com.arena.shooter3d

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var hudView: HUDView
    private lateinit var soundManager: SoundManager
    private val engine = GameEngine()
    private val input = InputController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        soundManager = SoundManager(this)
        soundManager.init()

        hudView = HUDView(this, input)
        gameView = GameView(this, engine, input, hudView, soundManager)

        val root = findViewById<FrameLayout>(R.id.rootLayout)
        root.addView(gameView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(hudView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun onResume() { super.onResume(); gameView.onResume(); hideSystemUI() }
    override fun onPause() { super.onPause(); gameView.onPause() }
    override fun onDestroy() { super.onDestroy(); soundManager.release() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }
}
