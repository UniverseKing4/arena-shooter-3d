package com.arena.shooter3d

import kotlin.math.*
import kotlin.random.Random

class GameEngine {
    val player = Player()
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val pickups = mutableListOf<Pickup>()
    val particles = mutableListOf<Particle>()
    val floatingTexts = mutableListOf<FloatingText>()
    val arena = Arena()
    @Volatile var gameState = GameState.MENU
    var score = 0; var highScore = 0; var wave = 0
    var combo = 0; var comboTimer = 0f
    var onHighScoreChanged: ((Int) -> Unit)? = null
    private var waveDelay = 0f
    private val moveSpeed = 6.5f
    private val sprintMultiplier = 1.7f
    private val rng = Random
    val soundEvents = mutableListOf<SoundEvent>()
    @Volatile private var pendingStart = false
    @Volatile private var pendingTogglePause = false
    var isMoving = false
    var isSprinting = false
    var headshots = 0
    var wavePopupTimer = 0f
    var weaponSwitchPopup = ""
    var weaponSwitchPopupTimer = 0f

    fun startGame() {
        pendingStart = true
    }

    fun togglePause() {
        pendingTogglePause = true
    }

    fun update(dt: Float, input: InputController) {
        soundEvents.clear()

        if (pendingStart) {
            pendingStart = false
            player.reset(); enemies.clear(); projectiles.clear(); particles.clear(); floatingTexts.clear()
            score = 0; wave = 0; combo = 0; comboTimer = 0f; waveDelay = 1.5f; headshots = 0
            gameState = GameState.PLAYING; isMoving = false; isSprinting = false
            pickups.clear()
            for ((i, spot) in arena.pickupSpots.withIndex())
                pickups.add(Pickup(spot.copy(), if (i % 2 == 0) PickupType.HEALTH else PickupType.AMMO))
            soundEvents.add(SoundEvent.GAME_START)
        }

        if (pendingTogglePause) {
            pendingTogglePause = false
            if (gameState == GameState.PLAYING) gameState = GameState.PAUSED
            else if (gameState == GameState.PAUSED) gameState = GameState.PLAYING
        }

        if (gameState != GameState.PLAYING) return
        val cdt = dt.coerceAtMost(0.05f)

        if (input.consumePause()) { gameState = GameState.PAUSED; return }

        updatePlayer(cdt, input)
        updateGunAnimation(cdt)
        updateProjectiles(cdt)
        updateEnemies(cdt)
        updatePickups(cdt)
        updateParticles(cdt)
        updateFloatingTexts(cdt)
        updateWaves(cdt)
        updateCombo(cdt)

        if (player.health <= 0) {
            gameState = GameState.GAME_OVER
            if (score > highScore) {
                highScore = score
                onHighScoreChanged?.invoke(highScore)
            }
            soundEvents.add(SoundEvent.GAME_OVER)
            spawnBurst(player.position.x, player.eyeY, player.position.z, 1f, 0.3f, 0.3f, 30)
        }
    }

