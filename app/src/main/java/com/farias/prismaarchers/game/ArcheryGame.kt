package com.farias.prismaarchers.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A floating text in world space ("-34", "HEADSHOT!", "+12"). kind: 0 damage, 1 head, 2 HEADSHOT, 3 heal. */
class WorldText(val text: String, val x: Float, val y: Float, val kind: Int, val born: Float)

/** Something in the scenery worth a bonus: a bird crossing the sky or a balloon drifting up. */
class Prop(val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, val phase: Float) {
    var alive = true
    var gone = false
    var spin = 0f

    companion object {
        const val BIRD = 0
        const val BALLOON = 1
    }
}

/** The helmet knocked off the enemy's head, tumbling. */
class Debris {
    var active = false
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var rot = 0f; var vr = 0f
    var resting = false
}

/**
 * Everything the game view shows: the duel, the camera, slow motion and effects. Updated once per
 * frame from the UI; no Android here (testable on the JVM).
 */
class ArcheryGame(private val listener: (DuelEvent) -> Unit) {

    enum class Mode { DEMO, PLAY }

    var mode = Mode.DEMO
        private set
    var duel = Duel(Levels.level(3), seed = 1L)
        private set
    var clock = 0f
        private set

    // Camera (world units): centre and visible width.
    var camX = 0f
        private set
    var camY = 1.5f
        private set
    var viewW = VIEW_W
        private set
    /** Slow motion factor 0..1 (1 = full slow). */
    var slow = 0f
        private set
    private var slowUntil = -1f
    var shake = 0f
        private set
    /** White flash on a headshot kill (1 → 0). */
    var flash = 0f
        private set
    /** Orange flash of an explosion (1 → 0). */
    var blastFlash = 0f
        private set
    /** "Panorama": the camera pulls back to show both archers. */
    var overview = false
    /** Opening pan: the camera starts on the enemy, then glides to the player. */
    private var introUntil = -1f
    val inIntro: Boolean get() = clock < introUntil
    /** Kill cam: 0..1 (letterbox bars, zoom on the arrow). */
    var killCam = 0f
        private set
    private var killCamOn = false
    private var killArrow: Arrow? = null
    /** Clock time of the killing blow in this duel (-1 = none yet). */
    private var killAt = -1f
    /** Water or lava below: arrows and bodies splash and sink. 0 water, 1 lava. */
    var liquidGround = false
        set(v) { field = v; duel.liquid = v }
    var lavaGround = false
    /** Ripples on the water surface: x, birth clock, strength (ring buffer). */
    val ripples = FloatArray(RIPPLES * 3)
    private var rippleNext = 0
    /** Burning (fire arrow) until this clock time, per side. */
    /** Burning until this clock time, per enemy. */
    val foeBurnUntil = FloatArray(8) { -1f }
    /** Hit-stop: the world freezes for a blink on impact (real clock). */
    private var freezeUntil = -1f
    /** Zoom punch on impact (1 → 0) and red flash when the player is hurt. */
    var punch = 0f
        private set
    var hurtFlash = 0f
        private set
    var playerBurnUntil = -1f
        private set

    val particles = Particles(900)
    val texts = ArrayList<WorldText>()
    val props = ArrayList<Prop>()
    val helmet = Debris()
    val recorder = Recorder()
    private val events = ArrayList<DuelEvent>()
    private val rnd = Random(System.nanoTime())

    // Aiming state from the UI (player turn)
    var aiming = false
        private set
    var aimAngle = 0.3f
        private set
    var aimPower = 0f
        private set

    private var demoWait = 0f
    private var demoPlan = 0.3f to 0.6f
    private var overWait = -1f

    fun startDemo() {
        mode = Mode.DEMO
        val n = 5 + rnd.nextInt(40)
        begin(Duel(Levels.level(n), seed = rnd.nextLong()))
        demoWait = 0f
    }

    fun startPlay(spec: LevelSpec, playerHp: Int = 100, shields: Int = 0) {
        mode = Mode.PLAY
        begin(Duel(spec, playerHp).also { it.shields = shields })
    }

    /** Rewarded "continue": the player stands up again with [hp]. */
    fun revive(hp: Int) {
        duel.revivePlayer(hp)
        slow = 0f
        slowUntil = -1f
        overWait = -1f
        killCamOn = false
        killArrow = null
        killAt = -1f
        recorder.reset()
        burst(duel.player.x[Ragdoll.CHEST], duel.player.y[Ragdoll.CHEST], 40, REVIVE_SPARK, 4f)
    }

    /** Next enemy of a survival run: same player (with [playerHp]), new opponent. */
    fun nextOpponent(spec: LevelSpec, playerHp: Int, shields: Int) {
        begin(Duel(spec, playerHp).also { it.shields = shields })
    }

