package com.arena.shooter3d

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val engine: GameEngine,
    private val input: InputController,
    private val hudView: HUDView,
    private val soundManager: SoundManager
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(GameRenderer(engine, input,
            { state -> hudView.hudState = state; hudView.postInvalidate() },
            { events -> post { for (e in events) playSoundEvent(e) } }
        ))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private fun playSoundEvent(event: SoundEvent) {
        when (event) {
            SoundEvent.SHOOT_PISTOL -> soundManager.play(0)
            SoundEvent.SHOOT_SHOTGUN -> soundManager.play(1)
            SoundEvent.SHOOT_RIFLE -> soundManager.play(2)
            SoundEvent.HIT_ENEMY -> soundManager.play(3)
            SoundEvent.KILL_ENEMY -> soundManager.play(4)
            SoundEvent.PICKUP_HEALTH -> soundManager.play(5)
            SoundEvent.PICKUP_AMMO -> soundManager.play(5)
            SoundEvent.PLAYER_HURT -> soundManager.play(6)
            SoundEvent.WAVE_START -> soundManager.play(7)
            SoundEvent.GAME_OVER -> soundManager.play(8)
            SoundEvent.WEAPON_SWITCH -> soundManager.play(9)
            SoundEvent.HEADSHOT -> soundManager.play(10)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ai = event.actionIndex
        val pid = event.getPointerId(ai)
        val x = event.getX(ai); val y = event.getY(ai)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                when (engine.gameState) {
                    GameState.MENU -> { engine.startGame(); return true }
                    GameState.GAME_OVER -> { engine.startGame(); return true }
                    GameState.PAUSED -> {
                        if (input.pauseBtnX - input.pauseBtnRadius - 12 < x &&
                            x < input.pauseBtnX + input.pauseBtnRadius + 12 &&
                            input.pauseBtnY - input.pauseBtnRadius - 12 < y &&
                            y < input.pauseBtnY + input.pauseBtnRadius + 12) {
                            engine.togglePause()
                        }
                        return true
                    }
                    GameState.PLAYING -> input.onTouchDown(pid, x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount)
                    input.onTouchMove(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> input.onTouchUp(pid)
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) input.onTouchUp(event.getPointerId(i))
            }
        }
        return true
    }
}
