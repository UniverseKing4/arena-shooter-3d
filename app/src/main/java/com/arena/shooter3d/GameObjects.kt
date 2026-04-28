package com.arena.shooter3d

import kotlin.math.*

data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    fun normalized(): Vec3 { val l = length(); return if (l > 1e-5f) Vec3(x / l, y / l, z / l) else Vec3() }
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun distTo(o: Vec3) = (this - o).length()
    fun xz() = Vec3(x, 0f, z)
}

enum class GameState { MENU, PLAYING, PAUSED, GAME_OVER }
enum class EnemyState { CHASE, ATTACK, STAGGER, DYING }

enum class SoundEvent {
    SHOOT_PISTOL, SHOOT_SHOTGUN, SHOOT_RIFLE,
    HIT_ENEMY, KILL_ENEMY, HEADSHOT,
    PICKUP_HEALTH, PICKUP_AMMO,
    PLAYER_HURT, WAVE_START, GAME_OVER,
    WEAPON_SWITCH, RELOAD, RELOAD_COMPLETE
}

enum class EnemyType(
    val maxHealth: Int, val speed: Float, val damage: Int, val attackRange: Float,
    val size: Float, val bodyR: Float, val bodyG: Float, val bodyB: Float,
    val headR: Float, val headG: Float, val headB: Float, val scoreValue: Int
) {
    WALKER(35, 3.5f, 8, 2.5f, 1.3f,
        0.3f, 0.5f, 0.22f, 0.35f, 0.55f, 0.28f, 100),
    RUNNER(55, 6f, 12, 2.6f, 1.15f,
        0.55f, 0.22f, 0.18f, 0.6f, 0.28f, 0.22f, 200),
    BRUTE(130, 2.2f, 22, 4.0f, 2.0f,
        0.38f, 0.28f, 0.42f, 0.48f, 0.38f, 0.52f, 500)
}

class Enemy(
    var position: Vec3,
    val type: EnemyType,
    var health: Int = type.maxHealth,
    var state: EnemyState = EnemyState.CHASE,
    var stateTimer: Float = 0f,
    var attackCooldown: Float = 0f,
    var deathTimer: Float = 0f,
    var bobPhase: Float = (Math.random() * PI * 2).toFloat(),
    var hitFlash: Float = 0f,
    var deathRotX: Float = 0f,
    var strafeDir: Float = if (Math.random() < 0.5) 1f else -1f,
    var strafeTimer: Float = (Math.random() * 2f).toFloat()
)

data class Weapon(
    val name: String, val damage: Int, val fireRate: Float,
    val magSize: Int, val reloadTime: Float,
    val spread: Float, val projectileSpeed: Float, val pellets: Int = 1,
    val r: Float, val g: Float, val b: Float
)

data class GunPart(
    val ox: Float, val oy: Float, val oz: Float,
    val sx: Float, val sy: Float, val sz: Float,
    val r: Float, val g: Float, val b: Float
)

class Player {
    var position = Vec3(0f, 0f, 0f)
    var yaw = 0f; var pitch = 0f
    var health = 100; val maxHealth = 100
    var velocityY = 0f; var isGrounded = true
    val weapons = arrayOf(
        Weapon("PISTOL", 22, 0.25f, 12, 1.2f, 0.015f, 38f, 1, 1f, 1f, 0.4f),
        Weapon("SHOTGUN", 18, 0.8f, 6, 2.0f, 0.1f, 30f, 5, 1f, 0.7f, 0.25f),
        Weapon("RIFLE", 11, 0.1f, 30, 1.8f, 0.035f, 48f, 1, 0.4f, 0.85f, 1f)
    )
    var currentWeapon = 0
    var mag = intArrayOf(12, 6, 30)
    var reserve = intArrayOf(999, 24, 120)
    var isReloading = false; var reloadTimer = 0f; var reloadDuration = 0f
    var fireCooldown = 0f
    var damageFlash = 0f; var screenShake = 0f; var kills = 0

    var gunRecoil = 0f
    var gunSwapProgress = 0f
    var gunBobPhase = 0f
    var swapPhase = 0
    var pendingWeapon = -1
    var muzzleFlash = 0f
    var reloadDip = 0f

    val weapon get() = weapons[currentWeapon]
    val eyeY get() = 1.6f + position.y