    private fun begin(d: Duel) {
        duel = d
        d.liquid = liquidGround
        for (i in 0 until RIPPLES) ripples[i * 3 + 1] = -100f
        particles.clear()
        texts.clear()
        props.clear()
        helmet.active = false
        recorder.reset()
        slow = 0f
        slowUntil = -1f
        shake = 0f
        aiming = false
        aimPower = 0f
        overWait = -1f
        flash = 0f
        blastFlash = 0f
        killCam = 0f
        killCamOn = false
        killArrow = null
        killAt = -1f
        foeBurnUntil.fill(-1f)
        punch = 0f
        hurtFlash = 0f
        playerBurnUntil = -1f
        overview = false
        camX = d.player.rootX + VIEW_W * 0.28f
        camY = d.player.rootY + 1.6f
        viewW = VIEW_W
        if (mode == Mode.PLAY) {
            // Start on the enemy, a little wide, then glide back to the player.
            camX = d.enemy.rootX - VIEW_W * 0.2f
            camY = d.enemy.rootY + 1.6f
            viewW = VIEW_W * 1.15f
            introUntil = clock + 1.1f
            maybeSpawnProp()
        } else {
            introUntil = -1f
        }
    }

    // ------------------------------------------------------------------ input (player turn)

    fun aim(angle: Float, power: Float) {
        if (mode != Mode.PLAY || duel.turn != Duel.Turn.PLAYER) return
        aiming = true
        aimAngle = angle
        aimPower = power
        duel.aimPlayer(angle, power)
    }

    /** Shoots; returns true when the arrow (of [kind]) actually went off. */
    fun release(angle: Float, power: Float, kind: Int = ArrowKind.NORMAL): Boolean {
        if (mode != Mode.PLAY) return false
        aiming = false
        events.clear()
        val ok = duel.releasePlayer(angle, power, events, kind)
        aimPower = 0f
        handle(events)
        return ok
    }

    /** Two-player mode: the second player aims and shoots the enemy. */
    fun aimEnemy(angle: Float, power: Float) {
        if (mode != Mode.PLAY) return
        aiming = true
        aimAngle = angle
        aimPower = power
        duel.aimEnemy(angle, power)
    }

    fun releaseEnemy(angle: Float, power: Float, kind: Int = ArrowKind.NORMAL): Boolean {
        if (mode != Mode.PLAY) return false
        aiming = false
        events.clear()
        val ok = duel.releaseEnemy(angle, power, events, kind)
        aimPower = 0f
        handle(events)
        return ok
    }

    fun cancelAim() {
        aiming = false
        aimPower = 0f
        duel.aimPlayer(duel.player.aim, 0f)
    }

    // ------------------------------------------------------------------ frame

    fun update(realDt: Float) {
        val rd = realDt.coerceIn(0f, 1f / 20f)
        clock += rd
        val target = if (clock < slowUntil || killCamOn) 1f else 0f
        slow += (target - slow) * min(1f, rd * 10f)
        var dt = rd * (1f - (if (killCamOn) 0.84f else 0.78f) * slow)
        if (clock < freezeUntil) dt = 0f
        if (mode == Mode.DEMO) demoBrain(dt)
        // Fixed sub-steps for stable physics.
        var left = dt
        while (left > 1e-5f) {
            val h = min(left, 1f / 120f)
            events.clear()
            duel.step(h, events)
            handle(events)
            stepProps(h)
            left -= h
        }
        stepHelmet(dt)
        burning(duel.player, playerBurnUntil)
        dust(duel.player)
        water(duel.player)
        for (f in duel.foes) {
            burning(f.body, foeBurnUntil[f.index.coerceAtMost(7)])
            dust(f.body)
            water(f.body)
        }
        punch *= exp(-rd * 7f)
        hurtFlash *= exp(-rd * 4f)
        particles.update(dt)
        texts.removeAll { clock - it.born > 1.4f }
        shake *= exp(-rd * 8f)
        flash *= exp(-rd * 5f)
        blastFlash *= exp(-rd * 6f)
        updateKillCam(rd)
        updateCamera(rd)
        if (mode == Mode.PLAY) recorder.record(this, rd)
        if (mode == Mode.DEMO && duel.finished) {
            if (overWait < 0f) overWait = clock
            if (clock - overWait > 1.5f) startDemo()
        }
    }

