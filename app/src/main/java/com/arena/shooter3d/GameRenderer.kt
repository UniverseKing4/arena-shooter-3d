package com.arena.shooter3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class GameRenderer(
    private val engine: GameEngine,
    private val input: InputController,
    private val onHUDUpdate: (HUDState) -> Unit,
    private val onSoundEvents: (List<SoundEvent>) -> Unit
) : GLSurfaceView.Renderer {

    private var lastTime = 0L
    private var screenW = 1; private var screenH = 1
    private var sceneProgram = 0; private var hudProgram = 0; private var particleProgram = 0
    private var uMVP = 0; private var uModel = 0; private var uColor = 0
    private var uLightDir = 0; private var uCameraPos = 0; private var uAlpha = 0; private var uTexType = 0
    private var aPos = 0; private var aNormal = 0
    private var huMVP = 0; private var huColor = 0; private var haPos = 0
    private var puVP = 0; private var paPos = 0; private var paSize = 0; private var paColor = 0
    private lateinit var cubeVerts: FloatBuffer; private var cubeVC = 0
    private lateinit var sphereVerts: FloatBuffer; private var sphereVC = 0
    private lateinit var floorVerts: FloatBuffer
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val vp = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.38f, 0.55f, 0.78f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        sceneProgram = buildProgram(SCENE_VS, SCENE_FS)
        uMVP = loc(sceneProgram, "uMVP"); uModel = loc(sceneProgram, "uModel")
        uColor = loc(sceneProgram, "uColor"); uLightDir = loc(sceneProgram, "uLightDir")
        uCameraPos = loc(sceneProgram, "uCameraPos"); uAlpha = loc(sceneProgram, "uAlpha")
        uTexType = loc(sceneProgram, "uTexType")
        aPos = aloc(sceneProgram, "aPos"); aNormal = aloc(sceneProgram, "aNormal")
        hudProgram = buildProgram(HUD_VS, HUD_FS)
        huMVP = loc(hudProgram, "uMVP"); huColor = loc(hudProgram, "uColor"); haPos = aloc(hudProgram, "aPos")
        particleProgram = buildProgram(PARTICLE_VS, PARTICLE_FS)
        puVP = loc(particleProgram, "uVP"); paPos = aloc(particleProgram, "aPos")
        paSize = aloc(particleProgram, "aSize"); paColor = aloc(particleProgram, "aColor")
        buildMeshes(); lastTime = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        screenW = w; screenH = h; GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 70f, w.toFloat() / h, 0.1f, 150f)
        input.setScreenSize(w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1e9f).coerceAtMost(0.1f); lastTime = now
        engine.update(dt, input)
        val sounds = engine.consumeSoundEvents()
        if (sounds.isNotEmpty()) onSoundEvents(sounds)
        val hudState = engine.getHUDState()
        onHUDUpdate(hudState)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (engine.gameState == GameState.MENU) return

        val p = engine.player; val shake = p.screenShake
        val sx = if (shake > 0) (Math.random().toFloat() - 0.5f) * shake * 0.4f else 0f
        val sy = if (shake > 0) (Math.random().toFloat() - 0.5f) * shake * 0.25f else 0f
        val fwd = p.forward(); val eyeY = p.eyeY
        Matrix.setLookAtM(view, 0, p.position.x + sx, eyeY + sy, p.position.z,
            p.position.x + fwd.x, eyeY + fwd.y, p.position.z + fwd.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        useScene(); GLES20.glUniform3f(uLightDir, 0.4f, 0.9f, 0.3f)
        GLES20.glUniform3f(uCameraPos, p.position.x, p.eyeY, p.position.z)
        drawSun()
        drawFloor(); drawWalls(); drawEnemies(); drawProjectiles(); drawPickups()
        drawParticles(); drawGunViewmodel()
    }

    private fun useScene() { GLES20.glUseProgram(sceneProgram); GLES20.glUniform1f(uAlpha, 1f); GLES20.glUniform1i(uTexType, 0) }

    private fun drawFloor() {
        useScene(); GLES20.glUniform1i(uTexType, 1)
        Matrix.setIdentityM(model, 0); Matrix.scaleM(model, 0, engine.arena.size, 1f, engine.arena.size)
        setMats(); GLES20.glUniform3f(uColor, 0.18f, 0.14f, 0.10f)
        bindDraw(floorVerts, 6, 6)
    }

    private fun drawSun() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        useScene(); GLES20.glUniform1i(uTexType, 0)
        val p = engine.player
        val lightDir = Vec3(0.4f, 0.9f, 0.3f).normalize()
        val sunDist = 200f
        val sunX = p.position.x + lightDir.x * sunDist
        val sunY = lightDir.y * sunDist
        val sunZ = p.position.z + lightDir.z * sunDist
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, sunX, sunY, sunZ)
        val pulse = 1f + sin((System.currentTimeMillis() % 3000).toFloat() / 3000f * 6.28f) * 0.08f
        Matrix.scaleM(model, 0, 18f * pulse, 18f * pulse, 18f * pulse)
        setMats()
        GLES20.glUniform3f(uColor, 1f, 0.95f, 0.7f)
        GLES20.glUniform1f(uAlpha, 0.95f)
        bindDraw(sphereVerts, sphereVC, sphereVC)
        GLES20.glUniform1f(uAlpha, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawWalls() {
        useScene()
        for ((i, w) in engine.arena.walls.withIndex()) {
            val cx = (w.minX + w.maxX) / 2f; val cz = (w.minZ + w.maxZ) / 2f
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, cx, w.height / 2f, cz)
            Matrix.scaleM(model, 0, w.maxX - w.minX, w.height, w.maxZ - w.minZ)
            setMats()
            when {
                i < engine.arena.outerWallCount -> {
                    GLES20.glUniform1i(uTexType, 2); GLES20.glUniform3f(uColor, 0.6f, 0.38f, 0.28f)
                }
                i < engine.arena.pillarEndIndex -> {
                    GLES20.glUniform1i(uTexType, 3); GLES20.glUniform3f(uColor, 0.5f, 0.55f, 0.68f)
                }
                else -> {
                    GLES20.glUniform1i(uTexType, 4); GLES20.glUniform3f(uColor, 0.38f, 0.5f, 0.35f)
                }
            }
            bindDraw(cubeVerts, cubeVC, 6)
        }
    }

    private fun drawEnemies() {
        useScene()
        for (e in engine.enemies) {
            val s = e.type.size
            val dying = e.state == EnemyState.DYING
            val chasing = e.state == EnemyState.CHASE
            val attacking = e.state == EnemyState.ATTACK
            val bob = if (dying) 0f else sin(e.bobPhase) * 0.08f
            val alpha = if (dying) (e.deathTimer / 1.2f).coerceIn(0f, 1f) else 1f
            val flash = e.hitFlash > 0f || dying
            if (alpha < 1f) { GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA) }
            GLES20.glUniform1f(uAlpha, alpha); GLES20.glUniform1i(uTexType, 0)

            val ex = e.position.x; val ez = e.position.z
            val deathRot = if (dying) e.deathRotX else 0f
            val leanFwd = if (chasing) -8f else if (attacking) -15f else 0f
            val walkSpeed = if (chasing) e.bobPhase * 2.5f else e.bobPhase * 1.5f
            val legSwing = if (dying) 0f else sin(walkSpeed) * 25f
            val armSwing = if (dying) 0f else sin(e.bobPhase * 1.5f) * 18f

            val flashR = 1f; val flashG = 0.1f; val flashB = 0.1f
            val limbColor = if (flash) null else Triple(e.type.bodyR * 0.85f, e.type.bodyG * 0.85f, e.type.bodyB * 0.85f)
            val legColor = if (flash) null else Triple(e.type.bodyR * 0.7f, e.type.bodyG * 0.7f, e.type.bodyB * 0.7f)

            if (dying) {
                // Body
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, 0f, ez)
                Matrix.rotateM(model, 0, deathRot, 1f, 0f, 0f)
                Matrix.translateM(model, 0, 0f, s * 0.55f, 0f)
                Matrix.scaleM(model, 0, s * 0.45f, s * 0.6f, s * 0.3f)
                setMats()
                if (flash) GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                else GLES20.glUniform3f(uColor, e.type.bodyR, e.type.bodyG, e.type.bodyB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Head
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, 0f, ez)
                Matrix.rotateM(model, 0, deathRot, 1f, 0f, 0f)
                Matrix.translateM(model, 0, 0f, s * 1.05f, 0f)
                Matrix.scaleM(model, 0, s * 0.28f, s * 0.28f, s * 0.28f)
                setMats()
                if (flash) GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                else GLES20.glUniform3f(uColor, e.type.headR, e.type.headG, e.type.headB)
                bindDraw(sphereVerts, sphereVC, 6)

                // Left arm
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, 0f, ez)
                Matrix.rotateM(model, 0, deathRot, 1f, 0f, 0f)
                Matrix.translateM(model, 0, -s * 0.35f, s * 0.8f, 0f)
                Matrix.scaleM(model, 0, s * 0.12f, s * 0.45f, s * 0.12f)
                setMats()
                if (limbColor != null) GLES20.glUniform3f(uColor, limbColor.first, limbColor.second, limbColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Right arm
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, 0f, ez)
                Matrix.rotateM(model, 0, deathRot, 1f, 0f, 0f)
                Matrix.translateM(model, 0, s * 0.35f, s * 0.8f, 0f)
                Matrix.scaleM(model, 0, s * 0.12f, s * 0.45f, s * 0.12f)
                setMats()
                if (limbColor != null) GLES20.glUniform3f(uColor, limbColor.first, limbColor.second, limbColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Left leg
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, 0f, ez)
                Matrix.rotateM(model, 0, deathRot, 1f, 0f, 0f)
                Matrix.translateM(model, 0, -s * 0.14f, s * 0.12f, 0f)
                Matrix.scaleM(model, 0, s * 0.14f, s * 0.38f, s * 0.14f)
                setMats()
                if (legColor != null) GLES20.glUniform3f(uColor, legColor.first, legColor.second, legColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Right leg
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, 0f, ez)
                Matrix.rotateM(model, 0, deathRot, 1f, 0f, 0f)
                Matrix.translateM(model, 0, s * 0.14f, s * 0.12f, 0f)
                Matrix.scaleM(model, 0, s * 0.14f, s * 0.38f, s * 0.14f)
                setMats()
                if (legColor != null) GLES20.glUniform3f(uColor, legColor.first, legColor.second, legColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)
            } else {
                // Body
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, s * 0.55f + bob, ez)
                Matrix.rotateM(model, 0, leanFwd, 1f, 0f, 0f)
                Matrix.scaleM(model, 0, s * 0.45f, s * 0.6f, s * 0.3f)
                setMats()
                if (flash) GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                else GLES20.glUniform3f(uColor, e.type.bodyR, e.type.bodyG, e.type.bodyB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Head
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex, s * 1.05f + bob, ez)
                Matrix.scaleM(model, 0, s * 0.28f, s * 0.28f, s * 0.28f)
                setMats()
                if (flash) GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                else GLES20.glUniform3f(uColor, e.type.headR, e.type.headG, e.type.headB)
                bindDraw(sphereVerts, sphereVC, 6)

                // Left arm
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex - s * 0.35f, s * 0.8f + bob, ez)
                Matrix.rotateM(model, 0, -35f + armSwing, 1f, 0f, 0f)
                Matrix.scaleM(model, 0, s * 0.12f, s * 0.45f, s * 0.12f)
                setMats()
                if (limbColor != null) GLES20.glUniform3f(uColor, limbColor.first, limbColor.second, limbColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Right arm
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex + s * 0.35f, s * 0.8f + bob, ez)
                Matrix.rotateM(model, 0, -35f - armSwing, 1f, 0f, 0f)
                Matrix.scaleM(model, 0, s * 0.12f, s * 0.45f, s * 0.12f)
                setMats()
                if (limbColor != null) GLES20.glUniform3f(uColor, limbColor.first, limbColor.second, limbColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Left leg
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex - s * 0.14f, s * 0.12f + bob, ez)
                Matrix.rotateM(model, 0, legSwing, 1f, 0f, 0f)
                Matrix.scaleM(model, 0, s * 0.14f, s * 0.38f, s * 0.14f)
                setMats()
                if (legColor != null) GLES20.glUniform3f(uColor, legColor.first, legColor.second, legColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)

                // Right leg
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, ex + s * 0.14f, s * 0.12f + bob, ez)
                Matrix.rotateM(model, 0, -legSwing, 1f, 0f, 0f)
                Matrix.scaleM(model, 0, s * 0.14f, s * 0.38f, s * 0.14f)
                setMats()
                if (legColor != null) GLES20.glUniform3f(uColor, legColor.first, legColor.second, legColor.third)
                else GLES20.glUniform3f(uColor, flashR, flashG, flashB)
                bindDraw(cubeVerts, cubeVC, 6)
            }

            if (alpha < 1f) { GLES20.glDisable(GLES20.GL_BLEND) }
            GLES20.glUniform1f(uAlpha, 1f)
        }
    }

    private fun drawProjectiles() {
        useScene()
        for (p in engine.projectiles) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, p.position.x, p.position.y, p.position.z)
            Matrix.scaleM(model, 0, p.size, p.size, p.size)
            setMats(); GLES20.glUniform3f(uColor, p.r, p.g, p.b)
            bindDraw(sphereVerts, sphereVC, 6)
        }
    }

    private fun drawPickups() {
        useScene()
        for (pk in engine.pickups) {
            if (!pk.active) continue
            val bob = sin(pk.bobPhase) * 0.15f
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, pk.position.x, 0.6f + bob, pk.position.z)
            Matrix.rotateM(model, 0, pk.bobPhase * 57.3f, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, 0.38f, 0.38f, 0.38f)
            setMats(); GLES20.glUniform3f(uColor, pk.type.r, pk.type.g, pk.type.b)
            bindDraw(cubeVerts, cubeVC, 6)
        }
    }

    private fun drawGunViewmodel() {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        useScene(); GLES20.glUniform3f(uLightDir, 0.3f, 0.8f, 0.5f)
        GLES20.glUniform3f(uCameraPos, 0f, 0f, 0f); GLES20.glUniform1i(uTexType, 0)

        val p = engine.player
        val parts = when (p.currentWeapon) {
            0 -> Player.pistolParts; 1 -> Player.shotgunParts; else -> Player.rifleParts
        }
        val bx = 0.22f; val by = -0.2f; val bz = -0.45f
        val recoilZ = p.gunRecoil * 0.04f
        val recoilRot = p.gunRecoil * -6f
        val swapY = p.gunSwapProgress * -0.35f
        val reloadDipY = -p.reloadDip
        val bobX = if (engine.isMoving) sin(p.gunBobPhase) * 0.008f else 0f
        val bobY = if (engine.isMoving) sin(p.gunBobPhase * 2f) * 0.005f else 0f

        for (part in parts) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, bx + part.ox + bobX, by + part.oy + swapY + reloadDipY + bobY, bz + part.oz + recoilZ)
            Matrix.rotateM(model, 0, recoilRot, 1f, 0f, 0f)
            Matrix.scaleM(model, 0, part.sx, part.sy, part.sz)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
            GLES20.glUniform3f(uColor, part.r, part.g, part.b)
            bindDraw(cubeVerts, cubeVC, 6)
        }

        if (p.muzzleFlash > 0.3f) {
            val flashZ = when (p.currentWeapon) { 0 -> -0.24f; 1 -> -0.46f; else -> -0.42f }
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, bx + bobX, by + 0.02f + swapY + reloadDipY + bobY, bz + flashZ + recoilZ)
            val fs = p.muzzleFlash * 0.04f
            Matrix.scaleM(model, 0, fs, fs, fs)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
            GLES20.glUniform3f(uColor, 1f, 0.85f, 0.3f)
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            bindDraw(sphereVerts, sphereVC, 6)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun drawParticles() {
        val pts = engine.particles; if (pts.isEmpty()) return
        GLES20.glUseProgram(particleProgram); GLES20.glUniformMatrix4fv(puVP, 1, false, vp, 0)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        val d = FloatArray(pts.size * 8); var idx = 0
        for (pt in pts) { d[idx++]=pt.x; d[idx++]=pt.y; d[idx++]=pt.z; d[idx++]=pt.size*pt.alpha
            d[idx++]=pt.r; d[idx++]=pt.g; d[idx++]=pt.b; d[idx++]=pt.alpha }
        val buf = fbuf(d)
        GLES20.glEnableVertexAttribArray(paPos); GLES20.glEnableVertexAttribArray(paSize); GLES20.glEnableVertexAttribArray(paColor)
        buf.position(0); GLES20.glVertexAttribPointer(paPos, 3, GLES20.GL_FLOAT, false, 32, buf)
        buf.position(3); GLES20.glVertexAttribPointer(paSize, 1, GLES20.GL_FLOAT, false, 32, buf)
        buf.position(4); GLES20.glVertexAttribPointer(paColor, 4, GLES20.GL_FLOAT, false, 32, buf)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pts.size)
        GLES20.glDisableVertexAttribArray(paPos); GLES20.glDisableVertexAttribArray(paSize); GLES20.glDisableVertexAttribArray(paColor)
        GLES20.glDepthMask(true); GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawDamageFlash(amt: Float) {
        if (amt <= 0.01f) return
        GLES20.glUseProgram(hudProgram); GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val o = FloatArray(16); Matrix.orthoM(o, 0, 0f, screenW.toFloat(), screenH.toFloat(), 0f, -1f, 1f)
        GLES20.glUniformMatrix4fv(huMVP, 1, false, o, 0)
        val w = screenW.toFloat(); val h = screenH.toFloat()

        GLES20.glUniform4f(huColor, 0.9f, 0.02f, 0.02f, amt * 0.7f)
        val d = floatArrayOf(0f,0f,w,0f,w,h,0f,0f,w,h,0f,h); val buf = fbuf(d)
        GLES20.glEnableVertexAttribArray(haPos); GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        val edge = w * 0.22f; val edgeH = h * 0.22f
        GLES20.glUniform4f(huColor, 1f, 0f, 0f, amt * 0.8f)
        val lv = floatArrayOf(0f,0f,edge,0f,edge,h,0f,0f,edge,h,0f,h)
        GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, fbuf(lv))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        val rv = floatArrayOf(w-edge,0f,w,0f,w,h,w-edge,0f,w,h,w-edge,h)
        GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, fbuf(rv))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        val tv = floatArrayOf(0f,0f,w,0f,w,edgeH,0f,0f,w,edgeH,0f,edgeH)
        GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, fbuf(tv))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        val bv = floatArrayOf(0f,h-edgeH,w,h-edgeH,w,h,0f,h-edgeH,w,h,0f,h)
        GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, fbuf(bv))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(haPos)
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun setMats() {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
    }

    private fun bindDraw(buf: FloatBuffer, vc: Int, stride: Int) {
        val bs = stride * 4; GLES20.glEnableVertexAttribArray(aPos); GLES20.glEnableVertexAttribArray(aNormal)
        buf.position(0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, bs, buf)
        buf.position(3); GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, bs, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vc)
        GLES20.glDisableVertexAttribArray(aPos); GLES20.glDisableVertexAttribArray(aNormal)
    }

    private fun buildMeshes() { cubeVerts = buildCube(); cubeVC = 36; sphereVerts = buildSphere(10, 16); floorVerts = buildFloor() }

    private fun buildCube(): FloatBuffer {
        val v = floatArrayOf(
            -.5f,-.5f,.5f,0f,0f,1f,.5f,-.5f,.5f,0f,0f,1f,.5f,.5f,.5f,0f,0f,1f, -.5f,-.5f,.5f,0f,0f,1f,.5f,.5f,.5f,0f,0f,1f,-.5f,.5f,.5f,0f,0f,1f,
            .5f,-.5f,-.5f,0f,0f,-1f,-.5f,-.5f,-.5f,0f,0f,-1f,-.5f,.5f,-.5f,0f,0f,-1f, .5f,-.5f,-.5f,0f,0f,-1f,-.5f,.5f,-.5f,0f,0f,-1f,.5f,.5f,-.5f,0f,0f,-1f,
            .5f,-.5f,.5f,1f,0f,0f,.5f,-.5f,-.5f,1f,0f,0f,.5f,.5f,-.5f,1f,0f,0f, .5f,-.5f,.5f,1f,0f,0f,.5f,.5f,-.5f,1f,0f,0f,.5f,.5f,.5f,1f,0f,0f,
            -.5f,-.5f,-.5f,-1f,0f,0f,-.5f,-.5f,.5f,-1f,0f,0f,-.5f,.5f,.5f,-1f,0f,0f, -.5f,-.5f,-.5f,-1f,0f,0f,-.5f,.5f,.5f,-1f,0f,0f,-.5f,.5f,-.5f,-1f,0f,0f,
            -.5f,.5f,.5f,0f,1f,0f,.5f,.5f,.5f,0f,1f,0f,.5f,.5f,-.5f,0f,1f,0f, -.5f,.5f,.5f,0f,1f,0f,.5f,.5f,-.5f,0f,1f,0f,-.5f,.5f,-.5f,0f,1f,0f,
            -.5f,-.5f,-.5f,0f,-1f,0f,.5f,-.5f,-.5f,0f,-1f,0f,.5f,-.5f,.5f,0f,-1f,0f, -.5f,-.5f,-.5f,0f,-1f,0f,.5f,-.5f,.5f,0f,-1f,0f,-.5f,-.5f,.5f,0f,-1f,0f)
        return fbuf(v)
    }

    private fun buildSphere(stacks: Int, sectors: Int): FloatBuffer {
        val v = mutableListOf<Float>()
        for (i in 0 until stacks) { val t0 = PI.toFloat()*i/stacks; val t1 = PI.toFloat()*(i+1)/stacks
            for (j in 0 until sectors) { val p0 = 2f*PI.toFloat()*j/sectors; val p1 = 2f*PI.toFloat()*(j+1)/sectors
                val x00=sin(t0)*cos(p0); val y00=cos(t0); val z00=sin(t0)*sin(p0)
                val x01=sin(t0)*cos(p1); val y01=cos(t0); val z01=sin(t0)*sin(p1)
                val x10=sin(t1)*cos(p0); val y10=cos(t1); val z10=sin(t1)*sin(p0)
                val x11=sin(t1)*cos(p1); val y11=cos(t1); val z11=sin(t1)*sin(p1)
                fun a(x:Float,y:Float,z:Float){v.addAll(listOf(x*.5f,y*.5f,z*.5f,x,y,z))}
                a(x00,y00,z00);a(x10,y10,z10);a(x11,y11,z11);a(x00,y00,z00);a(x11,y11,z11);a(x01,y01,z01) } }
        sphereVC = v.size / 6; return fbuf(v.toFloatArray())
    }

    private fun buildFloor(): FloatBuffer = fbuf(floatArrayOf(
        -.5f,0f,-.5f,0f,1f,0f,.5f,0f,.5f,0f,1f,0f,.5f,0f,-.5f,0f,1f,0f,
        -.5f,0f,-.5f,0f,1f,0f,-.5f,0f,.5f,0f,1f,0f,.5f,0f,.5f,0f,1f,0f))

    private fun fbuf(d: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(d.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(d); position(0) }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = cs(GLES20.GL_VERTEX_SHADER, vs); val f = cs(GLES20.GL_FRAGMENT_SHADER, fs)
        return GLES20.glCreateProgram().also { GLES20.glAttachShader(it,v); GLES20.glAttachShader(it,f); GLES20.glLinkProgram(it) }
    }
    private fun cs(t:Int,s:String):Int = GLES20.glCreateShader(t).also { GLES20.glShaderSource(it,s); GLES20.glCompileShader(it) }
    private fun loc(p:Int,n:String) = GLES20.glGetUniformLocation(p,n)
    private fun aloc(p:Int,n:String) = GLES20.glGetAttribLocation(p,n)

    companion object {
        private const val SCENE_VS = """
            uniform mat4 uMVP; uniform mat4 uModel;
            attribute vec4 aPos; attribute vec3 aNormal;
            varying vec3 vWP; varying vec3 vN;
            void main(){ gl_Position=uMVP*aPos; vWP=(uModel*aPos).xyz; vN=normalize((uModel*vec4(aNormal,0.0)).xyz); }"""
        private const val SCENE_FS = """
            precision mediump float;
            uniform vec3 uColor; uniform vec3 uLightDir; uniform vec3 uCameraPos;
            uniform float uAlpha; uniform int uTexType;
            varying vec3 vWP; varying vec3 vN;
            void main(){
                vec3 n=normalize(vN); vec3 l=normalize(uLightDir);
                float amb=0.38; float diff=max(dot(n,l),0.0)*0.55;
                vec3 vd=normalize(uCameraPos-vWP); vec3 hd=normalize(l+vd);
                float spec=pow(max(dot(n,hd),0.0),24.0)*0.3;
                vec3 bc=uColor;
                if(uTexType==1){
                    vec2 tc=vWP.xz/2.0; vec2 f=fract(tc); float e=0.04;
                    float gx=step(e,f.x)*step(f.x,1.0-e); float gy=step(e,f.y)*step(f.y,1.0-e);
                    float g=gx*gy; vec2 tid=floor(tc); float ck=mod(tid.x+tid.y,2.0);
                    bc=mix(uColor*0.88,uColor*1.12,ck*0.3)*mix(0.55,1.0,g);
                } else if(uTexType==2){
                    float rh=0.35; float bw=0.7; float row=floor(vWP.y/rh);
                    float off=mod(row,2.0)*0.5*bw;
                    float h=abs(vN.x)>0.5?vWP.z:vWP.x;
                    vec2 bk=fract(vec2((h+off)/bw,vWP.y/rh)); float me=0.06;
                    float bx=step(me,bk.x)*step(bk.x,1.0-me); float by=step(me,bk.y)*step(bk.y,1.0-me);
                    bc=mix(uColor*0.5,uColor,bx*by);
                } else if(uTexType==3){
                    float ln=sin(vWP.y*8.0*3.14159)*0.5+0.5;
                    bc=mix(uColor*0.82,uColor*1.2,ln*0.25);
                } else if(uTexType==4){
                    float v1=sin(vWP.x*7.0+vWP.y*3.0); float v2=sin(vWP.z*5.0+vWP.y*4.0);
                    bc=mix(uColor*0.88,uColor*1.1,(v1*v2)*0.15+0.5);
                }
                vec3 c=bc*(amb+diff)+vec3(1.0)*spec;
                gl_FragColor=vec4(c,uAlpha);
            }"""
        private const val HUD_VS = "attribute vec4 aPos; uniform mat4 uMVP; void main(){ gl_Position=uMVP*aPos; }"
        private const val HUD_FS = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }"
        private const val PARTICLE_VS = """
            attribute vec4 aPos; attribute float aSize; attribute vec4 aColor; uniform mat4 uVP;
            varying vec4 vC; void main(){ gl_Position=uVP*aPos; gl_PointSize=aSize; vC=aColor; }"""
        private const val PARTICLE_FS = """
            precision mediump float; varying vec4 vC;
            void main(){ vec2 c=gl_PointCoord-vec2(0.5); if(dot(c,c)>0.25)discard; gl_FragColor=vC; }"""
    }
}
