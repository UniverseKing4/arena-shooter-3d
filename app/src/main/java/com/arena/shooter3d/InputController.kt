package com.arena.shooter3d

import kotlin.math.sqrt

class InputController {
    var moveX = 0f
    var moveZ = 0f
    var lookDeltaX = 0f
    var lookDeltaY = 0f
    var isFiring = false
    var switchWeaponTapped = false

    private var movePointerId = -1
    private var moveStartX = 0f
    private var moveStartY = 0f
    private var lookPointerId = -1
    private var lastLookX = 0f
    private var lastLookY = 0f

    private var screenW = 1f
    private var screenH = 1f
    private val joystickRadius = 140f
    private val lookSensitivity = 0.0045f

    private var weaponBtnX = 0f
    private var weaponBtnY = 0f
    private val weaponBtnRadius = 70f

    fun setScreenSize(w: Int, h: Int) {
        screenW = w.toFloat()
        screenH = h.toFloat()
        weaponBtnX = screenW - 160f
        weaponBtnY = screenH - 260f
    }

    fun onTouchDown(pointerId: Int, x: Float, y: Float) {
        val dx = x - weaponBtnX
        val dy = y - weaponBtnY
        if (dx * dx + dy * dy < weaponBtnRadius * weaponBtnRadius) {
            switchWeaponTapped = true
            return
        }

        if (x < screenW * 0.4f) {
            movePointerId = pointerId
            moveStartX = x
            moveStartY = y
            moveX = 0f
            moveZ = 0f
        } else {
            lookPointerId = pointerId
            lastLookX = x
            lastLookY = y
            isFiring = true
        }
    }

    fun onTouchMove(pointerId: Int, x: Float, y: Float) {
        when (pointerId) {
            movePointerId -> {
                val dx = x - moveStartX
                val dy = y - moveStartY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > joystickRadius) {
                    moveX = dx / dist
                    moveZ = dy / dist
                } else {
                    moveX = dx / joystickRadius
                    moveZ = dy / joystickRadius
                }
            }
            lookPointerId -> {
                lookDeltaX += (x - lastLookX) * lookSensitivity
                lookDeltaY += (y - lastLookY) * lookSensitivity
                lastLookX = x
                lastLookY = y
            }
        }
    }

    fun onTouchUp(pointerId: Int) {
        when (pointerId) {
            movePointerId -> {
                movePointerId = -1
                moveX = 0f
                moveZ = 0f
            }
            lookPointerId -> {
                lookPointerId = -1
                lookDeltaX = 0f
                lookDeltaY = 0f
                isFiring = false
            }
        }
    }

    fun consumeLookDelta(): Pair<Float, Float> {
        val dx = lookDeltaX
        val dy = lookDeltaY
        lookDeltaX = 0f
        lookDeltaY = 0f
        return dx to dy
    }

    fun consumeWeaponSwitch(): Boolean {
        val t = switchWeaponTapped
        switchWeaponTapped = false
        return t
    }
}
