package com.arena.shooter3d

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class HUDView(context: Context) : View(context) {

    @Volatile var hudState: HUDState? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 80f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt(); textSize = 32f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 40f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt(); textSize = 36f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val healthBarBg = RectF()
    private val healthBarFg = RectF()
    private val minimapRect = RectF()
    private val btnPath = Path()

    override fun onDraw(canvas: Canvas) {
        val hs = hudState ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        when (hs.gameState) {
            GameState.MENU -> drawMenu(canvas, w, h, hs)
            GameState.PLAYING -> drawPlaying(canvas, w, h, hs)
            GameState.GAME_OVER -> drawGameOver(canvas, w, h, hs)
        }
    }

    private fun drawMenu(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        paint.color = 0xDD0A0A1A.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        titlePaint.color = 0xFFFF1744.toInt()
        canvas.drawText("ARENA SHOOTER 3D", w / 2, h * 0.35f, titlePaint)

        subtitlePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("TAP TO START", w / 2, h * 0.55f, subtitlePaint)

        if (hs.highScore > 0) {
            subtitlePaint.color = 0xFF00E5FF.toInt()
            canvas.drawText("HIGH SCORE: ${hs.highScore}", w / 2, h * 0.68f, subtitlePaint)
        }

        subtitlePaint.color = 0xFF888888.toInt()
        subtitlePaint.textSize = 24f
        canvas.drawText("LEFT: MOVE  |  RIGHT: AIM & FIRE  |  BUTTON: SWITCH WEAPON", w / 2, h * 0.85f, subtitlePaint)
        subtitlePaint.textSize = 32f
    }

    private fun drawPlaying(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        drawHealthBar(canvas, w, h, hs)
        drawAmmoDisplay(canvas, w, h, hs)
        drawScore(canvas, w, h, hs)
        drawWaveInfo(canvas, w, h, hs)
        drawMinimap(canvas, w, h, hs)
        drawWeaponButton(canvas, w, h, hs)
        drawCombo(canvas, w, h, hs)
    }

    private fun drawHealthBar(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val barW = 220f
        val barH = 18f
        val x = 30f
        val y = h - 50f

        healthBarBg.set(x, y, x + barW, y + barH)
        paint.color = 0x66000000.toInt()
        canvas.drawRoundRect(healthBarBg, 4f, 4f, paint)

        val ratio = hs.health.toFloat() / hs.maxHealth
        healthBarFg.set(x + 2, y + 2, x + 2 + (barW - 4) * ratio, y + barH - 2)
        paint.color = when {
            ratio > 0.6f -> 0xFF4CAF50.toInt()
            ratio > 0.3f -> 0xFFFFC107.toInt()
            else -> 0xFFFF1744.toInt()
        }
        canvas.drawRoundRect(healthBarFg, 3f, 3f, paint)

        textPaint.textSize = 28f
        textPaint.color = Color.WHITE
        canvas.drawText("${hs.health}", x + barW + 12, y + barH - 1, textPaint)

        textPaint.textSize = 20f
        textPaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("HP", x, y - 6, textPaint)
    }

    private fun drawAmmoDisplay(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        textPaint.textSize = 22f
        textPaint.color = 0xFF00E5FF.toInt()
        val ammoText = if (hs.ammo == -1) "INF" else "${hs.ammo}"
        canvas.drawText(hs.weaponName, 30f, h - 90f, textPaint)
        textPaint.textSize = 36f
        textPaint.color = Color.WHITE
        canvas.drawText(ammoText, 30f, h - 110f, textPaint)
    }

    private fun drawScore(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        scorePaint.textSize = 42f
        scorePaint.color = Color.WHITE
        canvas.drawText("${hs.score}", w - 30, 55f, scorePaint)

        scorePaint.textSize = 20f
        scorePaint.color = 0xFFB0BEC5.toInt()
        canvas.drawText("SCORE", w - 30, 25f, scorePaint)
    }

    private fun drawCombo(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        if (hs.combo > 1) {
            val alpha = ((hs.comboTimer / 3f) * 255).toInt().coerceIn(0, 255)
            comboPaint.color = Color.argb(alpha, 255, 193, 7)
            comboPaint.textSize = 30f + hs.combo.coerceAtMost(10) * 2f
            canvas.drawText("x${hs.combo}", w - 30, 95f, comboPaint)
        }
    }

    private fun drawWaveInfo(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        wavePaint.textSize = 30f
        canvas.drawText("WAVE ${hs.wave}", w / 2, 35f, wavePaint)

        textPaint.textSize = 20f
        textPaint.color = 0xFFB0BEC5.toInt()
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${hs.enemyCount} ENEMIES", w / 2, 60f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawMinimap(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val mapSize = 110f
        val mx = 25f
        val my = 25f
        val mapCx = mx + mapSize / 2
        val mapCy = my + mapSize / 2

        minimapRect.set(mx, my, mx + mapSize, my + mapSize)
        paint.color = 0x44000000.toInt()
        canvas.drawRoundRect(minimapRect, 6f, 6f, paint)

        paint.color = 0x220088FF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(minimapRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        val scale = mapSize / (hs.arenaHalf * 2)

        for ((ex, ez) in hs.enemyPositions) {
            val rx = mapCx + (ex - hs.playerX) * scale
            val ry = mapCy + (ez - hs.playerZ) * scale
            if (rx > mx + 3 && rx < mx + mapSize - 3 && ry > my + 3 && ry < my + mapSize - 3) {
                paint.color = 0xFFFF1744.toInt()
                canvas.drawCircle(rx, ry, 3f, paint)
            }
        }

        for ((px, pz, typeOrd) in hs.pickupPositions) {
            if (typeOrd < 0) continue
            val rx = mapCx + (px - hs.playerX) * scale
            val ry = mapCy + (pz - hs.playerZ) * scale
            if (rx > mx + 3 && rx < mx + mapSize - 3 && ry > my + 3 && ry < my + mapSize - 3) {
                paint.color = if (typeOrd == 0) 0xFF4CAF50.toInt() else 0xFF2196F3.toInt()
                canvas.drawCircle(rx, ry, 2.5f, paint)
            }
        }

        paint.color = 0xFF00E5FF.toInt()
        canvas.drawCircle(mapCx, mapCy, 4f, paint)

        val dirLen = 8f
        val dx = sin(hs.playerYaw) * dirLen
        val dy = -cos(hs.playerYaw) * dirLen
        paint.strokeWidth = 2f
        canvas.drawLine(mapCx, mapCy, mapCx + dx, mapCy + dy, paint)
        paint.strokeWidth = 1f
    }

    private fun drawWeaponButton(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val bx = w - 160f
        val by = h - 260f
        val br = 50f

        paint.color = 0x44FFFFFF.toInt()
        canvas.drawCircle(bx, by, br, paint)
        paint.color = 0x88FFFFFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(bx, by, br, paint)
        paint.style = Paint.Style.FILL

        textPaint.textSize = 16f
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SWITCH", bx, by + 6, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        paint.color = 0xCC0A0A1A.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        titlePaint.color = 0xFFFF1744.toInt()
        titlePaint.textSize = 70f
        canvas.drawText("GAME OVER", w / 2, h * 0.3f, titlePaint)
        titlePaint.textSize = 80f

        subtitlePaint.color = Color.WHITE
        subtitlePaint.textSize = 40f
        canvas.drawText("SCORE: ${hs.score}", w / 2, h * 0.45f, subtitlePaint)

        subtitlePaint.textSize = 28f
        subtitlePaint.color = 0xFF00E5FF.toInt()
        canvas.drawText("WAVE ${hs.wave}  |  ${hs.kills} KILLS", w / 2, h * 0.55f, subtitlePaint)

        if (hs.score >= hs.highScore && hs.score > 0) {
            subtitlePaint.color = 0xFFFFC107.toInt()
            canvas.drawText("NEW HIGH SCORE!", w / 2, h * 0.65f, subtitlePaint)
        }

        subtitlePaint.color = 0xFFFFFFFF.toInt()
        subtitlePaint.textSize = 30f
        canvas.drawText("TAP TO RESTART", w / 2, h * 0.78f, subtitlePaint)
        subtitlePaint.textSize = 32f
    }
}
