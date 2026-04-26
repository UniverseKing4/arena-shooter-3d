package com.arena.shooter3d

import kotlin.math.sqrt

class InputController {
    var joyX = 0f; var joyY = 0f
    var joyCenterX = 0f; var joyCenterY = 0f
    var joyThumbX = 0f; var joyThumbY = 0f
    var joyActive = false

    var lookDeltaX = 0f; var lookDeltaY = 0f
    var isFiring = false
    var weaponSwitchTapped = false
    var pauseTapped = false

    private var movePointerId = -1
    private var lookPointerId = -1
    private var firePointerId = -1
    private var lastLookX = 0f; private var lastLookY = 0f

    private var screenW = 1f; private var screenH = 1f
    private val lookSensitivity = 0.005f

    var fireBtnX = 0f; var fireBtnY = 0f; val fireBtnRadius = 62f
    var switchBtnX = 0f; var switchBtnY = 0f; val switchBtnRadius = 40f
    var pauseBtnX = 0f; var pauseBtnY = 0f; val pauseBtnRadius = 30f
    val joyOuterRadius = 75f; val joyInnerRadius = 28f

    fun setScreenSize(w: Int, h: Int) {
        screenW = w.toFloat(); screenH = h.toFloat()
        fireBtnX = screenW - 175f; fireBtnY = screenH - 150f
        switchBtnX = screenW - 175f; switchBtnY = screenH - 290f
        pauseBtnX = screenW - 55f; pauseBtnY = 55f
    }

    fun onTouchDown(pointerId: Int, x: Float, y: Float) {
        if (inCircle(x, y, fireBtnX, fireBtnY, fireBtnRadius + 18f)) {
            firePointerId = pointerId; isFiring = true; return
        }
        if (inCircle(x, y, switchBtnX, switchBtnY, switchBtnRadius + 12f)) {
            weaponSwitchTapped = true; return
        }
        if (inCircle(x, y, pauseBtnX, pauseBtnY, pauseBtnRadius + 12f)) {
            pauseTapped = true; return
        }
        if (x < screenW * 0.4f) {
            movePointerId = pointerId
            joyCenterX = x; joyCenterY = y
            joyThumbX = x; joyThumbY = y
            joyActive = true; joyX = 0f; joyY = 0f
            return
        }
        lookPointerId = pointerId; lastLookX = x; lastLookY = y
    }

    fun onTouchMove(pointerId: Int, x: Float, y: Float) {
        when (pointerId) {
            movePointerId -> {
                val dx = x - joyCenterX; val dy = y - joyCenterY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > joyOuterRadius) {
                    joyX = dx / dist; joyY = dy / dist
                    joyThumbX = joyCenterX + joyX * joyOuterRadius
                    joyThumbY = joyCenterY + joyY * joyOuterRadius
                } else {
                    joyX = dx / joyOuterRadius; joyY = dy / joyOuterRadius
                    joyThumbX = x; joyThumbY = y
                }
            }
            lookPointerId -> {
                lookDeltaX += (x - lastLookX) * lookSensitivity
                lookDeltaY += (y - lastLookY) * lookSensitivity
                lastLookX = x; lastLookY = y
            }
        }
    }

    fun onTouchUp(pointerId: Int) {
        when (pointerId) {
            movePointerId -> { movePointerId = -1; joyX = 0f; joyY = 0f; joyActive = false }
            lookPointerId -> { lookPointerId = -1; lookDeltaX = 0f; lookDeltaY = 0f }
            firePointerId -> { firePointerId = -1; isFiring = false }
        }
    }

    fun consumeLookDelta(): Pair<Float, Float> {
        val dx = lookDeltaX; val dy = lookDeltaY
        lookDeltaX = 0f; lookDeltaY = 0f; return dx to dy
    }
    fun consumeWeaponSwitch(): Boolean { val t = weaponSwitchTapped; weaponSwitchTapped = false; return t }
    fun consumePause(): Boolean { val t = pauseTapped; pauseTapped = false; return t }

    private fun inCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx; val dy = y - cy; return dx * dx + dy * dy < r * r
    }
}