    fun forward(): Vec3 {
        val cp = cos(pitch)
        return Vec3(sin(yaw) * cp, -sin(pitch), -cos(yaw) * cp).normalized()
    }
    fun flatForward() = Vec3(sin(yaw), 0f, -cos(yaw)).normalized()
    fun right() = Vec3(cos(yaw), 0f, sin(yaw))

    fun reset() {
        position = Vec3(0f, 0f, 0f); yaw = 0f; pitch = 0f
        health = maxHealth; currentWeapon = 0
        mag = intArrayOf(12, 6, 30); reserve = intArrayOf(999, 24, 120)
        isReloading = false; reloadTimer = 0f; reloadDuration = 0f
        fireCooldown = 0f; damageFlash = 0f; screenShake = 0f; kills = 0
        gunRecoil = 0f; gunSwapProgress = 0f; gunBobPhase = 0f
        swapPhase = 0; pendingWeapon = -1; muzzleFlash = 0f; reloadDip = 0f
        velocityY = 0f; isGrounded = true
    }

    companion object {
        val pistolParts = arrayOf(
            GunPart(0f, 0f, 0f, 0.055f, 0.075f, 0.17f, 0.35f, 0.35f, 0.4f),
            GunPart(0f, 0.018f, -0.12f, 0.022f, 0.022f, 0.12f, 0.28f, 0.28f, 0.33f),
            GunPart(0f, -0.055f, 0.03f, 0.038f, 0.075f, 0.045f, 0.3f, 0.27f, 0.24f),
            GunPart(0f, -0.01f, -0.03f, 0.018f, 0.014f, 0.05f, 0.22f, 0.22f, 0.25f),
        )
        val shotgunParts = arrayOf(
            GunPart(0f, 0f, 0.04f, 0.048f, 0.055f, 0.32f, 0.42f, 0.3f, 0.17f),
            GunPart(0f, 0.008f, -0.2f, 0.028f, 0.028f, 0.26f, 0.32f, 0.32f, 0.37f),
            GunPart(0f, -0.014f, -0.08f, 0.034f, 0.034f, 0.1f, 0.3f, 0.3f, 0.35f),
            GunPart(0f, -0.048f, 0.1f, 0.038f, 0.068f, 0.055f, 0.38f, 0.27f, 0.15f),
        )
        val rifleParts = arrayOf(
            GunPart(0f, 0f, 0f, 0.044f, 0.058f, 0.3f, 0.24f, 0.26f, 0.3f),
            GunPart(0f, 0.014f, -0.2f, 0.018f, 0.018f, 0.22f, 0.2f, 0.22f, 0.26f),
            GunPart(0f, -0.05f, -0.02f, 0.024f, 0.055f, 0.038f, 0.22f, 0.24f, 0.27f),
            GunPart(0f, 0.038f, -0.02f, 0.014f, 0.014f, 0.055f, 0.15f, 0.52f, 0.68f),
            GunPart(0f, -0.042f, 0.1f, 0.034f, 0.055f, 0.048f, 0.22f, 0.24f, 0.27f),
            GunPart(0f, 0f, 0.14f, 0.038f, 0.038f, 0.075f, 0.22f, 0.24f, 0.27f),
        )
    }
}

class Projectile(
    var position: Vec3, val velocity: Vec3, val damage: Int,
    var lifetime: Float = 2.5f,
    val r: Float, val g: Float, val b: Float, val size: Float = 0.1f
)

enum class PickupType(val r: Float, val g: Float, val b: Float) {
    HEALTH(0.15f, 0.95f, 0.35f), AMMO(0.35f, 0.55f, 1f)
}

class Pickup(
    val position: Vec3, val type: PickupType,
    var bobPhase: Float = 0f, var respawnTimer: Float = 0f, var active: Boolean = true
)

class Particle(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val maxLife: Float, var size: Float,
    val r: Float, val g: Float, val b: Float
) {
    val alpha get() = (life / maxLife).coerceIn(0f, 1f)
    val alive get() = life > 0f
}

data class Wall(val minX: Float, val minZ: Float, val maxX: Float, val maxZ: Float, val height: Float = 3.5f)

data class FloatingText(var x: Float, var y: Float, var z: Float, val text: String, var timer: Float, val r: Float, val g: Float, val b: Float)