    private fun updatePlayer(dt: Float, input: InputController) {
        val (ldx, ldy) = input.consumeLookDelta()
        player.yaw += ldx
        player.pitch = (player.pitch + ldy).coerceIn(-1.2f, 1.2f)

        if ((input.consumeReload() || player.mag[player.currentWeapon] <= 0) && !player.isReloading && player.swapPhase == 0) {
            val w = player.weapon
            if (player.mag[player.currentWeapon] < w.magSize && player.reserve[player.currentWeapon] > 0) {
                player.isReloading = true
                player.reloadingWeapon = player.currentWeapon
                player.reloadTimer = 0f
                player.reloadDuration = w.reloadTime
                soundEvents.add(SoundEvent.RELOAD)
            }
        }

        if (player.isReloading) {
            player.reloadTimer += dt
            if (player.reloadingWeapon == player.currentWeapon) {
                player.reloadDip = sin(player.reloadTimer / player.reloadDuration * PI.toFloat()) * 0.3f
            }
            if (player.reloadTimer >= player.reloadDuration) {
                val w = player.weapons[player.reloadingWeapon]
                val needed = w.magSize - player.mag[player.reloadingWeapon]
                val toReload = needed.coerceAtMost(player.reserve[player.reloadingWeapon])
                player.mag[player.reloadingWeapon] += toReload
                player.reserve[player.reloadingWeapon] -= toReload
                player.isReloading = false
                player.reloadTimer = 0f
                player.reloadDip = 0f
                player.reloadingWeapon = -1
                soundEvents.add(SoundEvent.RELOAD_COMPLETE)
            }
        }

        if (input.consumeWeaponSwitch() && player.swapPhase == 0) {
            player.pendingWeapon = (player.currentWeapon + 1) % player.weapons.size
            player.swapPhase = 1; player.gunSwapProgress = 0f
            soundEvents.add(SoundEvent.WEAPON_SWITCH)
            weaponSwitchPopup = player.weapons[player.pendingWeapon].name
            weaponSwitchPopupTimer = 1.5f
        }

        if (input.consumeJump() && player.isGrounded) {
            player.velocityY = 9.5f; player.isGrounded = false
        }

        player.velocityY -= 22f * dt
        player.position.y += player.velocityY * dt

        var supportHeight = 0f
        for (w in arena.walls) {
            if (w.height > 3f) continue
            val px = player.position.x; val pz = player.position.z
            if (px > w.minX - 0.4f && px < w.maxX + 0.4f && pz > w.minZ - 0.4f && pz < w.maxZ + 0.4f) {
                if (player.position.y <= w.height + 0.1f && player.position.y > w.height - 2f) {
                    if (w.height > supportHeight) supportHeight = w.height
                }
            }
        }
        if (player.position.y <= supportHeight) {
            player.position.y = supportHeight; player.velocityY = 0f; player.isGrounded = true
        } else if (player.position.y <= 0f) {
            player.position.y = 0f; player.velocityY = 0f; player.isGrounded = true
        } else if (player.velocityY < 0f && player.position.y > supportHeight + 0.1f && supportHeight == 0f) {
            player.isGrounded = false
        }

        isSprinting = input.isSprinting
        val speed = if (isSprinting) moveSpeed * sprintMultiplier else moveSpeed

        val fwd = player.flatForward(); val rgt = player.right()
        var mx = fwd.x * (-input.joyY) + rgt.x * input.joyX
        var mz = fwd.z * (-input.joyY) + rgt.z * input.joyX
        val ml = sqrt(mx * mx + mz * mz)
        isMoving = ml > 0.1f
        if (ml > 0.01f) {
            mx = mx / ml * speed * dt; mz = mz / ml * speed * dt
            player.position.x += mx; player.position.z += mz
            resolvePlayerWallCollision()
        }
        val h = arena.half - 0.4f
        player.position.x = player.position.x.coerceIn(-h, h)
        player.position.z = player.position.z.coerceIn(-h, h)

        player.fireCooldown -= dt
        if (input.isFiring && player.fireCooldown <= 0f && player.swapPhase == 0 && !player.isReloading) fireWeapon()

        player.damageFlash = (player.damageFlash - dt * 2f).coerceAtLeast(0f)
        player.screenShake = (player.screenShake - dt * 5f).coerceAtLeast(0f)
    }

    private fun updateGunAnimation(dt: Float) {
        player.gunRecoil = (player.gunRecoil - dt * 12f).coerceAtLeast(0f)
        player.muzzleFlash = (player.muzzleFlash - dt * 20f).coerceAtLeast(0f)
        if (isMoving) {
            player.gunBobPhase += dt * 7f
            if (player.gunBobPhase > 628f) player.gunBobPhase -= 628f
        }

        when (player.swapPhase) {
            1 -> {
                player.gunSwapProgress += dt * 7f
                if (player.gunSwapProgress >= 1f) {
                    player.gunSwapProgress = 1f
                    player.currentWeapon = player.pendingWeapon
                    player.swapPhase = 2
                }
            }
            2 -> {
                player.gunSwapProgress -= dt * 7f
                if (player.gunSwapProgress <= 0f) {
                    player.gunSwapProgress = 0f; player.swapPhase = 0
                }
            }
        }
    }

