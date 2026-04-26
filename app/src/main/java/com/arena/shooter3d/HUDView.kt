package com.arena.shooter3d

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class HUDView(context: Context, private val input: InputController) : View(context) {

    @Volatile var hudState: HUDState? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF1744.toInt(); textSize = 72f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt(); textSize = 30f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); textAlign = Paint.Align.CENTER }
    private val scoreP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 38f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
    private val comboP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
    private val waveP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt(); textSize = 28f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        val hs = hudState ?: return
        val w = width.toFloat(); val h = height.toFloat()
        when (hs.gameState) {
            GameState.MENU -> drawMenu(canvas, w, h, hs)
            GameState.PLAYING -> drawPlaying(canvas, w, h, hs)
            GameState.PAUSED -> { drawPlaying(canvas, w, h, hs); drawPauseOverlay(canvas, w, h) }
            GameState.GAME_OVER -> drawGameOver(canvas, w, h, hs)
        }
    }

    private fun drawMenu(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        p.color = 0xDD0A0A1A.toInt(); canvas.drawRect(0f, 0f, w, h, p)
        titleP.textSize = 72f; titleP.color = 0xFFFF1744.toInt()
        canvas.drawText("ARENA SHOOTER 3D", w / 2, h * 0.32f, titleP)
        subP.color = Color.WHITE; subP.textSize = 30f
        canvas.drawText("TAP TO START", w / 2, h * 0.52f, subP)
        if (hs.highScore > 0) { subP.color = 0xFF00E5FF.toInt(); canvas.drawText("HIGH SCORE: ${hs.highScore}", w / 2, h * 0.64f, subP) }
        subP.color = 0xFF777777.toInt(); subP.textSize = 22f
        canvas.drawText("LEFT JOYSTICK: MOVE  |  RIGHT SIDE: AIM  |  FIRE BUTTON: SHOOT", w / 2, h * 0.82f, subP)
        subP.textSize = 30f
    }

    private fun drawPlaying(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        drawCrosshair(canvas, w, h)
        drawJoystick(canvas)
        drawFireButton(canvas, w, h, hs)
        drawWeaponSwitch(canvas, w, h, hs)
        drawPauseBtn(canvas, w)
        drawHealthBar(canvas, w, h, hs)
        drawAmmo(canvas, h, hs)
        drawScore(canvas, w, hs)
        drawCombo(canvas, w, hs)
        drawWave(canvas, w, hs)
        drawMinimap(canvas, w, hs)
    }

    private fun drawCrosshair(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f

        p.color = 0xE0FFFFFF.toInt()
        val g = 10f; val s = 26f; val t = 2.5f
        canvas.drawRect(cx - s, cy - t, cx - g, cy + t, p)
        canvas.drawRect(cx + g, cy - t, cx + s, cy + t, p)
        canvas.drawRect(cx - t, cy - s, cx + t, cy - g, p)
        canvas.drawRect(cx - t, cy + g, cx + t, cy + s, p)

        p.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 3f, p)
    }

    private fun drawJoystick(canvas: Canvas) {
        val ic = input
        if (ic.joyActive) {
            p.color = 0x33FFFFFF.toInt()
            canvas.drawCircle(ic.joyCenterX, ic.joyCenterY, ic.joyOuterRadius, p)
            p.color = 0x22FFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
            canvas.drawCircle(ic.joyCenterX, ic.joyCenterY, ic.joyOuterRadius, p)
            p.style = Paint.Style.FILL; p.color = 0x88FFFFFF.toInt()
            canvas.drawCircle(ic.joyThumbX, ic.joyThumbY, ic.joyInnerRadius, p)
        } else {
            val cx = 280f; val cy = height - 260f
            p.color = 0x1AFFFFFF.toInt()
            canvas.drawCircle(cx, cy, ic.joyOuterRadius, p)
            p.color = 0x15FFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f
            canvas.drawCircle(cx, cy, ic.joyOuterRadius, p)
            p.style = Paint.Style.FILL; p.color = 0x25FFFFFF.toInt()
            canvas.drawCircle(cx, cy, ic.joyInnerRadius, p)
        }
    }

    private fun drawFireButton(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val bx = ic().fireBtnX; val by = ic().fireBtnY; val br = ic().fireBtnRadius
        p.color = if (ic().isFiring) 0x66FF1744.toInt() else 0x33FF1744.toInt()
        canvas.drawCircle(bx, by, br, p)
        p.color = if (ic().isFiring) 0xCCFF1744.toInt() else 0x66FF1744.toInt()
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f
        canvas.drawCircle(bx, by, br, p); p.style = Paint.Style.FILL

        p.color = if (ic().isFiring) 0xDDFFFFFF.toInt() else 0x88FFFFFF.toInt()
        val cs = 32f; val ct = 5f; val cg = 10f
        canvas.drawRect(bx - cs, by - ct, bx - cg, by + ct, p)
        canvas.drawRect(bx + cg, by - ct, bx + cs, by + ct, p)
        canvas.drawRect(bx - ct, by - cs, bx + ct, by - cg, p)
        canvas.drawRect(bx - ct, by + cg, bx + ct, by + cs, p)
        canvas.drawCircle(bx, by, 5f, p)
    }

    private fun drawWeaponSwitch(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val bx = ic().switchBtnX; val by = ic().switchBtnY; val br = ic().switchBtnRadius
        p.color = 0x44FFFFFF.toInt(); canvas.drawCircle(bx, by, br, p)
        p.color = 0x66FFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        canvas.drawCircle(bx, by, br, p); p.style = Paint.Style.FILL
        tp.textSize = 20f; tp.color = 0xDDFFFFFF.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText(hs.weaponName, bx, by + 7, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawPauseBtn(canvas: Canvas, w: Float) {
        val bx = w - 50f; val by = 50f; val br = 32f
        input.pauseBtnX = bx; input.pauseBtnY = by
        p.color = 0x55000000.toInt(); canvas.drawCircle(bx, by, br, p)
        p.color = 0x44FFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        canvas.drawCircle(bx, by, br, p); p.style = Paint.Style.FILL
        p.color = 0xDDFFFFFF.toInt()
        val hs = hudState
        if (hs != null && hs.gameState == GameState.PAUSED) {
            val path = android.graphics.Path()
            path.moveTo(bx - 7, by - 11)
            path.lineTo(bx + 11, by)
            path.lineTo(bx - 7, by + 11)
            path.close()
            canvas.drawPath(path, p)
        } else {
            canvas.drawRect(bx - 8, by - 11, bx - 3, by + 11, p)
            canvas.drawRect(bx + 3, by - 11, bx + 8, by + 11, p)
        }
    }

    private fun drawHealthBar(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val barW = 200f; val barH = 16f; val x = 30f; val y = h - 48f
        p.color = 0x55000000.toInt(); canvas.drawRoundRect(x, y, x + barW, y + barH, 4f, 4f, p)
        val ratio = hs.health.toFloat() / hs.maxHealth
        p.color = when { ratio > 0.6f -> 0xFF4CAF50.toInt(); ratio > 0.3f -> 0xFFFFC107.toInt(); else -> 0xFFFF1744.toInt() }
        canvas.drawRoundRect(x + 2, y + 2, x + 2 + (barW - 4) * ratio, y + barH - 2, 3f, 3f, p)
        tp.textSize = 24f; tp.color = Color.WHITE
        canvas.drawText("${hs.health}", x + barW + 10, y + barH - 1, tp)
        tp.textSize = 18f; tp.color = 0xFFB0BEC5.toInt(); canvas.drawText("HP", x, y - 5, tp)
    }

    private fun drawAmmo(canvas: Canvas, h: Float, hs: HUDState) {
        tp.textSize = 32f; tp.color = Color.WHITE
        canvas.drawText(if (hs.ammo == -1) "INF" else "${hs.ammo}", 30f, h - 105f, tp)
        tp.textSize = 18f; tp.color = 0xFF00E5FF.toInt()
        canvas.drawText(hs.weaponName, 30f, h - 78f, tp)
    }

    private fun drawScore(canvas: Canvas, w: Float, hs: HUDState) {
        val rx = w - 18f; val top = 95f
        p.color = 0x44000000.toInt()
        canvas.drawRoundRect(rx - 130f, top - 18f, rx + 5f, top + 50f, 6f, 6f, p)
        scoreP.textSize = 16f; scoreP.color = 0xFFB0BEC5.toInt()
        canvas.drawText("SCORE", rx, top, scoreP)
        scoreP.textSize = 36f; scoreP.color = Color.WHITE
        canvas.drawText("${hs.score}", rx, top + 38f, scoreP)
    }

    private fun drawCombo(canvas: Canvas, w: Float, hs: HUDState) {
        if (hs.combo > 1) {
            val a = ((hs.comboTimer / 3f) * 255).toInt().coerceIn(0, 255)
            comboP.color = Color.argb(a, 255, 193, 7)
            comboP.textSize = 28f + hs.combo.coerceAtMost(10) * 2f
            canvas.drawText("x${hs.combo}", w - 18f, 165f, comboP)
        }
    }

    private fun drawWave(canvas: Canvas, w: Float, hs: HUDState) {
        waveP.textSize = 26f; canvas.drawText("WAVE ${hs.wave}", w / 2, 32f, waveP)
        tp.textSize = 17f; tp.color = 0xFFB0BEC5.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("${hs.enemyCount} ENEMIES", w / 2, 54f, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawMinimap(canvas: Canvas, w: Float, hs: HUDState) {
        val ms = 240f; val mx = 15f; val my = 15f; val mcx = mx + ms / 2; val mcy = my + ms / 2
        p.color = 0x55000000.toInt(); canvas.drawRoundRect(mx, my, mx + ms, my + ms, 8f, 8f, p)
        p.color = 0x250088FF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        canvas.drawRoundRect(mx, my, mx + ms, my + ms, 8f, 8f, p); p.style = Paint.Style.FILL
        val sc = ms / (hs.arenaHalf * 2)
        for ((ex, ez) in hs.enemyPositions) {
            val rx = mcx + (ex - hs.playerX) * sc; val ry = mcy + (ez - hs.playerZ) * sc
            if (rx > mx + 5 && rx < mx + ms - 5 && ry > my + 5 && ry < my + ms - 5) {
                p.color = 0xFFFF1744.toInt(); canvas.drawCircle(rx, ry, 4.5f, p)
            }
        }
        for ((px, pz, t) in hs.pickupPositions) {
            if (t < 0) continue; val rx = mcx + (px - hs.playerX) * sc; val ry = mcy + (pz - hs.playerZ) * sc
            if (rx > mx + 5 && rx < mx + ms - 5 && ry > my + 5 && ry < my + ms - 5) {
                p.color = if (t == 0) 0xFF4CAF50.toInt() else 0xFF2196F3.toInt(); canvas.drawCircle(rx, ry, 3f, p)
            }
        }
        p.color = 0xFF00E5FF.toInt(); canvas.drawCircle(mcx, mcy, 5.5f, p)
        val dl = 12f; val dx = sin(hs.playerYaw) * dl; val dy = -cos(hs.playerYaw) * dl
        p.strokeWidth = 3f; canvas.drawLine(mcx, mcy, mcx + dx, mcy + dy, p); p.strokeWidth = 1f
    }

    private fun drawPauseOverlay(canvas: Canvas, w: Float, h: Float) {
        p.color = 0xAA0A0A1A.toInt(); canvas.drawRect(0f, 0f, w, h, p)
        titleP.textSize = 60f; titleP.color = Color.WHITE
        canvas.drawText("PAUSED", w / 2, h * 0.4f, titleP)
        subP.color = Color.WHITE; subP.textSize = 28f
        canvas.drawText("TAP PAUSE BUTTON TO RESUME", w / 2, h * 0.58f, subP)

        val bx = ic().pauseBtnX; val by = ic().pauseBtnY; val br = ic().pauseBtnRadius
        p.color = 0xFF333333.toInt(); canvas.drawCircle(bx, by, br + 4, p)
        p.color = 0xFF00E5FF.toInt(); canvas.drawCircle(bx, by, br, p)
        p.color = 0xFFFFFFFF.toInt()
        val path = android.graphics.Path()
        path.moveTo(bx - 8, by - 12)
        path.lineTo(bx + 12, by)
        path.lineTo(bx - 8, by + 12)
        path.close()
        canvas.drawPath(path, p)
    }

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        p.color = 0xCC0A0A1A.toInt(); canvas.drawRect(0f, 0f, w, h, p)
        titleP.textSize = 64f; titleP.color = 0xFFFF1744.toInt()
        canvas.drawText("GAME OVER", w / 2, h * 0.3f, titleP)
        subP.color = Color.WHITE; subP.textSize = 36f
        canvas.drawText("SCORE: ${hs.score}", w / 2, h * 0.45f, subP)
        subP.textSize = 26f; subP.color = 0xFF00E5FF.toInt()
        canvas.drawText("WAVE ${hs.wave}  |  ${hs.kills} KILLS", w / 2, h * 0.55f, subP)
        if (hs.score >= hs.highScore && hs.score > 0) {
            subP.color = 0xFFFFC107.toInt(); canvas.drawText("NEW HIGH SCORE!", w / 2, h * 0.64f, subP)
        }
        subP.color = Color.WHITE; subP.textSize = 28f
        canvas.drawText("TAP TO RESTART", w / 2, h * 0.76f, subP)
    }

    private fun ic() = input
}
