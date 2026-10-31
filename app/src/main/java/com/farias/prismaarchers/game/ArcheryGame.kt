package com.farias.prismaarchers.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A floating text in world space ("-34", "HEADSHOT!"). */
class WorldText(val text: String, val x: Float, val y: Float, val kind: Int, val born: Float)

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
    /** "Panorama": the camera pulls back to show both archers. */
    var overview = false
    /** Opening pan: the camera starts on the enemy, then glides to the player. */
    private var introUntil = -1f
    val inIntro: Boolean get() = clock < introUntil

    val particles = Particles(900)
    val texts = ArrayList<WorldText>()
    private val events = ArrayList<DuelEvent>()
    private val rnd = Random(System.nanoTime())

    // Aiming state from the UI (player turn)
    var aiming = false
        private set
    var aimAngle = 0.3f
        private set
    var aimPower = 0f
        private set
    /** Arrow look of each side (trail colour etc.). */
    var playerTrail = 0
    var enemyTrail = 0

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
        burst(duel.player.x[Ragdoll.CHEST], duel.player.y[Ragdoll.CHEST], 40, REVIVE_SPARK, 4f)
    }

    /** Next enemy of a survival run: same player (with [playerHp]), new opponent. */
    fun nextOpponent(spec: LevelSpec, playerHp: Int, shields: Int) {
        begin(Duel(spec, playerHp).also { it.shields = shields })
    }

    private fun begin(d: Duel) {
        duel = d
        particles.clear()
        texts.clear()
        slow = 0f
        slowUntil = -1f
        shake = 0f
        aiming = false
        aimPower = 0f
        overWait = -1f
        flash = 0f
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

    fun release(angle: Float, power: Float) {
        if (mode != Mode.PLAY) return
        aiming = false
        events.clear()
        duel.releasePlayer(angle, power, events)
        aimPower = 0f
        handle(events)
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
        val target = if (clock < slowUntil) 1f else 0f
        slow += (target - slow) * min(1f, rd * 10f)
        val dt = rd * (1f - 0.78f * slow)
        if (mode == Mode.DEMO) demoBrain(dt)
        // Fixed sub-steps for stable physics.
        var left = dt
        while (left > 1e-5f) {
            val h = min(left, 1f / 120f)
            events.clear()
            duel.step(h, events)
            handle(events)
            left -= h
        }
        dust(duel.player)
        dust(duel.enemy)
        particles.update(dt)
        texts.removeAll { clock - it.born > 1.4f }
        shake *= exp(-rd * 8f)
        flash *= exp(-rd * 5f)
        updateCamera(rd)
        if (mode == Mode.DEMO && duel.finished) {
            if (overWait < 0f) overWait = clock
            if (clock - overWait > 1.5f) startDemo()
        }
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
            burst(r.thudX, r.thudY + 0.05f, (10 + r.thud * 300).toInt().coerceAtMost(30), DUST, 1.5f + r.thud * 30f)
            shake = max(shake, (r.thud * 6f).coerceAtMost(0.35f))
        }
        r.thud = 0f
    }

    private fun updateCamera(dt: Float) {
        val d = duel
        val a = d.flying
        var tx: Float
        var ty: Float
        var tw = VIEW_W
        when {
            overview && d.turn != Duel.Turn.OVER -> {
                // Both archers in view (the arrow too, it's always between them or near).
                val x0 = min(d.player.rootX, d.enemy.rootX)
                val x1 = max(d.player.rootX, d.enemy.rootX)
                tx = (x0 + x1) / 2f
                ty = (d.player.rootY + d.enemy.rootY) / 2f + 1.4f
                tw = (x1 - x0) + 7f
                if (a != null && (a.x < x0 - 3f || a.x > x1 + 3f)) tx = a.x.coerceIn(x0 - 6f, x1 + 6f)
            }
            clock < introUntil -> {
                tx = d.enemy.rootX - VIEW_W * 0.2f
                ty = d.enemy.rootY + 1.6f
                tw = VIEW_W * 1.15f
            }
            a != null -> {
                // Follow the arrow, a bit ahead of it.
                tx = a.x + a.vx * 0.12f
                ty = a.y + 0.4f
            }
            d.turn == Duel.Turn.OVER -> {
                val loser = if (d.playerWon) d.enemy else d.player
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
        if (slow > 0.05f && d.turn == Duel.Turn.OVER) tw = VIEW_W * (1f - 0.3f * slow)
        val k = when {
            overview -> min(1f, dt * 4f)
            a != null -> min(1f, dt * 9f)
            else -> min(1f, dt * 3.2f)
        }
        camX += (tx - camX) * k
        camY += (ty - camY) * k
        viewW += (tw - viewW) * min(1f, dt * if (overview) 3.5f else 4f)
    }

    // ------------------------------------------------------------------ events → effects

    private fun handle(list: List<DuelEvent>) {
        for (e in list) {
            when (e) {
                is DuelEvent.Hit -> {
                    val c = if (e.part == Part.HEAD) HEAD_SPARK else HIT_SPARK
                    burst(e.x, e.y, if (e.kill) 60 else 26, c, if (e.kill) 6f else 4f)
                    shake = max(shake, if (e.kill) 0.5f else 0.25f)
                    texts.add(WorldText("-${e.damage}", e.x, e.y + 0.4f, if (e.part == Part.HEAD) 1 else 0, clock))
                    if (e.part == Part.HEAD && e.damage >= 100) {
                        texts.add(WorldText("HEADSHOT", e.x, e.y + 0.9f, 2, clock))
                        flash = 1f
                    }
                    if (e.kill || (e.part == Part.HEAD && e.byPlayer)) {
                        slowUntil = clock + if (e.kill) 1.3f else 0.6f
                    }
                }
                is DuelEvent.Miss -> if (e.ground) burst(e.x, e.y, 14, DUST, 2.5f)
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
        val p = duel.player
        return Duel.preview(p.bowX(), p.bowY(), 1f, aimAngle, aimPower, duel.spec.wind, 0.38f, out)
    }

    companion object {
        /** Visible world width (meters) in portrait. */
        const val VIEW_W = 11f
        val HIT_SPARK = floatArrayOf(1f, 0.35f, 0.45f)
        val HEAD_SPARK = floatArrayOf(1f, 0.85f, 0.3f)
        val DUST = floatArrayOf(0.85f, 0.75f, 0.6f)
        val REVIVE_SPARK = floatArrayOf(0.5f, 1f, 0.7f)
    }
}

/** Simple 2D particles in world space (struct of arrays, no allocations per frame). */
class Particles(val max: Int) {
    val x = FloatArray(max); val y = FloatArray(max); val vx = FloatArray(max); val vy = FloatArray(max)
    val r = FloatArray(max); val g = FloatArray(max); val b = FloatArray(max)
    val size = FloatArray(max); val life = FloatArray(max); val maxLife = FloatArray(max)
    var count = 0
        private set

    fun clear() { count = 0 }

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, cr: Float, cg: Float, cb: Float, s: Float, l: Float) {
        val i = if (count < max) count++ else Random.nextInt(max)
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; r[i] = cr; g[i] = cg; b[i] = cb; size[i] = s; life[i] = l; maxLife[i] = l
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                if (i != last) {
                    x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]; r[i] = r[last]; g[i] = g[last]; b[i] = b[last]
                    size[i] = size[last]; life[i] = life[last]; maxLife[i] = maxLife[last]
                }
                count--
                continue
            }
            vy[i] -= 9f * dt
            vx[i] *= 0.985f
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }
}