    private fun fireWeapon() {
        val w = player.weapon
        if (player.mag[player.currentWeapon] <= 0) return
        player.fireCooldown = w.fireRate
        player.mag[player.currentWeapon]--
        player.gunRecoil = 1f; player.muzzleFlash = 1f; player.screenShake = 0.12f

        val dir = player.forward()
        val spawnPos = Vec3(
            player.position.x + dir.x * 0.5f,
            player.eyeY + dir.y * 0.5f,
            player.position.z + dir.z * 0.5f
        )
        val rgt = player.right()
        for (i in 0 until w.pellets) {
            val sx = (rng.nextFloat() - 0.5f) * w.spread * 2
            val sy = (rng.nextFloat() - 0.5f) * w.spread * 2
            val d = Vec3(dir.x + rgt.x * sx + sy * 0f, dir.y + sy, dir.z + rgt.z * sx).normalized()
            projectiles.add(Projectile(spawnPos.copy(), d * w.projectileSpeed, w.damage, 2f, w.r, w.g, w.b, 0.1f))
        }
        soundEvents.add(when (player.currentWeapon) {
            0 -> SoundEvent.SHOOT_PISTOL; 1 -> SoundEvent.SHOOT_SHOTGUN; else -> SoundEvent.SHOOT_RIFLE
        })
        for (i in 0..4) {
            particles.add(Particle(
                spawnPos.x, spawnPos.y, spawnPos.z,
                dir.x * 8f + (rng.nextFloat() - 0.5f) * 2f,
                dir.y * 8f + (rng.nextFloat() - 0.5f) * 2f,
                dir.z * 8f + (rng.nextFloat() - 0.5f) * 2f,
                0.07f, 0.07f, rng.nextFloat() * 3f + 2.5f, 1f, 0.85f, 0.25f
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
                    hit = true; spawnBurst(p.position.x, p.position.y, p.position.z, 0.5f, 0.5f, 0.5f, 4); break
                }
            }
            if (!hit) {
                for (e in enemies) {
                    if (e.state == EnemyState.DYING) continue
                    val s = e.type.size

                    val headY = s * 1.05f
                    val headR = s * 0.28f + p.size
                    val hdx = nx - e.position.x; val hdy = ny - headY; val hdz = nz - e.position.z
                    val headDistSq = hdx * hdx + hdy * hdy + hdz * hdz
                    val isHeadshot = headDistSq < headR * headR

                    val ey = s * 0.7f
                    val dx = nx - e.position.x; val dy = ny - ey; val dz = nz - e.position.z
                    val r = s * 0.55f + p.size
                    val bodyHit = dx * dx + dy * dy + dz * dz < r * r

                    if (isHeadshot || bodyHit) {
                        val dmg = if (isHeadshot) (p.damage * 2.5f).toInt() else p.damage
                        e.health -= dmg; e.hitFlash = 0.15f
                        e.state = EnemyState.STAGGER; e.stateTimer = 0.18f
                        hit = true
                        if (isHeadshot) {
                            soundEvents.add(SoundEvent.HEADSHOT)
                            headshots++
                            spawnHeadshotBurst(nx, ny, nz)
                            floatingTexts.add(FloatingText(e.position.x, s * 1.5f, e.position.z, "HEADSHOT!", 1.5f, 1f, 0.85f, 0.15f))
                        } else {
                            soundEvents.add(SoundEvent.HIT_ENEMY)
                            spawnBurst(nx, ny, nz, 0.8f, 0.15f, 0.1f, 5)
                        }
                        if (e.health <= 0) {
                            e.state = EnemyState.DYING; e.deathTimer = 1.2f
                            e.hitFlash = 0.35f
                            addScore(e.type.scoreValue); player.kills++
                            soundEvents.add(SoundEvent.KILL_ENEMY)
                            spawnBurst(e.position.x, ey, e.position.z, e.type.bodyR, e.type.bodyG, e.type.bodyB, 18)
                        }
                        break
                    }
                }
            }
            if (hit) { iter.remove() } else {
                p.position.x = nx; p.position.y = ny; p.position.z = nz
                if (abs(nx) > arena.half + 2f || abs(nz) > arena.half + 2f || ny < -1f || ny > 20f) iter.remove()
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        val iter = enemies.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.bobPhase += dt * (if (e.state == EnemyState.CHASE) 5f else 3f)
            if (e.bobPhase > 628f) e.bobPhase -= 628f
            e.hitFlash = (e.hitFlash - dt * 5f).coerceAtLeast(0f)
            when (e.state) {
                EnemyState.DYING -> {
                    e.deathTimer -= dt
                    e.deathRotX = (e.deathRotX + dt * 120f).coerceAtMost(90f)
                    e.position.y = (e.position.y - dt * 1.5f).coerceAtLeast(0f)
                    if (e.deathTimer <= 0f) iter.remove()
                }
                EnemyState.STAGGER -> { e.stateTimer -= dt; if (e.stateTimer <= 0f) e.state = EnemyState.CHASE }
                EnemyState.CHASE -> {
                    val toPlayer = (player.position - e.position).xz()
                    val dist = toPlayer.length()
                    if (dist < e.type.attackRange) {
                        val onObstacle = player.position.y >= 0.5f && player.position.y < 1.5f
                        e.state = EnemyState.ATTACK
                        e.stateTimer = if (onObstacle) 0.65f else 0.35f
                    } else if (dist > 0.1f) {
                        val dir = toPlayer.normalized()
                        var spd = e.type.speed
                        var mx = dir.x; var mz = dir.z
                        e.strafeTimer -= dt
                        if (e.strafeTimer <= 0f) { e.strafeDir = -e.strafeDir; e.strafeTimer = 1.5f + rng.nextFloat() * 2f }
                        if (e.type == EnemyType.RUNNER && dist < 8f) {
                            val perpX = -dir.z * e.strafeDir; val perpZ = dir.x * e.strafeDir
                            mx = dir.x * 0.6f + perpX * 0.4f; mz = dir.z * 0.6f + perpZ * 0.4f
                            spd *= 1.2f
                        } else if (e.type == EnemyType.BRUTE && dist < 6f) {
                            spd *= 1.5f
                        }

                        val lookAhead = spd * 0.4f
                        val probeX = e.position.x + mx * lookAhead
                        val probeZ = e.position.z + mz * lookAhead
                        val er = e.type.size * 0.5f
                        var blocked = false
                        for (w in arena.walls) {
                            val clx = probeX.coerceIn(w.minX, w.maxX)
                            val clz = probeZ.coerceIn(w.minZ, w.maxZ)
                            val pdx = probeX - clx; val pdz = probeZ - clz
                            if (pdx * pdx + pdz * pdz < er * er * 1.5f) { blocked = true; break }
                        }
                        if (blocked) {
                            val perpX = -dir.z * e.strafeDir; val perpZ = dir.x * e.strafeDir
                            mx = dir.x * 0.3f + perpX * 0.7f; mz = dir.z * 0.3f + perpZ * 0.7f
                            val ml2 = sqrt(mx * mx + mz * mz)
                            if (ml2 > 0.01f) { mx /= ml2; mz /= ml2 }
                        }

                        var nx = e.position.x + mx * spd * dt
                        var nz = e.position.z + mz * spd * dt
                        for (pass in 0 until 3) {
                            for (w in arena.walls) {
                                val clx = nx.coerceIn(w.minX, w.maxX)
                                val clz = nz.coerceIn(w.minZ, w.maxZ)
                                val edx = nx - clx; val edz = nz - clz
                                val dSq = edx * edx + edz * edz
                                if (dSq < er * er) {
                                    if (dSq > 0.0001f) {
                                        val d = sqrt(dSq); val ov = er - d
                                        nx += (edx / d) * ov; nz += (edz / d) * ov
                                    } else {
                                        val cx = (w.minX + w.maxX) / 2f; val cz = (w.minZ + w.maxZ) / 2f
                                        val hw = (w.maxX - w.minX) / 2f + er; val hz = (w.maxZ - w.minZ) / 2f + er
                                        if (abs(nx - cx) / hw > abs(nz - cz) / hz) nx = cx + if (nx > cx) hw else -hw
                                        else nz = cz + if (nz > cz) hz else -hz
                                    }
                                }
                            }
                        }
                        e.position.x = nx.coerceIn(-arena.half + 0.5f, arena.half - 0.5f)
                        e.position.z = nz.coerceIn(-arena.half + 0.5f, arena.half - 0.5f)
                    }
                }
                EnemyState.ATTACK -> {
                    e.stateTimer -= dt
                    if (e.stateTimer <= 0f) {
                        val onObstacle = player.position.y >= 0.5f && player.position.y < 1.5f
                        val onGround = player.position.y <= 0.1f
                        val canHit = onGround || onObstacle
                        if (player.position.distTo(e.position) < e.type.attackRange + 0.5f && canHit) {
                            player.health -= e.type.damage
                            player.damageFlash = 1f; player.screenShake = 0.5f
                            soundEvents.add(SoundEvent.PLAYER_HURT)
                        }
                        e.attackCooldown = if (onObstacle) 4.0f else 1.0f
                        e.state = EnemyState.CHASE
                    }
                }
            }
            if (e.attackCooldown > 0f) e.attackCooldown -= dt
        }
    }

    private fun updatePickups(dt: Float) {
        for (p in pickups) {
            if (!p.active) { p.respawnTimer -= dt; if (p.respawnTimer <= 0f) p.active = true; continue }
            p.bobPhase += dt * 2.5f
            if (p.bobPhase > 628f) p.bobPhase -= 628f
            val dx = player.position.x - p.position.x; val dz = player.position.z - p.position.z
            if (dx * dx + dz * dz < 2.5f) {
                when (p.type) {
                    PickupType.HEALTH -> {
                        if (player.health < player.maxHealth) {
                            player.health = (player.health + 30).coerceAtMost(player.maxHealth)
                            p.active = false; p.respawnTimer = 20f
                            soundEvents.add(SoundEvent.PICKUP_HEALTH)
                            spawnPickupFX(p.position, p.type)
                        }
                    }
                    PickupType.AMMO -> {
                        player.reserve[0] = player.reserve[0] + 24
                        player.reserve[1] = player.reserve[1] + 18
                        player.reserve[2] = player.reserve[2] + 90
                        p.active = false; p.respawnTimer = 15f
                        soundEvents.add(SoundEvent.PICKUP_AMMO)
                        spawnPickupFX(p.position, p.type)
                    }
                }
            }
        }
    }

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next(); p.life -= dt
            if (!p.alive) { iter.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt; p.vy -= 8f * dt * 0.3f
        }
    }