    /** Turns the kill cam on when an arrow in the air is about to land a killing blow. */
    private fun updateKillCam(rd: Float) {
        val d = duel
        if (mode == Mode.PLAY && !killCamOn && d.turn != Duel.Turn.OVER) {
            for (a in d.inFlight) {
                if (d.predictKill(a, 0.55f)) {
                    killCamOn = true
                    killArrow = a
                    break
                }
            }
        }
        if (killCamOn) {
            val a = killArrow
            val missed = killAt < 0f && (a == null || a !in d.inFlight)
            // Ends shortly after the blow lands (the body's fall is the last beat).
            if (missed || (killAt >= 0f && clock > killAt + KILL_CAM_AFTER)) {
                killCamOn = false
                killArrow = null
            }
        }
        val t = if (killCamOn) 1f else 0f
        killCam += (t - killCam) * min(1f, rd * 6f)
    }

    /** In the menu demo the player side is a bot too. */
    private fun demoBrain(dt: Float) {
        val d = duel
        if (d.turn != Duel.Turn.PLAYER) return
        if (demoWait == 0f) {
            val s = Duel.solveShot(d.player.bowX(), d.player.bowY(), 1f, d.enemy.x[Ragdoll.CHEST], d.enemy.y[Ragdoll.CHEST], d.spec.wind)
            demoPlan = (s.first + (rnd.nextFloat() - 0.5f) * 0.08f) to (s.second + (rnd.nextFloat() - 0.5f) * 0.1f)
        }
        demoWait += dt
        d.aimPlayer(d.player.aim + (demoPlan.first - d.player.aim) * min(1f, dt * 4f), min(1f, demoWait / 0.9f) * demoPlan.second)
        if (demoWait > 1.1f) {
            events.clear()
            d.releasePlayer(demoPlan.first, demoPlan.second, events)
            handle(events)
            demoWait = 0f
        }
    }

    /** A puff of dust when a falling body slams into the ground. */
    private fun dust(r: Ragdoll) {
        if (r.thud > 0.035f) {
            val wet = liquidGround && r.thudY <= duel.groundY + 0.1f
            burst(r.thudX, r.thudY + 0.05f, (10 + r.thud * 300).toInt().coerceAtMost(if (wet) 50 else 30), if (wet) (if (lavaGround) FIRE_A else WATER) else DUST, (1.5f + r.thud * 30f) * if (wet) 1.6f else 1f)
            shake = max(shake, (r.thud * 6f).coerceAtMost(0.35f))
        }
        r.thud = 0f
    }

    /** Splash when a body hits the water, then bubbles while it sinks. */
    private fun water(r: Ragdoll) {
        if (!liquidGround) return
        val surf = duel.groundY
        if (r.splash > 0.02f) {
            val power = (r.splash * 120f).coerceIn(4f, 18f) // entry speed, m/s
            splash(r.splashX, surf, power)
            shake = max(shake, (power / 40f).coerceAtMost(0.4f))
            r.splash = 0f
        }
        if (r.underwater > 0f && r.underwater < 2.5f && rnd.nextFloat() < 0.35f) {
            val i = if (rnd.nextBoolean()) Ragdoll.HEAD else Ragdoll.CHEST
            val c = if (lavaGround) FIRE_B else BUBBLE
            particles.add(r.x[i] + (rnd.nextFloat() - 0.5f) * 0.3f, min(r.y[i], surf - 0.05f), (rnd.nextFloat() - 0.5f) * 0.2f, 0.6f + rnd.nextFloat() * 0.6f,
                c[0], c[1], c[2], 0.03f + rnd.nextFloat() * 0.04f, 0.5f + rnd.nextFloat() * 0.6f, -1.5f)
        }
    }

    /** Water flying up: a crown of droplets, a tall column, mist and expanding ripples. */
    fun splash(x: Float, surf: Float, power: Float) {
        val c = if (lavaGround) FIRE_A else WATER
        val n = (power * 6f).toInt().coerceIn(20, 110)
        repeat(n) {
            // Mostly upwards, fanning out to the sides
            val a = 1.5708f + (rnd.nextFloat() - 0.5f) * 2.2f
            val sp = power * (0.25f + rnd.nextFloat() * 0.55f)
            particles.add(x + (rnd.nextFloat() - 0.5f) * 0.4f, surf + 0.05f, cos(a) * sp * 0.6f, sin(a) * sp, c[0], c[1], c[2], 0.035f + rnd.nextFloat() * 0.06f, 0.7f + rnd.nextFloat() * 0.7f)
        }
        // Central column
        repeat(n / 3) {
            particles.add(x + (rnd.nextFloat() - 0.5f) * 0.25f, surf, (rnd.nextFloat() - 0.5f) * 0.6f, power * (0.6f + rnd.nextFloat() * 0.5f), 0.92f, 0.97f, 1f, 0.05f + rnd.nextFloat() * 0.05f, 0.8f + rnd.nextFloat() * 0.5f)
        }
        // Mist
        repeat(n / 4) {
            val a = rnd.nextFloat() * 3.1416f
            particles.add(x, surf + 0.1f, cos(a) * 2.5f, sin(a) * 1.2f, 0.95f, 0.98f, 1f, 0.09f + rnd.nextFloat() * 0.08f, 0.5f + rnd.nextFloat() * 0.4f, 1f)
        }
        ripple(x, (power / 10f).coerceIn(0.4f, 1.6f))
        ripple(x, (power / 14f).coerceIn(0.3f, 1.2f), 0.25f)
    }

