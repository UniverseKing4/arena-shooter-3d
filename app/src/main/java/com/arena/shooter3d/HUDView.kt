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
        val time = System.currentTimeMillis()
        val t = (time % 10000).toFloat() / 10000f
        val slowT = (time % 4000).toFloat() / 4000f
        val fastT = (time % 1500).toFloat() / 1500f

        // Full screen solid dark background
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0xFF050510.toInt()
        canvas.drawRect(0f, 0f, w, h, p)

        // Animated grid lines
        p.color = 0x18FF1744.toInt(); p.strokeWidth = 1f; p.style = Paint.Style.STROKE
        val gridSize = 90f
        val offsetX = (t * gridSize) % gridSize
        val offsetY = (t * gridSize * 0.7f) % gridSize
        var gx = -gridSize + offsetX
        while (gx < w + gridSize) { canvas.drawLine(gx, 0f, gx, h, p); gx += gridSize }
        var gy = -gridSize + offsetY
        while (gy < h + gridSize) { canvas.drawLine(0f, gy, w, gy, p); gy += gridSize }
        p.style = Paint.Style.FILL

        // Animated floating particles
        for (i in 0 until 22) {
            val seed = i * 1.17f
            val px = ((sin((t + seed * 0.37f) * 6.28f) * 0.42f + 0.5f) * w)
            val py = ((cos((t + seed * 0.53f) * 6.28f) * 0.42f + 0.5f) * h)
            val pa = (sin((t + seed * 0.2f) * 6.28f) * 0.5f + 0.5f)
            val ps = 2.5f + sin((t + seed * 0.6f) * 6.28f) * 2f
            p.color = Color.argb((pa * 100).toInt().coerceIn(0, 255), 255, 23, 68)
            canvas.drawCircle(px, py, ps, p)
        }

        // Red vignette glow at edges
        val vigAlpha = (sin(slowT * 6.28f) * 20 + 35).toInt().coerceIn(15, 55)
        val vigColor = Color.argb(vigAlpha, 255, 23, 68)
        val vigClear = Color.argb(0, 255, 23, 68)
        val edgeW = w * 0.22f
        p.shader = LinearGradient(0f, 0f, edgeW, 0f, vigColor, vigClear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, edgeW, h, p)
        p.shader = LinearGradient(w, 0f, w - edgeW, 0f, vigColor, vigClear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(w - edgeW, 0f, w, h, p)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.15f, vigColor, vigClear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.15f, p)
        p.shader = LinearGradient(0f, h, 0f, h * 0.85f, vigColor, vigClear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.85f, w, h, p)
        p.shader = null

        // Top decorative lines
        p.color = 0x55FF1744.toInt(); p.strokeWidth = 2.5f; p.style = Paint.Style.STROKE
        val lineW = w * 0.38f
        canvas.drawLine(w / 2 - lineW, h * 0.12f, w / 2 + lineW, h * 0.12f, p)
        p.color = 0x33FF1744.toInt(); p.strokeWidth = 1.5f
        canvas.drawLine(w / 2 - lineW * 0.65f, h * 0.145f, w / 2 + lineW * 0.65f, h * 0.145f, p)
        p.style = Paint.Style.FILL

        // Small dot decorations on top line
        p.color = 0xAAFF1744.toInt()
        canvas.drawCircle(w / 2 - lineW, h * 0.12f, 4f, p)
        canvas.drawCircle(w / 2 + lineW, h * 0.12f, 4f, p)

        // Game title with glow effect - BIGGER
        val titleGlow = (sin(slowT * 6.28f) * 0.12f + 0.88f)
        titleP.textSize = 68f
        titleP.textAlign = Paint.Align.CENTER
        // Shadow/glow layers
        titleP.color = Color.argb((titleGlow * 50).toInt(), 255, 23, 68)
        canvas.drawText("ARENA SHOOTER 3D", w / 2, h * 0.24f + 3f, titleP)
        canvas.drawText("ARENA SHOOTER 3D", w / 2 + 2f, h * 0.24f + 2f, titleP)
        // Main title
        titleP.color = Color.argb((titleGlow * 255).toInt(), 255, 23, 68)
        canvas.drawText("ARENA SHOOTER 3D", w / 2, h * 0.24f, titleP)

        // Subtitle - BIGGER
        subP.textSize = 24f; subP.color = 0xAAB0BEC5.toInt(); subP.textAlign = Paint.Align.CENTER
        canvas.drawText("SURVIVE THE ARENA", w / 2, h * 0.31f, subP)

        // Animated crosshair decoration behind play button
        val cx = w / 2f; val cy = h * 0.52f
        val crossRot = t * 360f
        val crossAlpha = (sin(fastT * 6.28f) * 25 + 55).toInt().coerceIn(30, 80)

        // Outer rotating ring
        p.color = Color.argb(crossAlpha, 255, 23, 68); p.strokeWidth = 1.8f; p.style = Paint.Style.STROKE
        val outerR = 105f
        canvas.drawCircle(cx, cy, outerR, p)
        p.style = Paint.Style.FILL

        // Rotating tick marks
        p.color = Color.argb(70, 255, 23, 68); p.strokeWidth = 2.5f; p.style = Paint.Style.STROKE
        for (i in 0 until 12) {
            val angle = (i * 30f + crossRot) * (PI.toFloat() / 180f)
            val r1 = outerR + 6f; val r2 = outerR + 15f
            canvas.drawLine(cx + cos(angle) * r1, cy + sin(angle) * r1, cx + cos(angle) * r2, cy + sin(angle) * r2, p)
        }

        // Counter-rotating inner dashes
        p.color = Color.argb(40, 255, 23, 68); p.strokeWidth = 1.5f
        for (i in 0 until 8) {
            val angle = (i * 45f - crossRot * 0.5f) * (PI.toFloat() / 180f)
            val r1 = outerR - 18f; val r2 = outerR - 8f
            canvas.drawLine(cx + cos(angle) * r1, cy + sin(angle) * r1, cx + cos(angle) * r2, cy + sin(angle) * r2, p)
        }
        p.style = Paint.Style.FILL

        // Play button - pulsing circle with triangle
        val btnR = 72f
        val pulse = sin(fastT * 6.28f) * 0.06f + 1f
        val btnRPulsed = btnR * pulse

        // Outer glow layers
        p.color = Color.argb(25, 255, 23, 68)
        canvas.drawCircle(cx, cy, btnRPulsed + 20f, p)
        p.color = Color.argb(45, 255, 23, 68)
        canvas.drawCircle(cx, cy, btnRPulsed + 10f, p)
        p.color = Color.argb(65, 255, 23, 68)
        canvas.drawCircle(cx, cy, btnRPulsed + 4f, p)

        // Button fill
        p.color = 0xEEFF1744.toInt()
        canvas.drawCircle(cx, cy, btnRPulsed, p)

        // Inner highlight
        p.color = 0x22FFFFFF.toInt()
        canvas.drawCircle(cx, cy - 10f, btnRPulsed * 0.55f, p)

        // Button border
        p.color = 0xFFFFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f
        canvas.drawCircle(cx, cy, btnRPulsed, p)
        p.style = Paint.Style.FILL

        // Play triangle
        p.color = 0xFFFFFFFF.toInt()
        val triSize = 34f
        val playPath = android.graphics.Path()
        playPath.moveTo(cx - triSize * 0.35f, cy - triSize)
        playPath.lineTo(cx + triSize * 0.9f, cy)
        playPath.lineTo(cx - triSize * 0.35f, cy + triSize)
        playPath.close()
        canvas.drawPath(playPath, p)

        // "PLAY" text below button - BIGGER
        subP.textSize = 28f; subP.color = 0xDDFFFFFF.toInt(); subP.textAlign = Paint.Align.CENTER
        canvas.drawText("PLAY", cx, cy + btnRPulsed + 40f, subP)

        // High score display - FIXED alignment, centered, bigger
        if (hs.highScore > 0) {
            val hsY = h * 0.76f
            val panelW = 200f; val panelH = 56f

            // Background panel
            p.color = 0x44000000.toInt()
            canvas.drawRoundRect(cx - panelW, hsY - panelH / 2, cx + panelW, hsY + panelH / 2, 12f, 12f, p)
            p.color = 0x55FF1744.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f
            canvas.drawRoundRect(cx - panelW, hsY - panelH / 2, cx + panelW, hsY + panelH / 2, 12f, 12f, p)
            p.style = Paint.Style.FILL

            // Trophy diamond icon
            p.color = 0xFFFFC107.toInt()
            val tPath = android.graphics.Path()
            val tCx = cx - 130f; val tCy = hsY
            tPath.moveTo(tCx, tCy - 10f)
            tPath.lineTo(tCx + 8f, tCy)
            tPath.lineTo(tCx, tCy + 10f)
            tPath.lineTo(tCx - 8f, tCy)
            tPath.close()
            canvas.drawPath(tPath, p)

            // "HIGH SCORE" label - centered
            subP.textSize = 22f; subP.color = 0xDDB0BEC5.toInt(); subP.textAlign = Paint.Align.CENTER
            canvas.drawText("HIGH SCORE", cx + 10f, hsY - 6f, subP)

            // Score value - centered below label
            subP.textSize = 30f; subP.color = 0xFF00E5FF.toInt(); subP.textAlign = Paint.Align.CENTER
            canvas.drawText("${hs.highScore}", cx + 10f, hsY + 24f, subP)
        }

        // Controls hint at bottom - BIGGER text
        val ctrlY = h * 0.91f
        p.color = 0x28FFFFFF.toInt()
        canvas.drawRoundRect(w / 2 - 340f, ctrlY - 20f, w / 2 + 340f, ctrlY + 18f, 10f, 10f, p)
        subP.color = 0x99FFFFFF.toInt(); subP.textSize = 18f; subP.textAlign = Paint.Align.CENTER
        canvas.drawText("MOVE  •  AIM  •  SHOOT  •  JUMP  •  RELOAD  •  SWITCH", w / 2, ctrlY + 6f, subP)

        // Bottom decorative lines
        p.color = 0x44FF1744.toInt(); p.strokeWidth = 2f; p.style = Paint.Style.STROKE
        canvas.drawLine(w * 0.15f, h * 0.965f, w * 0.85f, h * 0.965f, p)
        p.color = 0x22FF1744.toInt(); p.strokeWidth = 1f
        canvas.drawLine(w * 0.25f, h * 0.98f, w * 0.75f, h * 0.98f, p)
        p.style = Paint.Style.FILL

        // Bottom dot decorations
        p.color = 0x88FF1744.toInt()
        canvas.drawCircle(w * 0.15f, h * 0.965f, 3.5f, p)
        canvas.drawCircle(w * 0.85f, h * 0.965f, 3.5f, p)

        // Reset paint state
        subP.textAlign = Paint.Align.CENTER
        p.shader = null; p.style = Paint.Style.FILL

        // Force continuous redraw for animations
        postInvalidate()
    }

    private fun drawPlaying(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        if (hs.damageFlash > 0.01f) drawDamageVignette(canvas, w, h, hs.damageFlash)
        drawCrosshair(canvas, w, h)
        drawJoystick(canvas)
        drawFireButton(canvas, w, h, hs)
        drawJumpButton(canvas, w, h)
        drawReloadButton(canvas, w, h, hs)
        drawWeaponSwitch(canvas, w, h, hs)
        drawPauseBtn(canvas, w)
        if (hs.isReloading) drawReloadingIndicator(canvas, w, h, hs)
        drawHealthBar(canvas, w, h, hs)
        drawAmmo(canvas, h, hs)
        drawScore(canvas, w, hs)
        drawCombo(canvas, w, h, hs)
        drawWave(canvas, w, hs)
        drawMinimap(canvas, w, hs)
        if (hs.isSprinting) drawSprintIndicator(canvas, w, h)
        if (hs.wavePopupTimer > 0f) drawWavePopup(canvas, w, h, hs)
        if (hs.weaponSwitchPopupTimer > 0f) drawWeaponSwitchPopup(canvas, w, h, hs)
        if (hs.lowHealthWarning) drawLowHealthWarning(canvas, w, h)
    }

    private fun drawCrosshair(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f

        p.color = 0xE0FFFFFF.toInt()
        val g = 7f; val s = 18f; val t = 2f
        canvas.drawRect(cx - s, cy - t, cx - g, cy + t, p)
        canvas.drawRect(cx + g, cy - t, cx + s, cy + t, p)
        canvas.drawRect(cx - t, cy - s, cx + t, cy - g, p)
        canvas.drawRect(cx - t, cy + g, cx + t, cy + s, p)

        p.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 2.5f, p)
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
        val barW = 280f; val barH = 22f; val x = 30f; val y = h - 55f
        val dmgShake = if (hs.damageFlash > 0.1f) (sin(hs.damageFlash * 40f) * hs.damageFlash * 6f) else 0f
        val sx = x + dmgShake
        if (hs.damageFlash > 0.3f) {
            p.color = 0x55FF1744.toInt()
            canvas.drawRoundRect(sx - 4f, y - 4f, sx + barW + 4f, y + barH + 4f, 6f, 6f, p)
        }
        p.color = 0x55000000.toInt(); canvas.drawRoundRect(sx, y, sx + barW, y + barH, 4f, 4f, p)
        val ratio = hs.health.toFloat() / hs.maxHealth
        p.color = when { ratio > 0.6f -> 0xFF4CAF50.toInt(); ratio > 0.3f -> 0xFFFFC107.toInt(); else -> 0xFFFF1744.toInt() }
        canvas.drawRoundRect(sx + 2, y + 2, sx + 2 + (barW - 4) * ratio, y + barH - 2, 3f, 3f, p)
        if (ratio <= 0.3f && ratio > 0f) {
            val pulse = (sin(System.currentTimeMillis() / 200f) * 0.3f + 0.7f).coerceIn(0f, 1f)
            p.color = Color.argb((pulse * 80).toInt(), 255, 23, 68)
            canvas.drawRoundRect(sx + 2, y + 2, sx + 2 + (barW - 4) * ratio, y + barH - 2, 3f, 3f, p)
        }
        tp.textSize = 30f; tp.color = Color.WHITE
        canvas.drawText("${hs.health}", sx + barW + 12, y + barH - 1, tp)
        tp.textSize = 22f; tp.color = 0xFFB0BEC5.toInt(); canvas.drawText("HP", sx, y - 6, tp)
    }

    private fun drawAmmo(canvas: Canvas, h: Float, hs: HUDState) {
        tp.textSize = 44f; tp.color = Color.WHITE
        val magText = "${hs.magCurrent}"
        canvas.drawText(magText, 30f, h - 125f, tp)
        val magWidth = tp.measureText(magText)
        tp.textSize = 28f; tp.color = 0xFFB0BEC5.toInt()
        canvas.drawText(" / ${hs.reserveAmmo}", 30f + magWidth, h - 125f, tp)
        tp.textSize = 22f; tp.color = 0xFF00E5FF.toInt()
        canvas.drawText(hs.weaponName, 30f, h - 88f, tp)
    }

    private fun drawScore(canvas: Canvas, w: Float, hs: HUDState) {
        val rx = w - 18f; val top = 120f
        p.color = 0x55000000.toInt()
        canvas.drawRoundRect(rx - 180f, top - 25f, rx + 5f, top + 60f, 6f, 6f, p)
        scoreP.textSize = 26f; scoreP.color = 0xFFB0BEC5.toInt()
        canvas.drawText("SCORE", rx, top, scoreP)
        scoreP.textSize = 40f; scoreP.color = Color.WHITE
        canvas.drawText("${hs.score}", rx, top + 42f, scoreP)
    }

    private fun drawCombo(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        if (hs.combo > 1) {
            val a = ((hs.comboTimer / 3f) * 255).toInt().coerceIn(0, 255)
            val size = 38f + hs.combo.coerceAtMost(15) * 3f
            comboP.color = Color.argb(a, 255, 193, 7)
            comboP.textSize = size
            comboP.textAlign = Paint.Align.CENTER
            canvas.drawText("x${hs.combo} COMBO", w / 2f, 120f, comboP)
            comboP.textAlign = Paint.Align.RIGHT
        }
    }

    private fun drawWave(canvas: Canvas, w: Float, hs: HUDState) {
        waveP.textSize = 34f; canvas.drawText("WAVE ${hs.wave}", w / 2, 38f, waveP)
        tp.textSize = 22f; tp.color = 0xFFB0BEC5.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("${hs.enemyCount} ENEMIES", w / 2, 64f, tp); tp.textAlign = Paint.Align.LEFT
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

    private fun drawJumpButton(canvas: Canvas, w: Float, h: Float) {
        val bx = ic().jumpBtnX; val by = ic().jumpBtnY; val br = ic().jumpBtnRadius
        p.color = 0x3300E5FF.toInt(); canvas.drawCircle(bx, by, br, p)
        p.color = 0x6600E5FF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 4f
        canvas.drawCircle(bx, by, br, p); p.style = Paint.Style.FILL
        p.color = 0xCCFFFFFF.toInt()
        val path = android.graphics.Path()
        path.moveTo(bx, by - 24f)
        path.lineTo(bx - 18f, by + 10f)
        path.lineTo(bx + 18f, by + 10f)
        path.close()
        canvas.drawPath(path, p)
        tp.textSize = 16f; tp.color = 0xAAFFFFFF.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("JUMP", bx, by + 28f, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawWavePopup(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val alpha = (hs.wavePopupTimer / 2.5f * 255).toInt().coerceIn(0, 255)
        val scale = 1f + (1f - hs.wavePopupTimer / 2.5f) * 0.3f
        val size = 68f * scale
        tp.textSize = size; tp.color = Color.argb(alpha, 0, 229, 255); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("WAVE ${hs.wave}", w / 2f, h * 0.25f, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawWeaponSwitchPopup(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val alpha = (hs.weaponSwitchPopupTimer / 1.5f * 255).toInt().coerceIn(0, 255)
        tp.textSize = 32f; tp.color = Color.argb(alpha, 255, 255, 255); tp.textAlign = Paint.Align.CENTER
        canvas.drawText(hs.weaponSwitchPopup, w / 2f, h * 0.82f, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawLowHealthWarning(canvas: Canvas, w: Float, h: Float) {
        val t = (System.currentTimeMillis() % 1885).toFloat() / 300f
        val pulse = sin(t) * 0.2f + 0.8f
        val alpha = (pulse * 240).toInt().coerceIn(150, 240)
        tp.textSize = 28f; tp.color = Color.argb(alpha, 255, 23, 68); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("LOW HEALTH", w / 2f, h * 0.88f, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawDamageVignette(canvas: Canvas, w: Float, h: Float, amt: Float) {
        val a = (amt * 140).toInt().coerceIn(0, 140)
        val red = Color.argb(a, 220, 0, 0)
        val clear = Color.argb(0, 220, 0, 0)
        val edgeW = w * 0.18f; val edgeH = h * 0.18f

        p.shader = LinearGradient(0f, 0f, edgeW, 0f, red, clear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, edgeW, h, p)
        p.shader = LinearGradient(w, 0f, w - edgeW, 0f, red, clear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(w - edgeW, 0f, w, h, p)
        p.shader = LinearGradient(0f, 0f, 0f, edgeH, red, clear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, edgeH, p)
        p.shader = LinearGradient(0f, h, 0f, h - edgeH, red, clear, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - edgeH, w, h, p)
        p.shader = null
    }

    private fun drawSprintIndicator(canvas: Canvas, w: Float, h: Float) {
        tp.textSize = 22f; tp.color = 0xFFFFC107.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("SPRINT", w / 2, h - 30f, tp); tp.textAlign = Paint.Align.LEFT
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
        canvas.drawText("GAME OVER", w / 2, h * 0.28f, titleP)
        subP.color = Color.WHITE; subP.textSize = 36f
        canvas.drawText("SCORE: ${hs.score}", w / 2, h * 0.42f, subP)
        subP.textSize = 26f; subP.color = 0xFF00E5FF.toInt()
        canvas.drawText("WAVE ${hs.wave}  |  ${hs.kills} KILLS  |  ${hs.headshots} HEADSHOTS", w / 2, h * 0.52f, subP)
        if (hs.score >= hs.highScore && hs.score > 0) {
            subP.color = 0xFFFFC107.toInt(); canvas.drawText("NEW HIGH SCORE!", w / 2, h * 0.61f, subP)
        }

        val btnX = w / 2; val btnY = h * 0.74f; val btnW = 220f; val btnH = 60f
        p.color = 0xFFFF1744.toInt()
        canvas.drawRoundRect(btnX - btnW, btnY - btnH / 2, btnX + btnW, btnY + btnH / 2, 12f, 12f, p)
        p.color = 0x33FFFFFF.toInt()
        canvas.drawRoundRect(btnX - btnW + 3, btnY - btnH / 2 + 3, btnX + btnW - 3, btnY + btnH / 2 - 3, 10f, 10f, p)
        subP.color = Color.WHITE; subP.textSize = 34f
        canvas.drawText("RESTART", btnX, btnY + 12f, subP)
    }

    private fun drawReloadButton(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val bx = ic().reloadBtnX; val by = ic().reloadBtnY; val br = ic().reloadBtnRadius
        p.color = 0x44FFC107.toInt(); canvas.drawCircle(bx, by, br, p)
        p.color = 0x66FFC107.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        canvas.drawCircle(bx, by, br, p); p.style = Paint.Style.FILL
        tp.textSize = 20f; tp.color = 0xDDFFFFFF.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("RELOAD", bx, by + 7, tp); tp.textAlign = Paint.Align.LEFT
    }

    private fun drawReloadingIndicator(canvas: Canvas, w: Float, h: Float, hs: HUDState) {
        val cx = w / 2f; val cy = h / 2f - 50f
        tp.textSize = 22f; tp.color = 0xDDFFC107.toInt(); tp.textAlign = Paint.Align.CENTER
        canvas.drawText("RELOADING", cx, cy, tp); tp.textAlign = Paint.Align.LEFT
        val barW = 120f; val barH = 6f
        val bx = cx - barW / 2f; val by = cy + 8f
        p.color = 0x55FFFFFF.toInt(); canvas.drawRoundRect(bx, by, bx + barW, by + barH, 3f, 3f, p)
        p.color = 0xDDFFC107.toInt(); canvas.drawRoundRect(bx, by, bx + barW * hs.reloadProgress, by + barH, 3f, 3f, p)
    }

    private fun ic() = input
}
