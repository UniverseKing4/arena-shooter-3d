package com.arena.shooter3d

import kotlin.math.*
import kotlin.random.Random

class GameEngine {
    val player = Player()
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val pickups = mutableListOf<Pickup>()
    val particles = mutableListOf<Particle>()
    val arena = Arena()
    var gameState = GameState.MENU
    var score = 0
    var highScore = 0
    var wave = 0
    var combo = 0
    var comboTimer = 0f
    private var waveDelay = 0f
    private var pickupTimer = 0f
    private val moveSpeed = 7f
    private val rng = Random

    fun startGame() {
        player.reset()
        enemies.clear()
        projectiles.clear()
        particles.clear()
        score = 0
        wave = 0
        combo = 0
        comboTimer = 0f
        waveDelay = 1f
        pickupTimer = 0f
        gameState = GameState.PLAYING

        pickups.clear()
        for ((i, spot) in arena.pickupSpots.withIndex()) {
            pickups.add(Pickup(spot.copy(), if (i % 2 == 0) PickupType.HEALTH else PickupType.AMMO))
        }
    }

    fun update(dt: Float, input: InputController) {
        if (gameState != GameState.PLAYING) return
        val cdt = dt.coerceAtMost(0.05f)

        updatePlayer(cdt, input)
        updateProjectiles(cdt)
        updateEnemies(cdt)
        updatePickups(cdt)
        updateParticles(cdt)
        updateWaves(cdt)
        updateCombo(cdt)

        if (player.health <= 0) {
            gameState = GameState.GAME_OVER
            if (score > highScore) highScore = score
            spawnDeathParticles(player.position.x, player.eyeY, player.position.z, 1f, 0.3f, 0.3f, 30)
        }
    }

    private fun updatePlayer(dt: Float, input: InputController) {
        val (ldx, ldy) = input.consumeLookDelta()
        player.yaw += ldx
        player.pitch = (player.pitch + ldy).coerceIn(-1.2f, 1.2f)

        if (input.consumeWeaponSwitch()) {
            player.currentWeapon = (player.currentWeapon + 1) % player.weapons.size
        }

        val fwd = player.flatForward()
        val rgt = player.right()
        var mx = fwd.x * (-input.moveZ) + rgt.x * input.moveX
        var mz = fwd.z * (-input.moveZ) + rgt.z * input.moveX
        val ml = sqrt(mx * mx + mz * mz)
        if (ml > 0.01f) {
            mx = mx / ml * moveSpeed * dt
            mz = mz / ml * moveSpeed * dt
            player.position.x += mx
            player.position.z += mz
            resolvePlayerWallCollision()
        }

        val h = arena.half - 0.4f
        player.position.x = player.position.x.coerceIn(-h, h)
        player.position.z = player.position.z.coerceIn(-h, h)

        player.fireCooldown -= dt
        if (input.isFiring && player.fireCooldown <= 0f) {
            fireWeapon()
        }

        player.damageFlash = (player.damageFlash - dt * 3f).coerceAtLeast(0f)
        player.screenShake = (player.screenShake - dt * 5f).coerceAtLeast(0f)
    }

    private fun fireWeapon() {
        val w = player.weapon
        if (w.maxAmmo > 0 && player.ammo[player.currentWeapon] <= 0) return

        player.fireCooldown = w.fireRate
        if (w.maxAmmo > 0) player.ammo[player.currentWeapon]--

        val dir = player.forward()
        val spawnPos = Vec3(
            player.position.x + dir.x * 0.5f,
            player.eyeY + dir.y * 0.5f,
            player.position.z + dir.z * 0.5f
        )

        for (i in 0 until w.pellets) {
            val sx = (rng.nextFloat() - 0.5f) * w.spread * 2
            val sy = (rng.nextFloat() - 0.5f) * w.spread * 2
            val rgt = player.right()
            val up = Vec3(0f, 1f, 0f)
            val d = Vec3(
                dir.x + rgt.x * sx + up.x * sy,
                dir.y + rgt.y * sx + up.y * sy,
                dir.z + rgt.z * sx + up.z * sy
            ).normalized()
            val vel = d * w.projectileSpeed
            projectiles.add(Projectile(spawnPos.copy(), vel, w.damage, 2f, w.r, w.g, w.b, 0.12f))
        }

        player.screenShake = 0.15f
        spawnMuzzleFlash(spawnPos, dir)
    }