    private fun ripple(x: Float, strength: Float, delay: Float = 0f) {
        val i = rippleNext % RIPPLES
        rippleNext++
        ripples[i * 3] = x
        ripples[i * 3 + 1] = clock + delay
        ripples[i * 3 + 2] = strength
    }

    /** Little flames rising from a body hit by a fire arrow. */
    private fun burning(r: Ragdoll, until: Float) {
        if (clock > until) return
        repeat(2) {
            val i = if (rnd.nextBoolean()) Ragdoll.CHEST else Ragdoll.PELVIS
            val c = if (rnd.nextFloat() < 0.5f) FIRE_A else FIRE_B
            particles.add(r.x[i] + (rnd.nextFloat() - 0.5f) * 0.3f, r.y[i] + (rnd.nextFloat() - 0.5f) * 0.3f, (rnd.nextFloat() - 0.5f) * 0.6f, 1.5f + rnd.nextFloat() * 1.5f, c[0], c[1], c[2], 0.04f + rnd.nextFloat() * 0.05f, 0.35f + rnd.nextFloat() * 0.3f)
        }
    }

    // ------------------------------------------------------------------ scenery bonuses

    /** Sometimes a bird crosses the sky or a balloon drifts up between the archers. */
    private fun maybeSpawnProp() {
        if (mode != Mode.PLAY || props.count { it.alive } >= 2) return
        if (rnd.nextFloat() > 0.45f) return
        val d = duel
        val midY = (d.player.rootY + d.enemy.rootY) / 2f
        if (d.foes.isEmpty()) return
        if (rnd.nextFloat() < 0.6f) {
            val fromLeft = rnd.nextBoolean()
            val x = if (fromLeft) -6f else d.spec.distance + 6f
            val sp = (1.6f + rnd.nextFloat() * 1.4f) * (if (fromLeft) 1f else -1f)
            props.add(Prop(Prop.BIRD, x, midY + 4f + rnd.nextFloat() * 4f, sp, 0f, rnd.nextFloat() * 6f))
        } else {
            val x = d.spec.distance * (0.3f + rnd.nextFloat() * 0.4f)
            props.add(Prop(Prop.BALLOON, x, midY - 3f, 0f, 0.45f + rnd.nextFloat() * 0.25f, rnd.nextFloat() * 6f))
        }
    }

    private fun stepProps(dt: Float) {
        if (props.isEmpty()) return
        val d = duel
        for (p in props) {
            if (p.gone) continue
            if (p.alive) {
                p.x += p.vx * dt
                p.y += p.vy * dt + (if (p.kind == Prop.BIRD) sin(clock * 2f + p.phase) * 0.004f else 0f)
                if (p.kind == Prop.BALLOON) p.x += sin(clock * 0.8f + p.phase) * 0.003f
                // Player arrows in the air pop it.
                for (a in d.inFlight) {
                    if (!a.byPlayer) continue
                    val tx = a.x + cos(a.angle) * 0.3f
                    val ty = a.y + sin(a.angle) * 0.3f
                    val r = if (p.kind == Prop.BIRD) 0.42f else 0.5f
                    val dx = tx - p.x
                    val dy = ty - (p.y + if (p.kind == Prop.BALLOON) 0.15f else 0f)
                    if (dx * dx + dy * dy < r * r) {
                        p.alive = false
                        if (p.kind == Prop.BIRD) {
                            p.vx = a.vx * 0.15f
                            p.vy = 1.5f
                            burst(p.x, p.y, 24, FEATHER, 3f)
                        } else {
                            burst(p.x, p.y + 0.15f, 40, BALLOON_C, 5f)
                            p.gone = true
                        }
                        val e = DuelEvent.Bonus(p.kind, p.x, p.y)
                        if (mode == Mode.PLAY) listener(e)
                        break
                    }
                }
                if (abs(p.x - d.spec.distance / 2f) > d.spec.distance / 2f + 10f || p.y > 25f) p.gone = true
            } else {
                // Shot bird tumbling down.
                p.vy -= GRAVITY * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.spin += dt * 9f
                if (p.y < -30f) p.gone = true
            }
        }
        props.removeAll { it.gone }
    }

