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
    private val onHUDUpdate: (HUDState) -> Unit
) : GLSurfaceView.Renderer {

    private var lastTime = 0L
    private var screenW = 1
    private var screenH = 1

    private var sceneProgram = 0
    private var hudProgram = 0
    private var particleProgram = 0

    private var uMVP = 0; private var uModel = 0; private var uColor = 0
    private var uLightDir = 0; private var uCameraPos = 0; private var uAlpha = 0
    private var aPos = 0; private var aNormal = 0

    private var huMVP = 0; private var huColor = 0; private var haPos = 0

    private var puVP = 0; private var paPos = 0; private var paSize = 0; private var paColor = 0

    private lateinit var cubeVerts: FloatBuffer
    private var cubeVertCount = 0
    private lateinit var sphereVerts: FloatBuffer
    private var sphereVertCount = 0
    private lateinit var floorVerts: FloatBuffer
    private lateinit var quadVerts: FloatBuffer

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.04f, 0.1f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        sceneProgram = buildProgram(SCENE_VS, SCENE_FS)
        uMVP = GLES20.glGetUniformLocation(sceneProgram, "uMVP")
        uModel = GLES20.glGetUniformLocation(sceneProgram, "uModel")
        uColor = GLES20.glGetUniformLocation(sceneProgram, "uColor")
        uLightDir = GLES20.glGetUniformLocation(sceneProgram, "uLightDir")
        uCameraPos = GLES20.glGetUniformLocation(sceneProgram, "uCameraPos")
        uAlpha = GLES20.glGetUniformLocation(sceneProgram, "uAlpha")
        aPos = GLES20.glGetAttribLocation(sceneProgram, "aPos")
        aNormal = GLES20.glGetAttribLocation(sceneProgram, "aNormal")

        hudProgram = buildProgram(HUD_VS, HUD_FS)
        huMVP = GLES20.glGetUniformLocation(hudProgram, "uMVP")
        huColor = GLES20.glGetUniformLocation(hudProgram, "uColor")
        haPos = GLES20.glGetAttribLocation(hudProgram, "aPos")

        particleProgram = buildProgram(PARTICLE_VS, PARTICLE_FS)
        puVP = GLES20.glGetUniformLocation(particleProgram, "uVP")
        paPos = GLES20.glGetAttribLocation(particleProgram, "aPos")
        paSize = GLES20.glGetAttribLocation(particleProgram, "aSize")
        paColor = GLES20.glGetAttribLocation(particleProgram, "aColor")

        buildMeshes()
        lastTime = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = width; screenH = height
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 70f, ratio, 0.1f, 100f)
        input.setScreenSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1_000_000_000.0f).coerceAtMost(0.1f)
        lastTime = now

        engine.update(dt, input)
        val hudState = engine.getHUDState()
        onHUDUpdate(hudState)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (engine.gameState == GameState.MENU) {
            return
        }

        val p = engine.player
        val shake = p.screenShake
        val shakeX = if (shake > 0) (Math.random().toFloat() - 0.5f) * shake * 0.5f else 0f
        val shakeY = if (shake > 0) (Math.random().toFloat() - 0.5f) * shake * 0.3f else 0f

        val eyeX = p.position.x
        val eyeY = p.eyeY
        val eyeZ = p.position.z
        val fwd = p.forward()
        Matrix.setLookAtM(viewMatrix, 0,
            eyeX + shakeX, eyeY + shakeY, eyeZ,
            eyeX + fwd.x, eyeY + fwd.y, eyeZ + fwd.z,
            0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glUseProgram(sceneProgram)
        GLES20.glUniform3f(uLightDir, 0.4f, 0.8f, 0.3f)
        GLES20.glUniform3f(uCameraPos, eyeX, eyeY, eyeZ)
        GLES20.glUniform1f(uAlpha, 1f)

        drawFloor()
        drawArenaWalls()
        drawEnemies()
        drawProjectiles()
        drawPickups()
        drawParticles()
        drawCrosshair()
        drawDamageFlash(p.damageFlash)
    }

    private fun drawFloor() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, engine.arena.size, 1f, engine.arena.size)
        computeMVP()
        GLES20.glUseProgram(sceneProgram)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uColor, 0.08f, 0.08f, 0.15f)
        GLES20.glUniform1f(uAlpha, 1f)
        bindAndDraw(floorVerts, 6, 6)
    }

    private fun drawArenaWalls() {
        GLES20.glUseProgram(sceneProgram)
        GLES20.glUniform1f(uAlpha, 1f)
        val walls = engine.arena.walls
        for ((i, w) in walls.withIndex()) {
            val cx = (w.minX + w.maxX) / 2f
            val cz = (w.minZ + w.maxZ) / 2f
            val sx = w.maxX - w.minX
            val sz = w.maxZ - w.minZ
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, cx, w.height / 2f, cz)
            Matrix.scaleM(modelMatrix, 0, sx, w.height, sz)
            computeMVP()
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
            if (i < 4) {
                GLES20.glUniform3f(uColor, 0.1f, 0.1f, 0.25f)
            } else if (i < 13) {
                GLES20.glUniform3f(uColor, 0.12f, 0.15f, 0.3f)
            } else {
                GLES20.glUniform3f(uColor, 0.1f, 0.12f, 0.22f)
            }
            bindAndDraw(cubeVerts, cubeVertCount, 6)
        }
    }

    private fun drawEnemies() {
        GLES20.glUseProgram(sceneProgram)
        for (e in engine.enemies) {
            val s = e.type.size
            val bob = sin(e.bobPhase) * 0.1f
            val alpha = if (e.state == EnemyState.DYING) (e.deathTimer / 0.5f).coerceIn(0f, 1f) else 1f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, e.position.x, s + bob, e.position.z)
            Matrix.scaleM(modelMatrix, 0, s, s, s)
            computeMVP()
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
            if (e.hitFlash > 0f) {
                GLES20.glUniform3f(uColor, 1f, 1f, 1f)
            } else {
                GLES20.glUniform3f(uColor, e.type.r, e.type.g, e.type.b)
            }
            GLES20.glUniform1f(uAlpha, alpha)
            if (alpha < 1f) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
            bindAndDraw(sphereVerts, sphereVertCount, 6)
            if (alpha < 1f) GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUniform1f(uAlpha, 1f)
        }
    }

    private fun drawProjectiles() {
        GLES20.glUseProgram(sceneProgram)
        GLES20.glUniform1f(uAlpha, 1f)
        for (p in engine.projectiles) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.position.x, p.position.y, p.position.z)
            Matrix.scaleM(modelMatrix, 0, p.size, p.size, p.size)
            computeMVP()
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
            GLES20.glUniform3f(uColor, p.r, p.g, p.b)
            bindAndDraw(sphereVerts, sphereVertCount, 6)
        }
    }

    private fun drawPickups() {
        GLES20.glUseProgram(sceneProgram)
        GLES20.glUniform1f(uAlpha, 1f)
        for (pk in engine.pickups) {
            if (!pk.active) continue
            val bob = sin(pk.bobPhase) * 0.15f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, pk.position.x, 0.6f + bob, pk.position.z)
            Matrix.rotateM(modelMatrix, 0, pk.bobPhase * 57.3f, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, 0.4f, 0.4f, 0.4f)
            computeMVP()
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
            GLES20.glUniform3f(uColor, pk.type.r, pk.type.g, pk.type.b)
            bindAndDraw(cubeVerts, cubeVertCount, 6)
        }
    }

    private fun drawParticles() {
        val pts = engine.particles
        if (pts.isEmpty()) return

        GLES20.glUseProgram(particleProgram)
        GLES20.glUniformMatrix4fv(puVP, 1, false, vpMatrix, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)

        val data = FloatArray(pts.size * 8)
        var idx = 0
        for (p in pts) {
            data[idx++] = p.x; data[idx++] = p.y; data[idx++] = p.z
            data[idx++] = p.size * p.alpha
            data[idx++] = p.r; data[idx++] = p.g; data[idx++] = p.b; data[idx++] = p.alpha
        }
        val buf = makeFloatBuffer(data)

        GLES20.glEnableVertexAttribArray(paPos)
        GLES20.glEnableVertexAttribArray(paSize)
        GLES20.glEnableVertexAttribArray(paColor)

        buf.position(0)
        GLES20.glVertexAttribPointer(paPos, 3, GLES20.GL_FLOAT, false, 32, buf)
        buf.position(3)
        GLES20.glVertexAttribPointer(paSize, 1, GLES20.GL_FLOAT, false, 32, buf)
        buf.position(4)
        GLES20.glVertexAttribPointer(paColor, 4, GLES20.GL_FLOAT, false, 32, buf)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pts.size)

        GLES20.glDisableVertexAttribArray(paPos)
        GLES20.glDisableVertexAttribArray(paSize)
        GLES20.glDisableVertexAttribArray(paColor)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawCrosshair() {
        GLES20.glUseProgram(hudProgram)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val ortho = FloatArray(16)
        Matrix.orthoM(ortho, 0, 0f, screenW.toFloat(), screenH.toFloat(), 0f, -1f, 1f)
        GLES20.glUniformMatrix4fv(huMVP, 1, false, ortho, 0)
        GLES20.glUniform4f(huColor, 1f, 1f, 1f, 0.8f)

        val cx = screenW / 2f
        val cy = screenH / 2f
        val g = 6f
        val s = 14f
        val t = 1.5f

        val crosshairData = floatArrayOf(
            cx - s, cy - t, cx - g, cy - t, cx - g, cy + t,
            cx - s, cy - t, cx - g, cy + t, cx - s, cy + t,
            cx + g, cy - t, cx + s, cy - t, cx + s, cy + t,
            cx + g, cy - t, cx + s, cy + t, cx + g, cy + t,
            cx - t, cy - s, cx + t, cy - s, cx + t, cy - g,
            cx - t, cy - s, cx + t, cy - g, cx - t, cy - g,
            cx - t, cy + g, cx + t, cy + g, cx + t, cy + s,
            cx - t, cy + g, cx + t, cy + s, cx - t, cy + s,
            cx - 2f, cy - 2f, cx + 2f, cy - 2f, cx + 2f, cy + 2f,
            cx - 2f, cy - 2f, cx + 2f, cy + 2f, cx - 2f, cy + 2f
        )
        val buf = makeFloatBuffer(crosshairData)
        GLES20.glEnableVertexAttribArray(haPos)
        GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, crosshairData.size / 2)
        GLES20.glDisableVertexAttribArray(haPos)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawDamageFlash(amount: Float) {
        if (amount <= 0.01f) return
        GLES20.glUseProgram(hudProgram)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val ortho = FloatArray(16)
        Matrix.orthoM(ortho, 0, 0f, screenW.toFloat(), screenH.toFloat(), 0f, -1f, 1f)
        GLES20.glUniformMatrix4fv(huMVP, 1, false, ortho, 0)
        GLES20.glUniform4f(huColor, 0.8f, 0f, 0f, amount * 0.5f)

        val w = screenW.toFloat(); val h = screenH.toFloat()
        val data = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, 0f, w, h, 0f, h)
        val buf = makeFloatBuffer(data)
        GLES20.glEnableVertexAttribArray(haPos)
        GLES20.glVertexAttribPointer(haPos, 2, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(haPos)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun computeMVP() {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    }

    private fun bindAndDraw(buf: FloatBuffer, vertCount: Int, stride: Int) {
        val byteStride = stride * 4
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        buf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, byteStride, buf)
        buf.position(3)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, byteStride, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertCount)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    private fun buildMeshes() {
        cubeVerts = buildCube()
        cubeVertCount = 36
        sphereVerts = buildSphere(10, 16)
        floorVerts = buildFloor()
    }

    private fun buildCube(): FloatBuffer {
        val v = floatArrayOf(
            -0.5f,-0.5f, 0.5f, 0f, 0f, 1f,  0.5f,-0.5f, 0.5f, 0f, 0f, 1f,  0.5f, 0.5f, 0.5f, 0f, 0f, 1f,
            -0.5f,-0.5f, 0.5f, 0f, 0f, 1f,  0.5f, 0.5f, 0.5f, 0f, 0f, 1f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f,
             0.5f,-0.5f,-0.5f, 0f, 0f,-1f, -0.5f,-0.5f,-0.5f, 0f, 0f,-1f, -0.5f, 0.5f,-0.5f, 0f, 0f,-1f,
             0.5f,-0.5f,-0.5f, 0f, 0f,-1f, -0.5f, 0.5f,-0.5f, 0f, 0f,-1f,  0.5f, 0.5f,-0.5f, 0f, 0f,-1f,
             0.5f,-0.5f, 0.5f, 1f, 0f, 0f,  0.5f,-0.5f,-0.5f, 1f, 0f, 0f,  0.5f, 0.5f,-0.5f, 1f, 0f, 0f,
             0.5f,-0.5f, 0.5f, 1f, 0f, 0f,  0.5f, 0.5f,-0.5f, 1f, 0f, 0f,  0.5f, 0.5f, 0.5f, 1f, 0f, 0f,
            -0.5f,-0.5f,-0.5f,-1f, 0f, 0f, -0.5f,-0.5f, 0.5f,-1f, 0f, 0f, -0.5f, 0.5f, 0.5f,-1f, 0f, 0f,
            -0.5f,-0.5f,-0.5f,-1f, 0f, 0f, -0.5f, 0.5f, 0.5f,-1f, 0f, 0f, -0.5f, 0.5f,-0.5f,-1f, 0f, 0f,
            -0.5f, 0.5f, 0.5f, 0f, 1f, 0f,  0.5f, 0.5f, 0.5f, 0f, 1f, 0f,  0.5f, 0.5f,-0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0.5f, 0f, 1f, 0f,  0.5f, 0.5f,-0.5f, 0f, 1f, 0f, -0.5f, 0.5f,-0.5f, 0f, 1f, 0f,
            -0.5f,-0.5f,-0.5f, 0f,-1f, 0f,  0.5f,-0.5f,-0.5f, 0f,-1f, 0f,  0.5f,-0.5f, 0.5f, 0f,-1f, 0f,
            -0.5f,-0.5f,-0.5f, 0f,-1f, 0f,  0.5f,-0.5f, 0.5f, 0f,-1f, 0f, -0.5f,-0.5f, 0.5f, 0f,-1f, 0f
        )
        return makeFloatBuffer(v)
    }

    private fun buildSphere(stacks: Int, sectors: Int): FloatBuffer {
        val verts = mutableListOf<Float>()
        for (i in 0 until stacks) {
            val t0 = PI.toFloat() * i / stacks
            val t1 = PI.toFloat() * (i + 1) / stacks
            for (j in 0 until sectors) {
                val p0 = 2f * PI.toFloat() * j / sectors
                val p1 = 2f * PI.toFloat() * (j + 1) / sectors
                val x00 = sin(t0) * cos(p0); val y00 = cos(t0); val z00 = sin(t0) * sin(p0)
                val x01 = sin(t0) * cos(p1); val y01 = cos(t0); val z01 = sin(t0) * sin(p1)
                val x10 = sin(t1) * cos(p0); val y10 = cos(t1); val z10 = sin(t1) * sin(p0)
                val x11 = sin(t1) * cos(p1); val y11 = cos(t1); val z11 = sin(t1) * sin(p1)

                fun add(x: Float, y: Float, z: Float) { verts.addAll(listOf(x*0.5f, y*0.5f, z*0.5f, x, y, z)) }
                add(x00, y00, z00); add(x10, y10, z10); add(x11, y11, z11)
                add(x00, y00, z00); add(x11, y11, z11); add(x01, y01, z01)
            }
        }
        sphereVertCount = verts.size / 6
        return makeFloatBuffer(verts.toFloatArray())
    }

    private fun buildFloor(): FloatBuffer {
        val v = floatArrayOf(
            -0.5f, 0f, -0.5f, 0f, 1f, 0f,  0.5f, 0f, -0.5f, 0f, 1f, 0f,  0.5f, 0f, 0.5f, 0f, 1f, 0f,
            -0.5f, 0f, -0.5f, 0f, 1f, 0f,  0.5f, 0f, 0.5f, 0f, 1f, 0f, -0.5f, 0f, 0.5f, 0f, 1f, 0f
        )
        return makeFloatBuffer(v)
    }

    private fun makeFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data); position(0)
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, v)
        GLES20.glAttachShader(prog, f)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        return s
    }

    companion object {
        private const val SCENE_VS = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec4 aPos;
            attribute vec3 aNormal;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            void main() {
                gl_Position = uMVP * aPos;
                vWorldPos = (uModel * aPos).xyz;
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
            }
        """
        private const val SCENE_FS = """
            precision mediump float;
            uniform vec3 uColor;
            uniform vec3 uLightDir;
            uniform vec3 uCameraPos;
            uniform float uAlpha;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 l = normalize(uLightDir);
                float ambient = 0.22;
                float diff = max(dot(n, l), 0.0) * 0.6;
                vec3 viewDir = normalize(uCameraPos - vWorldPos);
                vec3 halfDir = normalize(l + viewDir);
                float spec = pow(max(dot(n, halfDir), 0.0), 24.0) * 0.35;
                vec3 color = uColor * (ambient + diff) + vec3(1.0) * spec;
                gl_FragColor = vec4(color, uAlpha);
            }
        """
        private const val HUD_VS = """
            attribute vec4 aPos;
            uniform mat4 uMVP;
            void main() { gl_Position = uMVP * aPos; }
        """
        private const val HUD_FS = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """
        private const val PARTICLE_VS = """
            attribute vec4 aPos;
            attribute float aSize;
            attribute vec4 aColor;
            uniform mat4 uVP;
            varying vec4 vColor;
            void main() {
                gl_Position = uVP * aPos;
                gl_PointSize = aSize;
                vColor = aColor;
            }
        """
        private const val PARTICLE_FS = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                vec2 c = gl_PointCoord - vec2(0.5);
                if (dot(c, c) > 0.25) discard;
                gl_FragColor = vColor;
            }
        """
    }
}