    private fun spawnMuzzleFlash(pos: Vec3, dir: Vec3) {
        for (i in 0..5) {
            val spread = 0.8f
            particles.add(Particle(
                pos.x, pos.y, pos.z,
                dir.x * 8f + (rng.nextFloat() - 0.5f) * spread,
                dir.y * 8f + (rng.nextFloat() - 0.5f) * spread,
                dir.z * 8f + (rng.nextFloat() - 0.5f) * spread,
                0.08f, 0.08f, rng.nextFloat() * 4f + 3f,
                1f, 0.8f, 0.2f
            ))
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.lifetime -= dt
            if (p.lifetime <= 0f) { iter.remove(); continue }

            val nx = p.position.x + p.velocity.x * dt
            val ny = p.position.y + p.velocity.y * dt
            val nz = p.position.z + p.velocity.z * dt

            var hit = false
            for (w in arena.walls) {
                if (nx > w.minX && nx < w.maxX && nz > w.minZ && nz < w.maxZ && ny < w.height && ny > 0f) {
                    hit = true
                    spawnHitParticles(p.position.x, p.position.y, p.position.z, 0.6f, 0.6f, 0.6f, 4)
                    break
                }
            }

            if (!hit) {
                val eIter = enemies.iterator()
                while (eIter.hasNext()) {
                    val e = eIter.next()
                    if (e.state == EnemyState.DYING) continue
                    val ey = e.type.size
                    val dx = nx - e.position.x
                    val dy = ny - ey
                    val dz = nz - e.position.z
                    val distSq = dx * dx + dy * dy + dz * dz
                    val r = e.type.size * 0.7f + p.size
                    if (distSq < r * r) {
                        e.health -= p.damage
                        e.hitFlash = 0.15f
                        e.state = EnemyState.STAGGER
                        e.stateTimer = 0.2f
                        hit = true
                        spawnHitParticles(nx, ny, nz, 1f, 0.3f, 0.2f, 6)
                        if (e.health <= 0) {
                            e.state = EnemyState.DYING
                            e.deathTimer = 0.5f
                            addScore(e.type.scoreValue)
                            player.kills++
                            spawnDeathParticles(e.position.x, ey, e.position.z, e.type.r, e.type.g, e.type.b, 20)
                        }
                        break
                    }
                }
            }

            if (hit) { iter.remove() } else {
                p.position.x = nx; p.position.y = ny; p.position.z = nz
                if (abs(nx) > arena.half + 2f || abs(nz) > arena.half + 2f || ny < -1f || ny > 20f) {
                    iter.remove()
                }
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        val iter = enemies.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.bobPhase += dt * 3f
            e.hitFlash = (e.hitFlash - dt * 4f).coerceAtLeast(0f)

            when (e.state) {
                EnemyState.DYING -> {
                    e.deathTimer -= dt
                    if (e.deathTimer <= 0f) iter.remove()
                }
                EnemyState.STAGGER -> {
                    e.stateTimer -= dt
                    if (e.stateTimer <= 0f) e.state = EnemyState.CHASE
                }
                EnemyState.CHASE -> {
                    val toPlayer = (player.position - e.position).xz()
                    val dist = toPlayer.length()
                    if (dist < e.type.attackRange) {
                        e.state = EnemyState.ATTACK
                        e.stateTimer = 0.4f
                    } else if (dist > 0.1f) {
                        val dir = toPlayer.normalized()
                        var nx = e.position.x + dir.x * e.type.speed * dt
                        var nz = e.position.z + dir.z * e.type.speed * dt
                        for (w in arena.walls) {
                            if (nx > w.minX - e.type.size * 0.5f && nx < w.maxX + e.type.size * 0.5f &&
                                nz > w.minZ - e.type.size * 0.5f && nz < w.maxZ + e.type.size * 0.5f) {
                                val cx = (w.minX + w.maxX) / 2f
                                val cz = (w.minZ + w.maxZ) / 2f
                                val hw = (w.maxX - w.minX) / 2f + e.type.size * 0.5f
                                val hz = (w.maxZ - w.minZ) / 2f + e.type.size * 0.5f
                                val ox = nx - cx
                                val oz = nz - cz
                                if (abs(ox) / hw > abs(oz) / hz) {
                                    nx = cx + if (ox > 0) hw else -hw
                                } else {
                                    nz = cz + if (oz > 0) hz else -hz
                                }
                            }
                        }
                        e.position.x = nx
                        e.position.z = nz
                    }
                    e.position.x = e.position.x.coerceIn(-arena.half + 0.5f, arena.half - 0.5f)
                    e.position.z = e.position.z.coerceIn(-arena.half + 0.5f, arena.half - 0.5f)
                }
                EnemyState.ATTACK -> {
                    e.stateTimer -= dt
                    if (e.stateTimer <= 0f) {
                        val dist = player.position.distTo(e.position)
                        if (dist < e.type.attackRange + 0.5f) {
                            player.health -= e.type.damage
                            player.damageFlash = 0.6f
                            player.screenShake = 0.3f
                        }
                        e.attackCooldown = 1.2f
                        e.state = EnemyState.CHASE
                    }
                }
            }

            if (e.attackCooldown > 0f) e.attackCooldown -= dt
        }
    }

    private fun updatePickups(dt: Float) {
        pickupTimer += dt
        for (p in pickups) {
            if (!p.active) {
                p.respawnTimer -= dt
                if (p.respawnTimer <= 0f) p.active = true
                continue
            }
            p.bobPhase += dt * 2.5f
            val dx = player.position.x - p.position.x
            val dz = player.position.z - p.position.z
            if (dx * dx + dz * dz < 2.5f) {
                when (p.type) {
                    PickupType.HEALTH -> {
                        if (player.health < player.maxHealth) {
                            player.health = (player.health + 25).coerceAtMost(player.maxHealth)
                            p.active = false
                            p.respawnTimer = 15f
                            spawnPickupParticles(p.position, p.type)
                        }
                    }
                    PickupType.AMMO -> {
                        player.ammo[1] = (player.ammo[1] + 8).coerceAtMost(64)
                        player.ammo[2] = (player.ammo[2] + 40).coerceAtMost(400)
                        p.active = false
                        p.respawnTimer = 12f
                        spawnPickupParticles(p.position, p.type)
                    }
                }
            }
        }
    }

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) { iter.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            p.vy -= 9.8f * dt * 0.3f
        }
    }

    private fun updateWaves(dt: Float) {
        val aliveEnemies = enemies.count { it.state != EnemyState.DYING }
        if (aliveEnemies == 0) {
            waveDelay -= dt
            if (waveDelay <= 0f) {
                wave++
                spawnWave()
                waveDelay = 2f
            }
        }
    }

    private fun spawnWave() {
        val count = (3 + wave * 2).coerceAtMost(25)
        val spawns = arena.spawnPoints.shuffled()
        for (i in 0 until count) {
            val spawnPt = spawns[i % spawns.size]
            val offset = Vec3((rng.nextFloat() - 0.5f) * 3f, 0f, (rng.nextFloat() - 0.5f) * 3f)
            val type = when {
                wave < 3 -> EnemyType.SCOUT
                wave < 5 -> if (rng.nextFloat() < 0.4f) EnemyType.SOLDIER else EnemyType.SCOUT
                wave < 8 -> when {
                    rng.nextFloat() < 0.2f -> EnemyType.TANK
                    rng.nextFloat() < 0.5f -> EnemyType.SOLDIER
                    else -> EnemyType.SCOUT
                }
                else -> when {
                    rng.nextFloat() < 0.3f -> EnemyType.TANK
                    rng.nextFloat() < 0.5f -> EnemyType.SOLDIER
                    else -> EnemyType.SCOUT
                }
            }
            enemies.add(Enemy(spawnPt + offset, type))
        }
    }

    private fun updateCombo(dt: Float) {
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }
    }

    private fun addScore(base: Int) {
        combo++
        comboTimer = 3f
        val multiplier = combo.coerceAtMost(10)
        score += base * multiplier
    }

    private fun resolvePlayerWallCollision() {
        val pr = 0.35f
        for (w in arena.walls) {
            val px = player.position.x
            val pz = player.position.z
            if (px + pr > w.minX && px - pr < w.maxX && pz + pr > w.minZ && pz - pr < w.maxZ) {
                val cx = (w.minX + w.maxX) / 2f
                val cz = (w.minZ + w.maxZ) / 2f
                val hw = (w.maxX - w.minX) / 2f + pr
                val hz = (w.maxZ - w.minZ) / 2f + pr
                val ox = px - cx
                val oz = pz - cz
                if (abs(ox) / hw > abs(oz) / hz) {
                    player.position.x = cx + if (ox > 0) hw else -hw
                } else {
                    player.position.z = cz + if (oz > 0) hz else -hz
                }
            }
        }
    }

    private fun spawnHitParticles(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int) {
        for (i in 0 until count) {
            particles.add(Particle(
                x, y, z,
                (rng.nextFloat() - 0.5f) * 6f, rng.nextFloat() * 4f + 1f, (rng.nextFloat() - 0.5f) * 6f,
                0.3f + rng.nextFloat() * 0.2f, 0.5f,
                rng.nextFloat() * 2f + 1.5f, r, g, b
            ))
        }
    }

    private fun spawnDeathParticles(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int) {
        for (i in 0 until count) {
            particles.add(Particle(
                x, y, z,
                (rng.nextFloat() - 0.5f) * 10f, rng.nextFloat() * 8f + 2f, (rng.nextFloat() - 0.5f) * 10f,
                0.5f + rng.nextFloat() * 0.5f, 1f,
                rng.nextFloat() * 3f + 2f, r, g, b
            ))
        }
    }

    private fun spawnPickupParticles(pos: Vec3, type: PickupType) {
        for (i in 0..10) {
            val a = rng.nextFloat() * PI.toFloat() * 2
            particles.add(Particle(
                pos.x, pos.y + 0.5f, pos.z,
                cos(a) * 3f, rng.nextFloat() * 5f + 2f, sin(a) * 3f,
                0.4f + rng.nextFloat() * 0.3f, 0.7f,
                rng.nextFloat() * 2f + 1.5f, type.r, type.g, type.b
            ))
        }
    }

    fun getHUDState(): HUDState {
        val ep = enemies.filter { it.state != EnemyState.DYING }.map { it.position.x to it.position.z }
        val pp = pickups.map { Triple(it.position.x, it.position.z, if (it.active) it.type.ordinal else -1) }
        return HUDState(
            player.health, player.maxHealth,
            player.ammo[player.currentWeapon], player.weapon.name, player.currentWeapon,
            score, highScore, wave, combo, comboTimer,
            player.damageFlash, gameState,
            enemies.count { it.state != EnemyState.DYING },
            player.position.x, player.position.z, player.yaw,
            ep, pp, arena.half, false, player.kills
        )
    }
}