    private fun stepHelmet(dt: Float) {
        val h = helmet
        if (!h.active || h.resting) return
        h.vy -= GRAVITY * dt
        h.x += h.vx * dt
        h.y += h.vy * dt
        h.rot += h.vr * dt
        for (p in duel.platforms) {
            if (h.x in p.x0..p.x1 && h.y < p.top + 0.12f && h.y > p.top - 1f && h.vy < 0f) {
                h.y = p.top + 0.12f
                h.vy = -h.vy * 0.35f
                h.vx *= 0.6f
                h.vr *= 0.5f
                if (abs(h.vy) < 0.6f) { h.resting = true; h.rot = 180f }
            }
        }
        if (h.y < -30f) h.active = false
    }

    private fun updateCamera(dt: Float) {
        val d = duel
        val a = d.flying
        var tx: Float
        var ty: Float
        var tw = VIEW_W
        val ka = killArrow
        when {
            killCamOn && killAt < 0f && ka != null -> {
                // Kill cam: tight on the arrow until it lands.
                tx = ka.x + ka.vx * 0.05f
                ty = ka.y + 0.2f
                tw = VIEW_W * 0.6f
            }
            killAt >= 0f -> {
                // After the killing blow: on the body (never on an arrow flying on).
                val loser = if (d.playerHp <= 0) d.player else d.foes.getOrNull(d.lastFallen)?.body ?: d.enemy
                tx = loser.x[Ragdoll.PELVIS]
                ty = loser.y[Ragdoll.PELVIS] + 0.6f
                tw = VIEW_W * (if (killCamOn) 0.7f else 1f)
            }
            clock < introUntil -> {
                tx = d.enemy.rootX - VIEW_W * 0.2f
                ty = d.enemy.rootY + 1.6f
                tw = VIEW_W * 1.15f
            }
            overview && d.turn != Duel.Turn.OVER -> {
                // Both archers in view (the arrow too, it's always between them or near).
                // Player and every enemy still standing (capped so they don't get too tiny).
                var x0 = d.player.rootX
                var x1 = d.player.rootX
                var y0 = d.player.rootY
                var y1 = d.player.rootY
                for (f in d.foes) {
                    if (!f.alive && f.index != d.activeFoe) continue
                    x0 = min(x0, f.body.rootX); x1 = max(x1, f.body.rootX)
                    y0 = min(y0, f.body.rootY); y1 = max(y1, f.body.rootY)
                }
                for (o in d.obstacles) if (o.alive && o.kind == ObstacleKind.TARGET) { x1 = max(x1, o.x1) }
                x1 = min(x1, x0 + 48f)
                tx = (x0 + x1) / 2f
                ty = (y0 + y1) / 2f + 1.4f
                tw = (x1 - x0) + 7f
                if (a != null && (a.x < x0 - 3f || a.x > x1 + 3f)) tx = a.x.coerceIn(x0 - 6f, x1 + 6f)
            }
            a != null -> {
                // Follow the arrow, a bit ahead of it.
                tx = a.x + a.vx * 0.12f
                ty = a.y + 0.4f
            }
            d.turn == Duel.Turn.OVER -> {
                val loser = if (d.playerWon) (d.foes.getOrNull(d.lastFallen)?.body ?: d.enemy) else d.player
                tx = loser.x[Ragdoll.PELVIS]
                ty = loser.y[Ragdoll.PELVIS] + 0.6f
                tw = VIEW_W * (1f - 0.25f * slow)
            }
            d.turn == Duel.Turn.ENEMY || d.turn == Duel.Turn.ENEMY_ARROW -> {
                tx = d.enemy.rootX - VIEW_W * 0.28f
                ty = d.enemy.rootY + 1.6f
            }
            else -> {
                tx = d.player.rootX + VIEW_W * 0.28f
                ty = d.player.rootY + 1.6f
            }
        }
        if (slow > 0.05f && d.turn == Duel.Turn.OVER) tw = VIEW_W * (if (killCam > 0.1f) 0.65f else 1f - 0.3f * slow)
        val k = when {
            killCamOn -> min(1f, dt * 12f)
            overview -> min(1f, dt * 4f)
            a != null -> min(1f, dt * 9f)
            else -> min(1f, dt * 3.2f)
        }
        camX += (tx - camX) * k
        camY += (ty - camY) * k
        viewW += (tw - viewW) * min(1f, dt * if (killCamOn) 5f else if (overview) 3.5f else 4f)
    }

    // ------------------------------------------------------------------ events → effects