    private fun updateFloatingTexts(dt: Float) {
        val iter = floatingTexts.iterator()
        while (iter.hasNext()) {
            val ft = iter.next(); ft.timer -= dt; ft.y += dt * 1.5f
            if (ft.timer <= 0f) iter.remove()
        }
    }

    private fun updateWaves(dt: Float) {
        if (enemies.count { it.state != EnemyState.DYING } == 0) {
            waveDelay -= dt
            if (waveDelay <= 0f) { wave++; spawnWave(); waveDelay = 2.5f; soundEvents.add(SoundEvent.WAVE_START); wavePopupTimer = 2.5f }
        }
    }

    private fun spawnWave() {
        val count = (3 + wave * 2).coerceAtMost(28)
        val spawns = arena.spawnPoints.shuffled()
        val limit = arena.half - 1.5f
        for (i in 0 until count) {
            val sp = spawns[i % spawns.size]
            val off = Vec3((rng.nextFloat() - 0.5f) * 3f, 0f, (rng.nextFloat() - 0.5f) * 3f)
            val type = when {
                wave <= 2 -> EnemyType.WALKER
                wave <= 4 -> if (rng.nextFloat() < 0.4f) EnemyType.RUNNER else EnemyType.WALKER
                wave <= 7 -> when { rng.nextFloat() < 0.15f -> EnemyType.BRUTE; rng.nextFloat() < 0.5f -> EnemyType.RUNNER; else -> EnemyType.WALKER }
                else -> when { rng.nextFloat() < 0.25f -> EnemyType.BRUTE; rng.nextFloat() < 0.5f -> EnemyType.RUNNER; else -> EnemyType.WALKER }
            }
            val pos = sp + off
            pos.x = pos.x.coerceIn(-limit, limit)
            pos.z = pos.z.coerceIn(-limit, limit)
            enemies.add(Enemy(pos, type))
        }
    }

