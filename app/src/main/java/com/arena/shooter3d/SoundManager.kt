package com.arena.shooter3d

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

class SoundManager(private val ctx: Context) {
    private var pool: SoundPool? = null
    private val ids = IntArray(14)
    private var ready = false

    fun init() {
        val a = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(a).build()
        try {
            ids[0] = gen("pistol", shot(0.1f, 900f, 0.6f))
            ids[1] = gen("shotgun", shot(0.18f, 350f, 0.9f))
            ids[2] = gen("rifle", shot(0.06f, 1400f, 0.45f))
            ids[3] = gen("hit", tone(0.05f, 2000f, 0.3f))
            ids[4] = gen("kill", killSfx())
            ids[5] = gen("pickup", ascTone(0.18f, 600f, 1400f, 0.4f))
            ids[6] = gen("hurt", hurtSfx())
            ids[7] = gen("wave", beeps(3, 0.06f, 1200f, 0.35f))
            ids[8] = gen("over", descTone(0.5f, 600f, 150f, 0.6f))
            ids[9] = gen("switch", tone(0.04f, 1600f, 0.25f))
            ids[10] = gen("headshot", headshotSfx())
            ids[11] = gen("reload", reloadSfx())
            ids[12] = gen("reload_complete", reloadCompleteSfx())
            ids[13] = gen("game_start", gameStartSfx())
            ready = true
        } catch (_: Exception) {}
    }

    fun play(index: Int) {
        if (ready && index in ids.indices) pool?.play(ids[index], 0.45f, 0.45f, 1, 0, 1f)
    }

    fun release() { pool?.release(); pool = null; ready = false }

    private fun gen(name: String, pcm: ShortArray): Int {
        val f = File(ctx.cacheDir, "$name.wav"); writeWav(f, pcm, 22050)
        return pool?.load(f.absolutePath, 1) ?: 0
    }