class Arena {
    val size = 70f; val half = size / 2f
    val walls = mutableListOf<Wall>()
    val outerWallCount: Int
    val pillarEndIndex: Int
    val pickupSpots = mutableListOf<Vec3>()
    val spawnPoints = mutableListOf<Vec3>()

    init {
        val w = 0.5f
        walls.add(Wall(-half, -half, half, -half + w))
        walls.add(Wall(-half, half - w, half, half))
        walls.add(Wall(-half, -half, -half + w, half))
        walls.add(Wall(half - w, -half, half, half))
        outerWallCount = walls.size

        val ps = 0.9f
        for (pos in listOf(
            Vec3(-14f, 0f, -14f), Vec3(14f, 0f, -14f), Vec3(-14f, 0f, 14f), Vec3(14f, 0f, 14f),
            Vec3(0f, 0f, 0f),
            Vec3(-24f, 0f, 0f), Vec3(24f, 0f, 0f), Vec3(0f, 0f, -24f), Vec3(0f, 0f, 24f),
            Vec3(-10f, 0f, 24f), Vec3(10f, 0f, -24f),
            Vec3(-24f, 0f, 14f), Vec3(24f, 0f, -14f),
            Vec3(-7f, 0f, -7f), Vec3(7f, 0f, 7f)
        )) { walls.add(Wall(pos.x - ps, pos.z - ps, pos.x + ps, pos.z + ps, 4.5f)) }
        pillarEndIndex = walls.size

        for (pos in listOf(
            Vec3(-8f, 0f, -6f), Vec3(8f, 0f, 6f),
            Vec3(-20f, 0f, 17f), Vec3(20f, 0f, -17f),
            Vec3(-6f, 0f, 20f), Vec3(6f, 0f, -20f),
            Vec3(16f, 0f, 16f), Vec3(-16f, 0f, -16f),
            Vec3(-18f, 0f, -8f), Vec3(18f, 0f, 8f),
            Vec3(0f, 0f, -12f), Vec3(0f, 0f, 12f)
        )) { walls.add(Wall(pos.x - 1.4f, pos.z - 0.5f, pos.x + 1.4f, pos.z + 0.5f, 1.3f)) }

        pickupSpots.addAll(listOf(
            Vec3(-24f, 0.5f, -24f), Vec3(24f, 0.5f, -24f),
            Vec3(-24f, 0.5f, 24f), Vec3(24f, 0.5f, 24f),
            Vec3(0f, 0.5f, -17f), Vec3(0f, 0.5f, 17f),
            Vec3(-17f, 0.5f, 0f), Vec3(17f, 0.5f, 0f),
            Vec3(-12f, 0.5f, 12f), Vec3(12f, 0.5f, -12f)
        ))
        spawnPoints.addAll(listOf(
            Vec3(-30f, 0f, -30f), Vec3(30f, 0f, -30f),
            Vec3(-30f, 0f, 30f), Vec3(30f, 0f, 30f),
            Vec3(-30f, 0f, 0f), Vec3(30f, 0f, 0f),
            Vec3(0f, 0f, -30f), Vec3(0f, 0f, 30f),
            Vec3(-18f, 0f, -30f), Vec3(18f, 0f, 30f),
            Vec3(-30f, 0f, 18f), Vec3(30f, 0f, -18f)
        ))
    }
}

data class HUDState(
    val health: Int, val maxHealth: Int,
    val magCurrent: Int, val magMax: Int, val reserveAmmo: Int,
    val weaponName: String, val currentWeapon: Int,
    val isReloading: Boolean, val reloadProgress: Float,
    val score: Int, val highScore: Int,
    val wave: Int, val combo: Int, val comboTimer: Float,
    val damageFlash: Float, val gameState: GameState,
    val enemyCount: Int,
    val playerX: Float, val playerZ: Float, val playerYaw: Float,
    val enemyPositions: List<Pair<Float, Float>>,
    val pickupPositions: List<Triple<Float, Float, Int>>,
    val arenaHalf: Float, val kills: Int,
    val soundEvents: List<SoundEvent>,
    val isSprinting: Boolean = false,
    val headshots: Int = 0,
    val wavePopupTimer: Float = 0f,
    val weaponSwitchPopup: String = "",
    val weaponSwitchPopupTimer: Float = 0f,
    val lowHealthWarning: Boolean = false
)