    private fun handle(list: List<DuelEvent>) {
        for (e in list) {
            when (e) {
                is DuelEvent.Hit -> {
                    // Impact weight: a blink of hit-stop and a zoom punch; coins pop out of hit enemies.
                    if (e.damage > 0) {
                        freezeUntil = clock + if (e.kill) 0.08f else 0.05f
                        punch = if (e.kill) 1f else 0.6f
                    }
                    if (!e.byPlayer && e.damage > 0) hurtFlash = 1f
                    if (e.byPlayer && e.damage > 0 && mode == Mode.PLAY) {
                        repeat(4 + e.damage / 20) {
                            particles.add(e.x, e.y, (rnd.nextFloat() - 0.5f) * 3f, 3f + rnd.nextFloat() * 3f, COIN_C[0], COIN_C[1], COIN_C[2], 0.07f, 0.9f)
                        }
                    }
                    val c = if (e.part == Part.HEAD) HEAD_SPARK else HIT_SPARK
                    burst(e.x, e.y, if (e.kill) 60 else 26, c, if (e.kill) 6f else 4f)
                    if (e.pierce) burst(e.x, e.y, 30, HEAD_SPARK, 8f)
                    shake = max(shake, if (e.kill) 0.5f else 0.25f)
                    if (e.damage > 0) texts.add(WorldText("-${e.damage}", e.x, e.y + 0.4f, if (e.part == Part.HEAD) 1 else 0, clock))
                    if (e.part == Part.HEAD && e.damage >= 100) {
                        texts.add(WorldText("HEADSHOT", e.x, e.y + 0.9f, 2, clock))
                        flash = 1f
                    }
                    if (e.kind == ArrowKind.FIRE) {
                        burst(e.x, e.y, 30, FIRE_A, 4f)
                        if (e.byPlayer) { if (e.foe in 0..7) foeBurnUntil[e.foe] = clock + 2.5f } else playerBurnUntil = clock + 2.5f
                    }
                    if (e.kind == ArrowKind.ICE) burst(e.x, e.y, 30, ICE_C, 3.5f)
                    if (e.kill || (e.part == Part.HEAD && e.byPlayer)) {
                        slowUntil = clock + if (e.kill) 1.4f else 0.6f
                    }
                    if (e.kill && e.final) {
                        recorder.markKill()
                        killAt = clock
                        slowUntil = clock + 1.1f
                    } else if (e.kill) {
                        // One of several enemies down: a short slow-motion beat.
                        slowUntil = clock + 0.5f
                    }
                }
                is DuelEvent.Miss -> if (e.ground) {
                    if (liquidGround && e.y <= duel.groundY + 0.3f) {
                        // Small splash: the arrow sinks.
                        splash(e.x, duel.groundY, 5f)
                    } else {
                        burst(e.x, e.y, 14, DUST, 2.5f)
                    }
                }
                is DuelEvent.Explode -> {
                    burst(e.x, e.y, 70, FIRE_A, 7f)
                    burst(e.x, e.y, 40, FIRE_B, 5f)
                    burst(e.x, e.y, 30, SMOKE, 3f)
                    shake = max(shake, 0.9f)
                    blastFlash = 1f
                    if (e.damage > 0) texts.add(WorldText("-${e.damage}", e.x, e.y + 0.6f, 0, clock))
                    punch = 1f
                    freezeUntil = clock + 0.06f
                    if (e.final) {
                        slowUntil = clock + 1.1f
                        killAt = clock
                        recorder.markKill()
                    } else if (e.kill) slowUntil = clock + 0.5f
                }
                is DuelEvent.HelmetOff -> {
                    helmet.active = true
                    helmet.resting = false
                    helmet.x = e.x; helmet.y = e.y; helmet.vx = e.vx; helmet.vy = e.vy
                    helmet.rot = 0f
                    helmet.vr = 520f * (if (e.vx > 0f) 1f else -1f)
                    burst(e.x, e.y, 24, METAL, 5f)
                    shake = max(shake, 0.35f)
                }
                is DuelEvent.ShieldHit -> {
                    burst(e.x, e.y, 40, SHIELD_C, 5f)
                    shake = max(shake, 0.3f)
                    val sb = duel.foes.getOrNull(e.foe)?.body
                    if (e.left == 0 && sb != null) burst(sb.x[Ragdoll.CHEST], sb.y[Ragdoll.CHEST], 60, SHIELD_C, 6f)
                }
                is DuelEvent.Frozen -> burst(e.x, e.y, 40, ICE_C, 3f)
                is DuelEvent.Thaw -> burst(e.x, e.y, 30, ICE_C, 2.5f)
                is DuelEvent.Fell -> {
                    // Shoved off the edge: slow motion on the fall.
                    if (e.final) {
                        killAt = clock
                        slowUntil = clock + 1.1f
                        recorder.markKill()
                    } else slowUntil = clock + 0.6f
                    shake = max(shake, 0.4f)
                    burst(e.x, e.y, 20, DUST, 2.5f)
                }
                is DuelEvent.Heal -> {
                    texts.add(WorldText("+${e.amount}", e.x, e.y, 3, clock))
                    burst(e.x, e.y - 0.4f, 24, HEAL_C, 2f)
                }
                is DuelEvent.TurnStart -> if (e.player) maybeSpawnProp()
                is DuelEvent.ObstacleHit -> {
                    val c = when (e.kind) {
                        ObstacleKind.WOOD, ObstacleKind.CRATE -> WOOD_C
                        ObstacleKind.WALL -> DUST
                        else -> METAL
                    }
                    burst(e.x, e.y, if (e.broken) 45 else 12, c, if (e.broken) 5f else 2.5f)
                    if (e.broken) shake = max(shake, 0.3f)
                    if (e.kind == ObstacleKind.CRATE && e.broken) repeat(10) {
                        particles.add(e.x, e.y, (rnd.nextFloat() - 0.5f) * 4f, 3f + rnd.nextFloat() * 4f, COIN_C[0], COIN_C[1], COIN_C[2], 0.08f, 1.1f)
                    }
                }
                is DuelEvent.Ricochet -> {
                    burst(e.x, e.y, 22, HEAD_SPARK, 5f)
                    punch = 0.5f
                }
                is DuelEvent.TargetHit -> {
                    burst(e.x, e.y, 30 + e.points * 3, if (e.points >= 10) HEAD_SPARK else WOOD_C, 4f)
                    texts.add(WorldText("+${e.points}", e.x, e.y + 0.5f, if (e.points >= 10) 1 else 0, clock))
                    if (e.points >= 10) { flash = 0.6f; freezeUntil = clock + 0.07f }
                    punch = 0.6f
                }
                else -> Unit
            }
            if (mode == Mode.PLAY) listener(e)
        }
    }

