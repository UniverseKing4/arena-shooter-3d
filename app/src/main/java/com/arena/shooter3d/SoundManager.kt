package com.arena.shooter3d

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var shootPistolId = 0
    private var shootShotgunId = 0
    private var shootRifleId = 0
    private var hitMarkerId = 0
    private var pickupId = 0
    private var hurtId = 0
    private var loaded = false

    fun init() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()

        try {
            shootPistolId = loadGeneratedSound("pistol", generateShot(0.12f, 800f, 0.7f))
            shootShotgunId = loadGeneratedSound("shotgun", generateShot(0.2f, 400f, 1f))
            shootRifleId = loadGeneratedSound("rifle", generateShot(0.08f, 1200f, 0.5f))
            hitMarkerId = loadGeneratedSound("hit", generateTone(0.06f, 1800f, 0.4f))
            pickupId = loadGeneratedSound("pickup", generatePickup())
            hurtId = loadGeneratedSound("hurt", generateShot(0.15f, 200f, 0.8f))
            loaded = true
        } catch (_: Exception) {}
    }

    private fun loadGeneratedSound(name: String, pcm: ShortArray): Int {
        val file = File(context.cacheDir, "$name.wav")
        writeWav(file, pcm, 22050)
        return soundPool?.load(file.absolutePath, 1) ?: 0
    }

    private fun generateShot(duration: Float, freq: Float, intensity: Float): ShortArray {
        val rate = 22050
        val samples = (rate * duration).toInt()
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toFloat() / rate
            val env = (1f - t / duration).pow(3f) * intensity
            val noise = (Random.nextFloat() * 2f - 1f)
            val tone = sin(2f * PI.toFloat() * freq * t * (1f - t / duration))
            out[i] = ((noise * 0.7f + tone * 0.3f) * env * 32000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun generateTone(duration: Float, freq: Float, vol: Float): ShortArray {
        val rate = 22050
        val samples = (rate * duration).toInt()
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toFloat() / rate
            val env = (1f - t / duration) * vol
            out[i] = (sin(2f * PI.toFloat() * freq * t) * env * 32000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun generatePickup(): ShortArray {
        val rate = 22050
        val dur = 0.2f
        val samples = (rate * dur).toInt()
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            val t = i.toFloat() / rate
            val freq = 600f + t / dur * 800f
            val env = (1f - t / dur) * 0.5f
            out[i] = (sin(2f * PI.toFloat() * freq * t) * env * 32000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()); buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray()); buf.put("fmt ".toByteArray())
        buf.putInt(16); buf.putShort(1); buf.putShort(1)
        buf.putInt(sampleRate); buf.putInt(sampleRate * 2)
        buf.putShort(2); buf.putShort(16)
        buf.put("data".toByteArray()); buf.putInt(dataSize)
        for (s in pcm) buf.putShort(s)
        FileOutputStream(file).use { it.write(buf.array()) }
    }

    fun playShoot(weaponIndex: Int) {
        if (!loaded) return
        val id = when (weaponIndex) { 0 -> shootPistolId; 1 -> shootShotgunId; else -> shootRifleId }
        soundPool?.play(id, 0.5f, 0.5f, 1, 0, 1f)
    }

    fun playHit() { if (loaded) soundPool?.play(hitMarkerId, 0.3f, 0.3f, 1, 0, 1f) }
    fun playPickup() { if (loaded) soundPool?.play(pickupId, 0.4f, 0.4f, 1, 0, 1f) }
    fun playHurt() { if (loaded) soundPool?.play(hurtId, 0.5f, 0.5f, 1, 0, 1f) }

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
    }
}