    private fun updateCombo(dt: Float) {
        if (comboTimer > 0f) { comboTimer -= dt; if (comboTimer <= 0f) combo = 0 }
        if (wavePopupTimer > 0f) wavePopupTimer -= dt
        if (weaponSwitchPopupTimer > 0f) weaponSwitchPopupTimer -= dt
    }

    private fun addScore(base: Int) {
        combo++; comboTimer = 3f; score += base * combo.coerceAtMost(10)
    }

    private fun resolvePlayerWallCollision() {
        val pr = 0.5f
        for (pass in 0 until 4) {
            for (w in arena.walls) {
                if (player.position.y >= w.height) continue
                val px = player.position.x; val pz = player.position.z
                val closestX = px.coerceIn(w.minX, w.maxX)
                val closestZ = pz.coerceIn(w.minZ, w.maxZ)
                val dx = px - closestX; val dz = pz - closestZ
                val distSq = dx * dx + dz * dz
                if (distSq < pr * pr) {
                    if (distSq > 0.0001f) {
                        val dist = sqrt(distSq)
                        val overlap = pr - dist
                        player.position.x += (dx / dist) * overlap
                        player.position.z += (dz / dist) * overlap
                    } else {
                        val cx = (w.minX + w.maxX) / 2f; val cz = (w.minZ + w.maxZ) / 2f
                        val hw = (w.maxX - w.minX) / 2f + pr; val hz = (w.maxZ - w.minZ) / 2f + pr
                        val ox = px - cx; val oz = pz - cz
                        if (abs(ox) / hw > abs(oz) / hz) player.position.x = cx + if (ox > 0) hw else -hw
                        else player.position.z = cz + if (oz > 0) hz else -hz
                    }
                }
            }
        }
    }