    private fun burst(x: Float, y: Float, n: Int, color: FloatArray, speed: Float) {
        repeat(n) {
            val a = rnd.nextFloat() * 6.283f
            val sp = speed * (0.3f + rnd.nextFloat())
            particles.add(x, y, cos(a) * sp, sin(a) * sp + 1.5f, color[0], color[1], color[2], 0.03f + rnd.nextFloat() * 0.05f, 0.4f + rnd.nextFloat() * 0.5f)
        }
    }

    /** Player's shot guide: points of the first part of the flight. */
    fun guide(out: FloatArray): Int {
        if (duel.versus && duel.turn == Duel.Turn.ENEMY) {
            val e = duel.enemy
            return Duel.preview(e.bowX(), e.bowY(), -1f, aimAngle, aimPower, duel.spec.wind, guideSeconds, out)
        }
        val p = duel.player
        return Duel.preview(p.bowX(), p.bowY(), 1f, aimAngle, aimPower, duel.spec.wind, guideSeconds, out)
    }

    /** How much of the flight the aim guide shows (seconds; the Guide talent makes it longer). */
    var guideSeconds = 0.38f

    companion object {
        /** Visible world width (meters) in portrait. */
        const val VIEW_W = 11f
        val HIT_SPARK = floatArrayOf(1f, 0.35f, 0.45f)
        val HEAD_SPARK = floatArrayOf(1f, 0.85f, 0.3f)
        val DUST = floatArrayOf(0.85f, 0.75f, 0.6f)
        val REVIVE_SPARK = floatArrayOf(0.5f, 1f, 0.7f)
        val FIRE_A = floatArrayOf(1f, 0.55f, 0.1f)
        val FIRE_B = floatArrayOf(1f, 0.85f, 0.25f)
        val SMOKE = floatArrayOf(0.35f, 0.33f, 0.35f)
        val ICE_C = floatArrayOf(0.65f, 0.92f, 1f)
        val METAL = floatArrayOf(0.85f, 0.88f, 0.92f)
        val SHIELD_C = floatArrayOf(0.5f, 0.85f, 1f)
        val HEAL_C = floatArrayOf(0.45f, 1f, 0.55f)
        val FEATHER = floatArrayOf(0.95f, 0.95f, 0.98f)
        val BALLOON_C = floatArrayOf(1f, 0.3f, 0.45f)
        val COIN_C = floatArrayOf(1f, 0.82f, 0.2f)
        val WOOD_C = floatArrayOf(0.62f, 0.42f, 0.22f)
        val WATER = floatArrayOf(0.62f, 0.86f, 1f)
        val BUBBLE = floatArrayOf(0.85f, 0.95f, 1f)
        const val RIPPLES = 8
        /** Seconds the kill cam lasts after the blow lands. */
        const val KILL_CAM_AFTER = 1.3f
    }
}

/**
 * Keeps the last few seconds of the duel (bodies, arrows, camera) at 30 fps, so the killing blow can
 * be replayed on the victory screen. Fixed arrays: no allocations while recording.
 */
