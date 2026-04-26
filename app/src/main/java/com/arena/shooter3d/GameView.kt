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
    private val hudView: HUDView
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(GameRenderer(engine, input) { state ->
            hudView.hudState = state
            hudView.postInvalidate()
        })
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (engine.gameState == GameState.MENU) {
                    engine.startGame()
                    return true
                }
                if (engine.gameState == GameState.GAME_OVER) {
                    engine.startGame()
                    return true
                }
                input.onTouchDown(pointerId, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    input.onTouchMove(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                input.onTouchUp(pointerId)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    input.onTouchUp(event.getPointerId(i))
                }
            }
        }
        return true
    }
}