    private fun shot(dur: Float, freq: Float, vol: Float): ShortArray {
        val r = 22050; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r
            val env = (1f - t / dur).pow(3f) * vol
            val ns = Random.nextFloat() * 2f - 1f
            val tn = sin(2f * PI.toFloat() * freq * t * (1f - t / dur))
            o[i] = ((ns * 0.7f + tn * 0.3f) * env * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun tone(dur: Float, freq: Float, vol: Float): ShortArray {
        val r = 22050; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r
            o[i] = (sin(2f * PI.toFloat() * freq * t) * (1f - t / dur) * vol * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun ascTone(dur: Float, f0: Float, f1: Float, vol: Float): ShortArray {
        val r = 22050; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val freq = f0 + (f1 - f0) * p
            o[i] = (sin(2f * PI.toFloat() * freq * t) * (1f - p) * vol * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun descTone(dur: Float, f0: Float, f1: Float, vol: Float): ShortArray {
        val r = 22050; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val freq = f0 + (f1 - f0) * p
            o[i] = (sin(2f * PI.toFloat() * freq * t) * (1f - p * 0.5f) * vol * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun beeps(count: Int, bDur: Float, freq: Float, vol: Float): ShortArray {
        val r = 22050; val gap = 0.04f; val total = count * bDur + (count - 1) * gap
        val n = (r * total).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r
            val cycle = bDur + gap; val pos = t % cycle
            if (pos < bDur) {
                val env = (1f - pos / bDur) * vol
                o[i] = (sin(2f * PI.toFloat() * freq * pos) * env * 30000).toInt().coerceIn(-32768, 32767).toShort()
            }
        }; return o
    }

    private fun hurtSfx(): ShortArray {
        val r = 22050; val dur = 0.22f; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val env = (1f - p).pow(2f)
            val bass = sin(2f * PI.toFloat() * 80f * t) * 0.5f
            val mid = sin(2f * PI.toFloat() * 200f * t * (1f - p * 0.5f)) * 0.3f
            val ns = (Random.nextFloat() * 2f - 1f) * 0.35f
            o[i] = ((bass + mid + ns) * env * 0.85f * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun killSfx(): ShortArray {
        val r = 22050; val dur = 0.28f; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val env = (1f - p).pow(1.5f)
            val crunch = sin(2f * PI.toFloat() * 600f * t * (1f - p * 0.7f)) * 0.4f
            val thud = sin(2f * PI.toFloat() * 120f * t) * (1f - p) * 0.4f
            val pop = if (p < 0.1f) sin(2f * PI.toFloat() * 1800f * t) * (1f - p / 0.1f) * 0.3f else 0f
            o[i] = ((crunch + thud + pop) * env * 0.6f * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun headshotSfx(): ShortArray {
        val r = 22050; val dur = 0.3f; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val env = (1f - p).pow(1.2f)
            val ping = sin(2f * PI.toFloat() * 2400f * t) * 0.35f
            val crack = sin(2f * PI.toFloat() * 1200f * t * (1f - p * 0.5f)) * 0.3f
            val snap = if (p < 0.08f) sin(2f * PI.toFloat() * 3200f * t) * (1f - p / 0.08f) * 0.4f else 0f
            val ns = (Random.nextFloat() * 2f - 1f) * 0.1f * (1f - p)
            o[i] = ((ping + crack + snap + ns) * env * 0.7f * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun reloadSfx(): ShortArray {
        val r = 22050; val dur = 0.35f; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val click1 = if (p < 0.15f) sin(2f * PI.toFloat() * 800f * t) * (1f - p / 0.15f) * 0.5f else 0f
            val slide = if (p in 0.15f..0.55f) { val sp = (p - 0.15f) / 0.4f; sin(2f * PI.toFloat() * (400f + sp * 300f) * t) * (1f - sp) * 0.3f } else 0f
            val click2 = if (p > 0.6f) { val sp = (p - 0.6f) / 0.4f; sin(2f * PI.toFloat() * 1200f * t) * (1f - sp).pow(2f) * 0.5f } else 0f
            val metal = (Random.nextFloat() * 2f - 1f) * 0.08f * (1f - p)
            o[i] = ((click1 + slide + click2 + metal) * 0.8f * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun reloadCompleteSfx(): ShortArray {
        val r = 22050; val dur = 0.25f; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val snap = if (p < 0.12f) sin(2f * PI.toFloat() * 1400f * t) * (1f - p / 0.12f).pow(2f) * 0.6f else 0f
            val click = if (p in 0.12f..0.25f) { val sp = (p - 0.12f) / 0.13f; sin(2f * PI.toFloat() * 1800f * t) * (1f - sp).pow(1.5f) * 0.5f } else 0f
            val lock = if (p > 0.25f) { val sp = (p - 0.25f) / 0.75f; sin(2f * PI.toFloat() * 2200f * t) * (1f - sp).pow(2f) * 0.4f } else 0f
            val metallic = (Random.nextFloat() * 2f - 1f) * 0.06f * (1f - p).pow(2f)
            o[i] = ((snap + click + lock + metallic) * 0.85f * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun gameStartSfx(): ShortArray {
        val r = 22050; val dur = 0.45f; val n = (r * dur).toInt(); val o = ShortArray(n)
        for (i in 0 until n) { val t = i.toFloat() / r; val p = t / dur
            val sweep = sin(2f * PI.toFloat() * (400f + p * 1200f) * t) * (1f - p * 0.6f) * 0.35f
            val tone1 = if (p < 0.3f) sin(2f * PI.toFloat() * 880f * t) * (1f - p / 0.3f) * 0.3f else 0f
            val tone2 = if (p in 0.15f..0.45f) { val sp = (p - 0.15f) / 0.3f; sin(2f * PI.toFloat() * 1320f * t) * (1f - sp).pow(1.5f) * 0.35f } else 0f
            val tone3 = if (p > 0.3f) { val sp = (p - 0.3f) / 0.7f; sin(2f * PI.toFloat() * 1760f * t) * (1f - sp).pow(2f) * 0.3f } else 0f
            val shimmer = sin(2f * PI.toFloat() * 3520f * t) * (1f - p).pow(3f) * 0.1f
            o[i] = ((sweep + tone1 + tone2 + tone3 + shimmer) * 0.9f * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }; return o
    }

    private fun writeWav(file: File, pcm: ShortArray, sr: Int) {
        val ds = pcm.size * 2
        val b = ByteBuffer.allocate(44 + ds).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + ds); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(sr); b.putInt(sr * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(ds)
        for (s in pcm) b.putShort(s)
        FileOutputStream(file).use { it.write(b.array()) }
    }
}