class Recorder {
    val pX = FloatArray(CAP * Ragdoll.N); val pY = FloatArray(CAP * Ragdoll.N)
    val eX = FloatArray(CAP * Ragdoll.N * FOES); val eY = FloatArray(CAP * Ragdoll.N * FOES)
    val alive = BooleanArray(CAP * (1 + FOES))
    val foeCount = IntArray(CAP)
    val cam = FloatArray(CAP * 3)
    val ax = FloatArray(CAP * ARROWS); val ay = FloatArray(CAP * ARROWS); val aa = FloatArray(CAP * ARROWS)
    val aKind = IntArray(CAP * ARROWS)
    val aCount = IntArray(CAP)
    val helmet = BooleanArray(CAP * FOES)
    /** Frames written so far (frame f lives at f % CAP). */
    var frames = 0
        private set
    var killFrame = -1
        private set
    private var acc = 0f
    private var afterKill = 0f

    fun reset() { frames = 0; killFrame = -1; acc = 0f; afterKill = 0f }

    fun markKill() { if (killFrame < 0) killFrame = frames }

    /** True once enough has been recorded after the kill. */
    val ready: Boolean get() = killFrame >= 0 && afterKill > AFTER

    fun record(g: ArcheryGame, rd: Float) {
        if (killFrame >= 0) {
            afterKill += rd
            if (afterKill > AFTER) return
        }
        acc += rd
        if (acc < 1f / 30f) return
        acc -= 1f / 30f
        val i = frames % CAP
        val d = g.duel
        System.arraycopy(d.player.x, 0, pX, i * Ragdoll.N, Ragdoll.N)
        System.arraycopy(d.player.y, 0, pY, i * Ragdoll.N, Ragdoll.N)
        val nf = min(FOES, d.foes.size)
        foeCount[i] = nf
        alive[i * (1 + FOES)] = d.player.alive
        for (q in 0 until nf) {
            val b = d.foes[q].body
            System.arraycopy(b.x, 0, eX, (i * FOES + q) * Ragdoll.N, Ragdoll.N)
            System.arraycopy(b.y, 0, eY, (i * FOES + q) * Ragdoll.N, Ragdoll.N)
            alive[i * (1 + FOES) + 1 + q] = b.alive
            helmet[i * FOES + q] = d.foes[q].helmetOn
        }
        cam[i * 3] = g.camX; cam[i * 3 + 1] = g.camY; cam[i * 3 + 2] = g.viewW
        var n = 0
        // Arrows in the air first, then the newest stuck in bodies.
        for (a in d.inFlight) {
            if (n == ARROWS) break
            put(i, n++, a)
        }
        var k = d.arrows.size - 1
        while (k >= 0 && n < ARROWS) {
            val a = d.arrows[k]
            if (a.stuckIn != null || (!a.flying && a.stuckTime >= 0f)) put(i, n++, a)
            k--
        }
        aCount[i] = n
        frames++
    }

    private fun put(i: Int, n: Int, a: Arrow) {
        val j = i * ARROWS + n
        ax[j] = a.x; ay[j] = a.y; aa[j] = a.angle
        aKind[j] = if (a.byPlayer) a.kind else -1
    }

    /** First and last frame of the replay clip (inclusive), or null. */
    fun clip(): IntArray? {
        if (killFrame < 0) return null
        val first = max(max(0, frames - CAP), killFrame - BEFORE)
        return intArrayOf(first, frames - 1)
    }

    companion object {
        const val CAP = 160
        const val FOES = 5
        const val ARROWS = 8
        /** Frames before the kill shown in the replay (≈1.7 s) and seconds kept after it. */
        const val BEFORE = 50
        const val AFTER = 2.2f
    }
}

/** Simple 2D particles in world space (struct of arrays, no allocations per frame). */
class Particles(val max: Int) {
    val x = FloatArray(max); val y = FloatArray(max); val vx = FloatArray(max); val vy = FloatArray(max)
    val r = FloatArray(max); val g = FloatArray(max); val b = FloatArray(max)
    val size = FloatArray(max); val life = FloatArray(max); val maxLife = FloatArray(max)
    /** Gravity per particle (m/s²): 9 falls, negative rises (bubbles). */
    val grav = FloatArray(max)
    var count = 0
        private set

    fun clear() { count = 0 }

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, cr: Float, cg: Float, cb: Float, s: Float, l: Float, gravity: Float = 9f) {
        val i = if (count < max) count++ else Random.nextInt(max)
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; r[i] = cr; g[i] = cg; b[i] = cb; size[i] = s; life[i] = l; maxLife[i] = l
        grav[i] = gravity
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                if (i != last) {
                    x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]; r[i] = r[last]; g[i] = g[last]; b[i] = b[last]
                    size[i] = size[last]; life[i] = life[last]; maxLife[i] = maxLife[last]; grav[i] = grav[last]
                }
                count--
                continue
            }
            vy[i] -= grav[i] * dt
            vx[i] *= 0.985f
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }
}