    private fun spawnBurst(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, n: Int) {
        if (particles.size > 500) return
        for (i in 0 until n) particles.add(Particle(x, y, z,
            (rng.nextFloat() - 0.5f) * 8f, rng.nextFloat() * 5f + 1f, (rng.nextFloat() - 0.5f) * 8f,
            0.3f + rng.nextFloat() * 0.3f, 0.6f, rng.nextFloat() * 2.5f + 1.5f, r, g, b))
    }

    private fun spawnHeadshotBurst(x: Float, y: Float, z: Float) {
        if (particles.size > 500) return
        for (i in 0 until 22) particles.add(Particle(x, y, z,
            (rng.nextFloat() - 0.5f) * 10f, rng.nextFloat() * 7f + 2f, (rng.nextFloat() - 0.5f) * 10f,
            0.4f + rng.nextFloat() * 0.4f, 0.8f, rng.nextFloat() * 4f + 3f, 1f, 0.85f, 0.15f))
    }

    private fun spawnPickupFX(pos: Vec3, type: PickupType) {
        for (i in 0..8) { val a = rng.nextFloat() * PI.toFloat() * 2
            particles.add(Particle(pos.x, pos.y + 0.5f, pos.z,
                cos(a) * 3f, rng.nextFloat() * 5f + 2f, sin(a) * 3f,
                0.4f + rng.nextFloat() * 0.3f, 0.7f, rng.nextFloat() * 2f + 1.5f, type.r, type.g, type.b))
        }
    }

    fun consumeSoundEvents(): List<SoundEvent> {
        if (soundEvents.isEmpty()) return emptyList()
        val copy = soundEvents.toList(); soundEvents.clear(); return copy
    }

    fun getHUDState(): HUDState {
        val ep = enemies.filter { it.state != EnemyState.DYING }.map { it.position.x to it.position.z }
        val pp = pickups.map { Triple(it.position.x, it.position.z, if (it.active) it.type.ordinal else -1) }
        val reloadProgress = if (player.isReloading) (player.reloadTimer / player.reloadDuration).coerceIn(0f, 1f) else 0f
        return HUDState(player.health, player.maxHealth,
            player.mag[player.currentWeapon], player.weapon.magSize, player.reserve[player.currentWeapon],
            player.weapon.name, player.currentWeapon,
            player.isReloading, reloadProgress,
            score, highScore, wave, combo, comboTimer, player.damageFlash, gameState,
            enemies.count { it.state != EnemyState.DYING },
            player.position.x, player.position.z, player.yaw,
            ep, pp, arena.half, player.kills, emptyList(),
            isSprinting, headshots, wavePopupTimer, weaponSwitchPopup, weaponSwitchPopupTimer, player.health <= 30)
    }
}
