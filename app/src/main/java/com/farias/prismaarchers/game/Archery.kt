package com.farias.prismaarchers.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Pure rules of the archery duel (no Android): ragdoll archers made of Verlet points, arrows with
 * gravity and wind, platforms, hits and the enemy AI. World units are meters, y points up.
 */

const val GRAVITY = 9.8f

/** A platform the archers stand on: from [x0] to [x1], top at [top], [depth] tall. */
class Platform(val x0: Float, val x1: Float, var top: Float, val depth: Float = 30f, val lift: Float = 0f, val boat: Boolean = false) {
    /** Resting height (elevators and boats move around it). */
    val baseTop = top
    fun contains(x: Float, y: Float) = x in x0..x1 && y <= top && y >= top - depth
}

/** Body parts (for damage and for where an arrow sticks). */
enum class Part(val damage: Int) { HEAD(100), TORSO(34), ARM(18), LEG(22) }

/**
 * A stick-figure archer simulated with Verlet points. While alive, every point is pulled towards an
 * animated pose (standing, aiming the bow); hits push points away and the pose pulls them back. When
 * dead, the pose lets go and the body falls freely, bouncing on platforms and off the edges.
 */
class Ragdoll(var rootX: Float, var rootY: Float, val facing: Float) {
    /** Where it stood at the start (a revive puts it back there). */
    var homeX = rootX
        private set
    /** Knockback: the feet slide towards this x. */
    var rootTargetX = rootX
    /** Pain 1 → 0 after a hit that didn't kill: leans back, drops the bow, clutches the wound. */
    var hurt = 0f
        private set
    private var hurtRelX = 0f
    private var hurtRelY = 1.2f
    companion object {
        const val HEAD = 0; const val NECK = 1; const val CHEST = 2; const val PELVIS = 3
        const val L_ELBOW = 4; const val L_HAND = 5; const val R_ELBOW = 6; const val R_HAND = 7
        const val L_KNEE = 8; const val L_FOOT = 9; const val R_KNEE = 10; const val R_FOOT = 11
        const val N = 12
        const val HEAD_R = 0.17f
        /** Seconds the pain reaction lasts. */
        const val HURT_TIME = 0.95f

        /** Bones: (a, b, part, radius). */
        val BONES = arrayOf(
            intArrayOf(NECK, HEAD), intArrayOf(NECK, CHEST), intArrayOf(CHEST, PELVIS),
            intArrayOf(NECK, L_ELBOW), intArrayOf(L_ELBOW, L_HAND), intArrayOf(NECK, R_ELBOW), intArrayOf(R_ELBOW, R_HAND),
            intArrayOf(PELVIS, L_KNEE), intArrayOf(L_KNEE, L_FOOT), intArrayOf(PELVIS, R_KNEE), intArrayOf(R_KNEE, R_FOOT),
        )
        val BONE_PART = arrayOf(Part.HEAD, Part.TORSO, Part.TORSO, Part.ARM, Part.ARM, Part.ARM, Part.ARM, Part.LEG, Part.LEG, Part.LEG, Part.LEG)
        val BONE_R = floatArrayOf(0.06f, 0.13f, 0.14f, 0.06f, 0.055f, 0.06f, 0.055f, 0.08f, 0.07f, 0.08f, 0.07f)
    }

    val x = FloatArray(N); val y = FloatArray(N)
    private val px = FloatArray(N); private val py = FloatArray(N)
    private val poseX = FloatArray(N); private val poseY = FloatArray(N)
    private val len = FloatArray(BONES.size)

    var alive = true
        internal set
    /** Aim angle of the bow arm (radians, 0 = towards [facing], up positive). */
    var aim = 0.25f
    /** 0..1 how far the string is drawn (pose only). */
    var draw = 0f
    /** Seconds since the last hit (for the flinch). */
    var sinceHit = 9f
    private var settledTime = 0f
    /** Seconds alive (breathing, idle sway). */
    var time = 0f
        private set
    /** 1 right after a shot, decays to 0: bow arm kicks, string hand follows through. */
    var recoil = 0f
    /** The winner raises the bow. */
    var cheer = false
    /** How the winner celebrates (0 jump, 1 dance, 2 flex, 3 bow). */
    var cheerStyle = 0
    /** Strongest landing impact since last read (for dust); [thudX]/[thudY] where. */
    var thud = 0f
    var thudX = 0f
    var thudY = 0f
    /** Water/lava surface height (NaN = solid ground): below it the body is slowed and sinks slowly. */
    var waterY = Float.NaN
    /** Strongest entry into the water since last read (for the splash), and where. */
    var splash = 0f
    var splashX = 0f
    /** Seconds the pelvis has been under the surface. */
    var underwater = 0f
        private set

    init {
        buildPose()
        for (i in 0 until N) { x[i] = poseX[i]; y[i] = poseY[i]; px[i] = x[i]; py[i] = y[i] }
        for ((k, b) in BONES.withIndex()) len[k] = dist(b[0], b[1])
    }

    private fun dist(a: Int, b: Int): Float { val dx = x[a] - x[b]; val dy = y[a] - y[b]; return sqrt(dx * dx + dy * dy) }

    /** The standing / aiming pose around the feet at (rootX, rootY). */
    private fun buildPose() {
        val f = facing
        fun set(i: Int, dx: Float, dy: Float) { poseX[i] = rootX + dx * f; poseY[i] = rootY + dy }
        // Breathing and a slow weight shift; a little crouch while drawing hard.
        val breathe = sin(time * 2.3f) * 0.014f
        val sway = sin(time * 0.9f) * 0.012f
        val crouch = 0.05f * draw
        val hop = if (cheer && cheerStyle == 0) abs(sin(time * 7f)) * 0.12f else 0f
        // Pain: an eased curve that peaks right after the hit; the upper body bends back, knees buckle.
        val hp = if (hurt > 0f) easeHurt(hurt) else 0f
        val lean = -0.22f * hp
        val buckle = 0.12f * hp
        set(L_FOOT, -0.17f - 0.12f * hp, hop); set(R_FOOT, 0.2f, hop)
        set(L_KNEE, -0.1f + sway - 0.08f * hp, 0.45f - crouch + hop - buckle * 0.5f); set(R_KNEE, 0.15f + sway + 0.05f * hp, 0.45f - crouch + hop - buckle * 0.6f)
        set(PELVIS, sway - 0.05f * hp, 0.9f - crouch + hop - buckle)
        set(CHEST, 0.02f + sway - 0.03f * draw + lean * 0.6f, 1.24f + breathe - crouch + hop - buckle)
        set(NECK, 0.03f + sway - 0.03f * draw + lean, 1.52f + breathe - crouch + hop - buckle * 1.1f)
        if (cheer) {
            when (cheerStyle) {
                1 -> {
                    // Dance: hips swing, arms wave in turn.
                    val sw = sin(time * 6f) * 0.12f
                    val wv = sin(time * 6f)
                    set(PELVIS, sw, 0.88f); set(CHEST, sw * 0.4f, 1.22f); set(NECK, 0f, 1.5f)
                    set(HEAD, sw * -0.3f, 1.72f)
                    set(R_ELBOW, 0.25f, 1.55f + wv * 0.15f); set(R_HAND, 0.3f, 1.85f + wv * 0.25f)
                    set(L_ELBOW, -0.25f, 1.55f - wv * 0.15f); set(L_HAND, -0.3f, 1.85f - wv * 0.25f)
                    set(L_KNEE, -0.1f + sw, 0.45f); set(R_KNEE, 0.15f + sw, 0.45f)
                }
                2 -> {
                    // Flex: both arms up, showing the muscles.
                    val b = abs(sin(time * 4f)) * 0.05f
                    set(HEAD, 0.04f, 1.75f + b)
                    set(R_ELBOW, 0.35f, 1.5f + b); set(R_HAND, 0.33f, 1.82f + b)
                    set(L_ELBOW, -0.33f, 1.5f + b); set(L_HAND, -0.3f, 1.82f + b)
                }
                3 -> {
                    // A polite bow, then up again.
                    val bend = (sin(time * 2.2f) * 0.5f + 0.5f)
                    set(CHEST, 0.02f + 0.18f * bend, 1.22f - 0.08f * bend)
                    set(NECK, 0.03f + 0.38f * bend, 1.48f - 0.22f * bend)
                    set(HEAD, 0.05f + 0.55f * bend, 1.68f - 0.35f * bend)
                    set(R_ELBOW, 0.12f + 0.2f * bend, 1.2f); set(R_HAND, 0.2f + 0.25f * bend, 1.0f)
                    set(L_ELBOW, -0.12f, 1.2f); set(L_HAND, -0.1f, 0.95f)
                }
                else -> {
                    // Victory: bow raised high, other fist pumping.
                    val pump = sin(time * 7f) * 0.08f
                    set(HEAD, 0.04f + sway, 1.75f + breathe + hop)
                    set(R_ELBOW, 0.18f, 1.78f + hop); set(R_HAND, 0.24f, 2.1f + hop)
                    set(L_ELBOW, -0.2f, 1.72f + hop + pump); set(L_HAND, -0.26f, 2.0f + hop + pump * 1.5f)
                }
            }
            return
        }
        // Hurt: the bow arm drops away from the aim.
        val effAim = aim * (1f - hp) - 0.7f * hp
        val ca = cos(effAim); val sa = sin(effAim)
        // The head leans into the aim, cheek near the string (snaps back when hurt).
        set(HEAD, 0.05f + sway + ca * 0.02f - 0.03f * draw + lean * 1.4f, 1.74f + breathe - crouch + sa * 0.05f - buckle * 1.2f + 0.04f * hp)
        // Front arm holds the bow along the aim (kicks back a bit on release); back arm draws the string.
        val reach = 0.58f - 0.07f * recoil
        set(R_ELBOW, 0.03f + sway + ca * 0.3f, 1.47f - crouch + sa * 0.3f)
        set(R_HAND, 0.03f + sway + ca * reach, 1.47f - crouch + sa * reach)
        val pull = (0.58f - 0.5f * draw - 0.62f * recoil).coerceAtLeast(-0.12f)
        set(L_HAND, 0.03f + sway + ca * pull - sa * 0.05f * recoil, 1.47f - crouch + sa * pull + 0.08f * recoil)
        set(L_ELBOW, 0.03f + sway + ca * pull * 0.5f - sa * 0.18f * draw - 0.12f * (1 - draw), 1.47f - crouch + sa * pull * 0.5f + ca * 0.18f * draw - 0.1f * (1 - draw) + 0.06f * recoil)
        if (hp > 0f) {
            // The free hand goes to the wound.
            val wx = (hurtRelX * facing).coerceIn(-0.3f, 0.3f)
            val wy = hurtRelY.coerceIn(0.5f, 1.6f) - buckle
            val k = (hp * 1.4f).coerceAtMost(1f)
            poseX[L_HAND] += ((rootX + (wx + 0.12f) * facing) - poseX[L_HAND]) * k
            poseY[L_HAND] += ((rootY + wy) - poseY[L_HAND]) * k
            poseX[L_ELBOW] += ((rootX + (wx + 0.28f) * facing) - poseX[L_ELBOW]) * k
            poseY[L_ELBOW] += ((rootY + (wy + 1.4f) / 2f) - poseY[L_ELBOW]) * k
        }
    }

    private fun easeHurt(t: Float): Float {
        // t goes 1 → 0; full pain for the first moments, then a smooth recovery.
        val u = 1f - t
        return if (u < 0.15f) 1f else (1f - (u - 0.15f) / 0.85f).let { it * it * (3f - 2f * it) }
    }

    /**
     * A hit that didn't kill: pain pose and a shove of [knock] meters in the arrow's direction ([dir] = ±1).
     * Enough shoves push the archer off the edge of its platform.
     */
    fun hurtBy(hitX: Float, hitY: Float, dir: Float, knock: Float) {
        hurt = 1f
        hurtRelX = hitX - rootX
        hurtRelY = hitY - rootY
        rootTargetX += dir * knock
    }

    /** Pushes the point nearest to the hit and the ones around it. */
    fun impulse(i: Int, vx: Float, vy: Float) {
        // Verlet velocity = x - px: moving px backwards adds velocity.
        val dt = 1f / 120f
        px[i] -= vx * dt; py[i] -= vy * dt
        sinceHit = 0f
    }

    fun kill(vx: Float, vy: Float, at: Int) {
        if (!alive) return
        alive = false
        settledTime = 0f
        impulse(at, vx, vy)
        // A little spin so every fall looks different.
        impulse(PELVIS, vx * 0.3f, vy * 0.3f)
    }

    /** Puts a standing archer at a new spot instantly (before the duel starts). */
    fun placeAt(x: Float, y: Float) {
        rootX = x; rootY = y
        rootTargetX = x
        homeX = x
        buildPose()
        for (i in 0 until N) { this.x[i] = poseX[i]; this.y[i] = poseY[i]; px[i] = this.x[i]; py[i] = this.y[i] }
    }

    /** Back on its feet at the root (rewarded "continue"). */
    fun revive() {
        alive = true
        cheer = false
        hurt = 0f
        rootX = homeX
        rootTargetX = homeX
        sinceHit = 9f
        buildPose()
        for (i in 0 until N) { x[i] = poseX[i]; y[i] = poseY[i]; px[i] = x[i]; py[i] = y[i] }
    }

    fun step(dt: Float, platforms: List<Platform>) {
        sinceHit += dt
        time += dt
        recoil *= exp(-dt * 7f)
        if (alive) {
            hurt = (hurt - dt / HURT_TIME).coerceAtLeast(0f)
            // Stagger: the feet slide back quickly after a shove.
            rootX += (rootTargetX - rootX) * min(1f, dt * 9f)
            buildPose()
            // Springs back to the pose: soft right after a hit (a flinch), then firm.
            val k = if (sinceHit < 0.25f) 0.08f else 0.35f
            for (i in 0 until N) {
                val vx = (x[i] - px[i]) * 0.86f
                val vy = (y[i] - py[i]) * 0.86f
                px[i] = x[i]; py[i] = y[i]
                x[i] += vx + (poseX[i] - x[i]) * k
                y[i] += vy + (poseY[i] - y[i]) * k
            }
        } else {
            val wy = waterY
            for (i in 0 until N) {
                val under = !wy.isNaN() && y[i] < wy
                // Water: strong drag and a much weaker pull (it sinks slowly, drifting a little).
                val damp = if (under) 0.95f else 0.995f
                val g = if (under) GRAVITY * 0.3f else GRAVITY
                val vx = (x[i] - px[i]) * damp
                val vy = (y[i] - py[i]) * damp
                val before = y[i]
                px[i] = x[i]; py[i] = y[i]
                x[i] += vx + (if (under) sin(time * 1.3f + i) * 0.0004f else 0f)
                y[i] += vy - g * dt * dt
                if (!wy.isNaN() && before >= wy && y[i] < wy && (i == PELVIS || i == CHEST || i == HEAD)) {
                    val sp = before - y[i]
                    if (sp > splash) { splash = sp; splashX = x[i] }
                }
            }
            if (!wy.isNaN() && y[PELVIS] < wy) underwater += dt else underwater = 0f
            repeat(6) { solveBones() }
            collide(platforms)
        }
    }

    private fun solveBones() {
        for ((k, b) in BONES.withIndex()) {
            val a = b[0]; val c = b[1]
            val dx = x[c] - x[a]; val dy = y[c] - y[a]
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
            val diff = (d - len[k]) / d * 0.5f
            x[a] += dx * diff; y[a] += dy * diff
            x[c] -= dx * diff; y[c] -= dy * diff
        }
        // Keep the legs and arms from folding into the body (soft distance limits).
        keepApart(HEAD, PELVIS, 0.7f)
        keepApart(L_FOOT, R_FOOT, 0.15f)
        keepApart(HEAD, CHEST, 0.42f)
    }

    private fun keepApart(a: Int, c: Int, minD: Float) {
        val dx = x[c] - x[a]; val dy = y[c] - y[a]
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
        if (d >= minD) return
        val diff = (d - minD) / d * 0.5f
        x[a] += dx * diff; y[a] += dy * diff
        x[c] -= dx * diff; y[c] -= dy * diff
    }

    private fun collide(platforms: List<Platform>) {
        for (i in 0 until N) {
            val r = if (i == HEAD) HEAD_R else 0.06f
            for (p in platforms) {
                if (x[i] < p.x0 || x[i] > p.x1) continue
                val bottom = y[i] - r
                if (bottom < p.top && py[i] - r >= p.top - 0.3f && y[i] > p.top - p.depth) {
                    // Land on top with friction.
                    val impact = py[i] - y[i]
                    if (impact > thud && (i == PELVIS || i == HEAD || i == CHEST)) { thud = impact; thudX = x[i]; thudY = p.top }
                    y[i] = p.top + r
                    val vx = x[i] - px[i]
                    px[i] = x[i] - vx * 0.6f
                    py[i] = y[i] + (y[i] - py[i]).coerceAtMost(0f) * 0.25f
                }
            }
        }
    }

    /** True when a dead body has stopped moving (or fell far away). */
    fun settled(dt: Float): Boolean {
        var speed = 0f
        for (i in 0 until N) speed = max(speed, abs(x[i] - px[i]) + abs(y[i] - py[i]))
        settledTime = if (speed < 0.004f) settledTime + dt else 0f
        return settledTime > 0.5f || y[PELVIS] < rootY - 25f
    }

    /** Where the bow's arrow starts and the string hand is. */
    fun bowX() = x[R_HAND]
    fun bowY() = y[R_HAND]

    /**
     * Segment/body test for a point moving from (ax, ay) to (bx, by). Returns the bone index hit and the
     * fraction along the motion, or null.
     */
    fun hitTest(ax: Float, ay: Float, bx: Float, by: Float): Pair<Int, Float>? {
        var best: Pair<Int, Float>? = null
        // Head as a circle first (it's what matters most).
        run {
            val t = segCircle(ax, ay, bx, by, x[HEAD], y[HEAD], HEAD_R + 0.02f)
            if (t != null) best = 0 to t
        }
        for ((k, b) in BONES.withIndex()) {
            if (k == 0) continue
            val t = segSeg(ax, ay, bx, by, x[b[0]], y[b[0]], x[b[1]], y[b[1]], BONE_R[k] + 0.03f) ?: continue
            if (best == null || t < best!!.second) best = k to t
        }
        return best
    }
}

/** Fraction along a→b where it enters the circle, or null. */
fun segCircle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, r: Float): Float? {
    val dx = bx - ax; val dy = by - ay
    val fx = ax - cx; val fy = ay - cy
    val a = dx * dx + dy * dy
    if (a < 1e-9f) return null
    val b = 2 * (fx * dx + fy * dy)
    val c = fx * fx + fy * fy - r * r
    val disc = b * b - 4 * a * c
    if (disc < 0) return null
    val s = sqrt(disc)
    val t1 = (-b - s) / (2 * a)
    val t2 = (-b + s) / (2 * a)
    if (t1 in 0f..1f) return t1
    if (c < 0 && t2 >= 0f) return 0f
    return null
}

/** Fraction along a→b where it comes within [r] of segment c→d (sampled), or null. */
fun segSeg(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, r: Float): Float? {
    val steps = 6
    for (s in 0..steps) {
        val t = s / steps.toFloat()
        val px = ax + (bx - ax) * t
        val py = ay + (by - ay) * t
        val ex = dx - cx; val ey = dy - cy
        val l2 = ex * ex + ey * ey
        val u = if (l2 < 1e-9f) 0f else (((px - cx) * ex + (py - cy) * ey) / l2).coerceIn(0f, 1f)
        val qx = cx + ex * u - px; val qy = cy + ey * u - py
        if (qx * qx + qy * qy <= r * r) return t
    }
    return null
}

/** Special arrows (the player picks one before shooting). */
object ArrowKind {
    const val NORMAL = 0
    const val FIRE = 1
    const val TRIPLE = 2
    const val BOMB = 3
    const val ICE = 4
    const val COUNT = 5
}

/** Boss abilities (one per boss, by its look). */
object BossPower {
    const val NONE = 0
    /** Knight: a magic bubble that blocks the first two hits, cracking. */
    const val SHIELD = 1
    /** Samurai: much better aim. */
    const val FOCUS = 2
    /** Viking: shoots two arrows at once. */
    const val DOUBLE = 3
    /** King: heals a bit at the start of each of his turns. */
    const val HEAL = 4
}

/** An arrow in flight or stuck somewhere. */
class Arrow(var x: Float, var y: Float, var vx: Float, var vy: Float, val byPlayer: Boolean, val kind: Int = 0, val dmgMul: Float = 1f) {
    var angle = atan2(vy, vx)
    var flying = true
    var age = 0f
    /** Stuck in a body: which archer, bone and where along it, plus the arrow angle relative to the bone. */
    var stuckIn: Ragdoll? = null
    var bone = 0
    var along = 0.5f
    var relAngle = 0f
    var depth = 0f
    /** Duel time when it stuck (for the quiver), -1 while flying. */
    var stuckTime = -1f
    /** Went through the target (or bounced off a shield): ignores bodies from now on. */
    var pierced = false
    /** Went through this enemy (index): ignores it, can still hit others behind. */
    var passed = -1
    /** Bounced off metal on the way (bank shot). */
    var ricochet = false
    /** Stuck in this platform or obstacle's platform (moves with it), -1 = none. */
    var stuckPlatform = -1
}

/** Something that happened, for sounds, effects and the UI. [foe] = which enemy (index in [Duel.foes]). */
sealed class DuelEvent {
    data class Shot(val byPlayer: Boolean, val kind: Int = 0) : DuelEvent()
    /** [kill] = that archer died; [final] = it decided the duel. */
    data class Hit(
        val byPlayer: Boolean, val part: Part, val damage: Int, val x: Float, val y: Float, val kill: Boolean,
        val kind: Int = 0, val pierce: Boolean = false, val foe: Int = 0, val final: Boolean = kill, val ricochet: Boolean = false,
    ) : DuelEvent()
    data class Miss(val byPlayer: Boolean, val x: Float, val y: Float, val ground: Boolean) : DuelEvent()
    data class TurnStart(val player: Boolean, val foe: Int = 0) : DuelEvent()
    data class Over(val playerWon: Boolean) : DuelEvent()
    data class Explode(val byPlayer: Boolean, val x: Float, val y: Float, val damage: Int, val kill: Boolean, val kills: Int = if (kill) 1 else 0, val final: Boolean = kill, val barrel: Boolean = false) : DuelEvent()
    data class HelmetOff(val x: Float, val y: Float, val vx: Float, val vy: Float, val foe: Int = 0) : DuelEvent()
    data class ShieldHit(val x: Float, val y: Float, val left: Int, val foe: Int = 0) : DuelEvent()
    data class Frozen(val x: Float, val y: Float, val foe: Int = 0) : DuelEvent()
    data class Thaw(val x: Float, val y: Float, val foe: Int = 0) : DuelEvent()
    data class Heal(val amount: Int, val x: Float, val y: Float, val foe: Int = 0) : DuelEvent()
    /** Shoved off the edge of a high platform: the fall kills ([enemy] = an enemy fell). */
    data class Fell(val enemy: Boolean, val x: Float, val y: Float, val foe: Int = 0, val final: Boolean = true) : DuelEvent()
    /** Scenery bonus hit (0 bird, 1 balloon), from [ArcheryGame]. */
    data class Bonus(val kind: Int, val x: Float, val y: Float) : DuelEvent()
    /** An arrow hit an obstacle (wood shield, wall, crate…); [broken] when it broke. */
    data class ObstacleHit(val kind: Int, val x: Float, val y: Float, val broken: Boolean, val byPlayer: Boolean) : DuelEvent()
    data class Ricochet(val x: Float, val y: Float) : DuelEvent()
    /** Target practice: [points] for how close to the centre (10 = bullseye). */
    data class TargetHit(val points: Int, val x: Float, val y: Float) : DuelEvent()
}

/** Kinds of obstacle in the field. */
object ObstacleKind {
    /** Wooden shield in front of an enemy: arrows stick, breaks after a few hits. */
    const val WOOD = 0
    /** Stone wall between the archers: arrows stick, never breaks. */
    const val WALL = 1
    /** Metal sign: arrows bounce off it (bank shots). */
    const val METAL = 2
    /** Explosive barrel next to an enemy. */
    const val BARREL = 3
    /** Wooden crate: breaks and drops coins. */
    const val CRATE = 4
    /** Target practice board (round). */
    const val TARGET = 5
}

/** An obstacle: a box from (x0, y0) to (x1, y1). Attached to a platform it moves with it. */
class Obstacle(val kind: Int, var x0: Float, var y0: Float, var x1: Float, var y1: Float, var hp: Int, val platform: Int = -1) {
    var alive = true
    /** Offsets from the platform top (for obstacles riding a moving platform). */
    var relY0 = 0f
    var relY1 = 0f
    var hitAt = -10f
    val cx get() = (x0 + x1) / 2f
    val cy get() = (y0 + y1) / 2f
}

/** How an obstacle is placed by the level: [anchor] -1 = stands on the ground, -2 = floats at [y], ≥0 = on that enemy's platform. */
class ObstacleSpec(val kind: Int, val x: Float, val w: Float, val h: Float, val anchor: Int, val y: Float = 0f, val hp: Int = 1)

/** One enemy of a level. [h] = height of its platform above the ground; [x] = where it stands. */
class FoeSpec(
    val x: Float,
    val h: Float,
    val hp: Int,
    val skill: Float,
    val helmet: Boolean,
    val power: Int,
    val boss: Boolean,
    /** Elevator: the platform goes up and down this many meters (0 = still). */
    val lift: Float = 0f,
)

object GameMode {
    const val DUEL = 0
    const val VERSUS = 1
    const val TARGETS = 2
}

/** One level: the player's spot, the enemies, obstacles and wind. Legacy fields describe the first enemy. */
class LevelSpec(
    val number: Int,
    val distance: Float,
    val heightDiff: Float,
    val wind: Float,
    val enemyHp: Int,
    val enemyHelmet: Boolean,
    val enemySkill: Float,
    val boss: Boolean,
    val seed: Long,
    val power: Int = BossPower.NONE,
    /** Height of the player's and the (first) enemy's platform above the ground (0 = standing on the ground). */
    val playerH: Float = 9f,
    val enemyH: Float = 9f,
    val foes: List<FoeSpec> = listOf(FoeSpec(distance, enemyH, enemyHp, enemySkill, enemyHelmet, power, boss)),
    val obstacles: List<ObstacleSpec> = emptyList(),
    val mode: Int = GameMode.DUEL,
)

object Levels {
    const val WIND_FROM = 6

    fun isBoss(n: Int) = n >= 10 && n % 10 == 0

    /** Boss ability by boss index (matches the armour in Looks.enemyOutfit: knight, samurai, viking, king). */
    fun bossPower(n: Int): Int = if (!isBoss(n)) BossPower.NONE else when ((n / 10) % 4) {
        0 -> BossPower.SHIELD
        1 -> BossPower.FOCUS
        2 -> BossPower.DOUBLE
        else -> BossPower.HEAL
    }

    /** How many enemies level [n] has: duels alternate with squads (2–3) and hordes (4–5). */
    fun foeCount(n: Int, rnd: Random): Int = when {
        n <= 3 -> 1
        isBoss(n) -> if (n >= 30) 3 else 1
        n % 5 == 4 -> if (n < 14) 3 else 4 + rnd.nextInt(2)
        n % 5 == 2 -> if (n < 10) 2 else 2 + rnd.nextInt(2)
        n % 5 == 0 -> if (n < 10) 2 else 3
        else -> if (n >= 8 && rnd.nextFloat() < 0.35f) 2 else 1
    }

    fun level(n: Int, salt: Long = 0L): LevelSpec {
        val rnd = Random(n * 7_919L + 31 + salt)
        val d = ((n - 1) / 80f).coerceIn(0f, 1f)
        val boss = isBoss(n)
        val power = bossPower(n)
        val distance = 13f + 14f * d + rnd.nextFloat() * (4f + 6f * d)
        rnd.nextFloat()
        val wind = if (n < WIND_FROM) 0f else (rnd.nextFloat() * 2f - 1f) * (0.6f + 3.2f * d)
        var hp = if (boss) 150 + n else 100 + (n / 5) * 5
        if (power == BossPower.SHIELD || power == BossPower.HEAL) hp -= 30 else if (boss) hp -= 25
        // 0 = clumsy, 1 = deadly. Bosses aim better.
        val skill = (0.15f + 0.7f * d + if (boss) 0.05f else 0f).coerceAtMost(0.9f)
        val helmet = boss || n >= 25 && rnd.nextFloat() < 0.25f
        // Where the archers stand: towers of different heights, one of them on the ground, or both low.
        val maxDiff = 5f + 14f * d
        val layout = rnd.nextFloat()
        val playerH: Float
        val enemyH: Float
        when {
            n <= 3 -> { playerH = 7f + rnd.nextFloat() * 2f; enemyH = playerH + (rnd.nextFloat() * 2f - 1f) * 0.8f }
            layout < 0.45f -> {
                playerH = 3f + rnd.nextFloat() * 13f
                val sign = if (rnd.nextBoolean()) 1f else -1f
                enemyH = (playerH + sign * (0.4f + 0.6f * rnd.nextFloat()) * maxDiff).coerceIn(2f, 22f)
            }
            layout < 0.65f -> { playerH = 0f; enemyH = min(5f + rnd.nextFloat() * 15f, maxDiff) }
            layout < 0.85f -> { enemyH = 0f; playerH = min(5f + rnd.nextFloat() * 15f, maxDiff) }
            else -> { playerH = 0f; enemyH = rnd.nextFloat() * min(3f, maxDiff) }
        }
        // Enemies: the first one as above; the others spread further out, at all sorts of heights.
        val count = foeCount(n, rnd)
        val foes = ArrayList<FoeSpec>()
        foes.add(FoeSpec(distance, enemyH, if (count > 1 && !boss) groupHp(n, count) else hp, skill, helmet, power, boss, lift(n, rnd)))
        var x = distance
        // Gaps between enemies, squeezed so the farthest stays within bow range (~55 m, wind included).
        val gaps = FloatArray(count) { if (it == 0) 0f else 5.5f + rnd.nextFloat() * (3.5f + 3f * d) }
        val span = gaps.sum()
        val room = (Duel.MAX_FOE_X - abs(wind) * 2.5f - distance).coerceAtLeast(5f * (count - 1))
        val squeeze = if (span > room) room / span else 1f
        for (i in 1 until count) {
            x += gaps[i] * squeeze
            val h = when {
                rnd.nextFloat() < 0.22f -> 0f
                else -> (playerH + (rnd.nextFloat() * 2f - 1f) * maxDiff).coerceIn(1.5f, 22f)
            }
            val minion = boss
            foes.add(FoeSpec(x, h, if (minion) 45 + n / 3 else groupHp(n, count), skill * (if (minion) 0.6f else 0.7f), n >= 25 && rnd.nextFloat() < 0.2f, BossPower.NONE, false, lift(n, rnd)))
        }
        // Obstacles: wooden shields, explosive barrels, crates, walls and metal signs in between.
        val obs = ArrayList<ObstacleSpec>()
        for ((i, f) in foes.withIndex()) {
            if (n >= 8 && rnd.nextFloat() < 0.3f) obs.add(ObstacleSpec(ObstacleKind.WOOD, f.x - 0.85f, 0.2f, 1.3f, i, hp = 3))
            if (n >= 6 && rnd.nextFloat() < 0.35f) obs.add(ObstacleSpec(ObstacleKind.BARREL, f.x + 1.0f, 0.6f, 0.85f, i))
            else if (n >= 4 && rnd.nextFloat() < 0.3f) obs.add(ObstacleSpec(ObstacleKind.CRATE, f.x + 1.1f, 0.75f, 0.75f, i))
        }
        if (n >= 12 && rnd.nextFloat() < 0.3f) {
            val wx = distance * (0.4f + rnd.nextFloat() * 0.2f)
            val top = max(playerH, enemyH) + 1.0f + rnd.nextFloat() * 2.5f
            obs.add(ObstacleSpec(ObstacleKind.WALL, wx, 1.2f, top, -1))
        } else if (n >= 15 && rnd.nextFloat() < 0.3f) {
            val wx = distance * (0.35f + rnd.nextFloat() * 0.3f)
            val y = (max(0f, enemyH - playerH)) + 4f + rnd.nextFloat() * 4f
            obs.add(ObstacleSpec(ObstacleKind.METAL, wx, 2.2f, 0.3f, -2, y))
        }
        return LevelSpec(n, distance, enemyH - playerH, wind, foes[0].hp, helmet, skill, boss, n * 131L + salt, power, playerH, enemyH, foes, obs)
    }

    /** Group enemies are frailer: a horde's archer falls to one or two good hits. */
    private fun groupHp(n: Int, count: Int): Int =
        if (count >= 4) (28 + n * 0.3f).toInt().coerceAtMost(58) else (42 + n * 0.45f).toInt().coerceAtMost(82)

    /** From level 20 some platforms are elevators. */
    private fun lift(n: Int, rnd: Random): Float = if (n >= 20 && rnd.nextFloat() < 0.25f) 1.5f + rnd.nextFloat() * 2f else 0f

    fun daily(day: Int): LevelSpec = level(30 + day % 40, salt = day * 2_654_435_761L)

    /** Two players on one phone: a single duel, no AI. */
    fun versus(seed: Long): LevelSpec {
        val base = level(12 + (seed % 30).toInt().let { if (it < 0) -it else it }, salt = seed)
        val f = base.foes[0]
        return LevelSpec(base.number, base.distance, base.heightDiff, base.wind * 0.6f, 100, false, 0f, false, base.seed, BossPower.NONE,
            base.playerH, f.h, listOf(FoeSpec(f.x, f.h, 100, 0f, false, BossPower.NONE, false)), base.obstacles.filter { it.anchor < 0 }, GameMode.VERSUS)
    }

    /** Target practice: boards at different distances and heights, no enemies. */
    fun targets(seed: Long): LevelSpec {
        val rnd = Random(seed)
        val obs = ArrayList<ObstacleSpec>()
        var x = 10f
        repeat(6) {
            x += 3f + rnd.nextFloat() * 5f
            val y = -2f + rnd.nextFloat() * 9f
            obs.add(ObstacleSpec(ObstacleKind.TARGET, x, 1.1f, 1.1f, -2, y))
        }
        obs.add(ObstacleSpec(ObstacleKind.CRATE, 12f + rnd.nextFloat() * 12f, 0.75f, 0.75f, -1))
        val wind = (rnd.nextFloat() * 2f - 1f) * 1.5f
        return LevelSpec(1, 20f, 0f, wind, 1, false, 0f, false, seed, BossPower.NONE, 6f, 6f, emptyList(), obs, GameMode.TARGETS)
    }
}

/** One enemy archer in a duel. */
class Foe(val index: Int, val spec: FoeSpec, val body: Ragdoll, val platform: Int) {
    var hp = spec.hp
    val maxHp = spec.hp
    var helmetOn = spec.helmet
    /** Hits the knight boss's bubble can still take. */
    var shield = if (spec.power == BossPower.SHIELD) 2 else 0
    /** Hit by an ice arrow: skips its next turn. */
    var frozen = false
    var shots = 0
    var plan: Pair<Float, Float> = 0.3f to 0.7f
    /** Standing on a boat (bobs on the water). */
    var boat = false
    val alive: Boolean get() = body.alive && hp > 0
}

/**
 * The duel simulation: turns, arrows, hits and the enemy AI. The UI feeds aim/release for the
 * player; [step] advances everything. There can be several enemies: they take turns shooting (one per
 * round). Several arrows can fly at once (triple shot, double-shooting boss): the turn passes when all of
 * them have landed.
 */
class Duel(val spec: LevelSpec, var playerHp: Int = 100, seed: Long = System.nanoTime()) {
    enum class Turn { PLAYER, PLAYER_ARROW, ENEMY, ENEMY_ARROW, OVER }

    var platforms: List<Platform>
        private set
    /** Height of the ground below the towers. */
    var groundY: Float
        private set
    val player: Ragdoll
    val foes: List<Foe>
    val obstacles = ArrayList<Obstacle>()
    /** The enemy shooting now (or the one the camera should show). */
    var activeFoe = 0
        private set
    var maxPlayerHp = playerHp
    var turn = Turn.PLAYER
        private set
    val arrows = ArrayList<Arrow>()
    /** Arrows in the air right now. */
    val inFlight = ArrayList<Arrow>()
    /** The arrow the camera follows. */
    val flying: Arrow? get() = if (inFlight.isEmpty()) null else inFlight[0]
    var time = 0f
        private set
    var playerWon = false
        private set
    private var enemyWait = 0f
    private val rnd = Random(seed)
    var shotsFired = 0
        private set
    /** Player shields left (blocks the next enemy hit). */
    var shields = 0
    /** Over: set when the loser's body stopped falling. */
    var finished = false
        private set
    private var overTime = 0f
    private var killed = false
    private var killByPlayer = false
    /** Two humans: the enemy side waits for [releaseEnemy] instead of the AI. */
    val versus = spec.mode == GameMode.VERSUS
    val practice = spec.mode == GameMode.TARGETS
    /** The last enemy that died (for the camera). */
    var lastFallen = -1
        private set

    // Legacy single-enemy view: the active enemy.
    val enemy: Ragdoll get() = focus().body
    val enemyHp: Int get() = focus().hp
    val maxEnemyHp: Int get() = focus().maxHp
    val helmetOn: Boolean get() = focus().helmetOn
    val bossShield: Int get() = focus().shield
    val enemyFrozen: Boolean get() = focus().frozen
    val aliveFoes: Int get() = foes.count { it.alive }

    /** The enemy to show: the active one if alive, else the last that fell, else any. */
    fun focus(): Foe {
        if (foes.isEmpty()) return DUMMY
        val a = foes[activeFoe.coerceIn(0, foes.size - 1)]
        if (a.alive) return a
        if (lastFallen >= 0) return foes[lastFallen]
        return foes.firstOrNull { it.alive } ?: a
    }

    /**
     * Water or lava below: arrows and bodies go through the ground and sink. An archer "on the ground" then
     * stands on a low rock ([LOW_ROCK] above the surface), or an enemy on a boat. Set right after creating the duel.
     */
    var liquid = false
        set(v) {
            field = v
            buildGround()
        }
    private lateinit var towers: List<Platform>
    private val px0: Float
    private val px1: Float
    private val fx0: FloatArray
    private val fx1: FloatArray

    init {
        val wr = Random(spec.seed * 31 + 7)
        px0 = -(2.2f + wr.nextFloat() * 1.8f)
        px1 = 0.9f + wr.nextFloat() * 0.6f
        fx0 = FloatArray(spec.foes.size)
        fx1 = FloatArray(spec.foes.size)
        val group = spec.foes.size > 1
        for ((i, f) in spec.foes.withIndex()) {
            fx0[i] = f.x - (0.9f + wr.nextFloat() * 0.6f)
            fx1[i] = f.x + (if (group) 1.6f else 2.2f) + wr.nextFloat() * (if (group) 1.2f else 1.8f)
        }
        groundY = -spec.playerH
        platforms = emptyList()
        player = Ragdoll(0f, 0f, 1f)
        player.aim = 0.3f
        foes = spec.foes.mapIndexed { i, f ->
            Foe(i, f, Ragdoll(f.x, f.h - spec.playerH, -1f).also { it.aim = 0.3f }, i + 1)
        }
        buildGround()
        if (versus) maxPlayerHp = 100
    }

    /** Platforms (player, each enemy, ground) and obstacles; the ground is lower over water (see [liquid]). */
    private fun buildGround() {
        val minFoeH = spec.foes.minOfOrNull { it.h } ?: 99f
        groundY = -spec.playerH - if (liquid && spec.playerH < LOW_ROCK) LOW_ROCK - spec.playerH else 0f
        val list = ArrayList<Platform>()
        list.add(Platform(px0, px1, 0f))
        for ((i, f) in spec.foes.withIndex()) {
            val foe = if (i < foes.size) foes[i] else null
            var top = f.h - spec.playerH
            val boat = liquid && f.h < LOW_ROCK
            if (boat) top = groundY + BOAT_DECK
            if (!liquid && f.h <= 0f) top = groundY
            val p = Platform(fx0[i], fx1[i], top, lift = f.lift, boat = boat)
            list.add(p)
            if (foe != null) {
                foe.boat = boat
                if (foe.body.rootY != top) foe.body.placeAt(f.x, top)
            }
        }
        list.add(Platform(-80f, (spec.foes.maxOfOrNull { it.x } ?: spec.distance) + 80f, groundY, 60f))
        platforms = list
        towers = list.subList(0, list.size - 1)
        if (minFoeH < -1f) Unit
        buildObstacles()
    }

    private fun buildObstacles() {
        obstacles.clear()
        for (o in spec.obstacles) {
            val half = o.w / 2f
            when {
                o.anchor >= 0 && o.anchor < foes.size -> {
                    val p = platforms[o.anchor + 1]
                    // Keep it on the platform
                    val cx = o.x.coerceIn(p.x0 + half, p.x1 - half)
                    val ob = Obstacle(o.kind, cx - half, p.top, cx + half, p.top + o.h, o.hp, o.anchor + 1)
                    ob.relY0 = 0f; ob.relY1 = o.h
                    obstacles.add(ob)
                }
                o.anchor == -1 -> obstacles.add(Obstacle(o.kind, o.x - half, groundY, o.x + half, groundY + o.h - (if (o.kind == ObstacleKind.WALL) groundY + spec.playerH else 0f), o.hp))
                else -> obstacles.add(Obstacle(o.kind, o.x - half, o.y - o.h / 2f, o.x + half, o.y + o.h / 2f, o.hp))
            }
        }
    }

    // ------------------------------------------------------------------ input

    /** Aim while dragging: [angle] radians (up positive, towards the enemy), [power] 0..1. */
    fun aimPlayer(angle: Float, power: Float) {
        if (turn != Turn.PLAYER) return
        player.aim = angle.coerceIn(-1.2f, 1.45f)
        player.draw = power.coerceIn(0f, 1f)
    }

    /** Returns true when the shot went off. */
    fun releasePlayer(angle: Float, power: Float, events: MutableList<DuelEvent>, kind: Int = 0): Boolean {
        if (turn != Turn.PLAYER || power < 0.08f) {
            player.draw = 0f
            return false
        }
        player.aim = angle.coerceIn(-1.2f, 1.45f)
        fire(player, player.aim, power, true, kind, events)
        player.draw = 0f
        shotsFired++
        turn = Turn.PLAYER_ARROW
        return true
    }

    /** Two-player mode: the second human aims the enemy (angle measured towards the player, up positive). */
    fun aimEnemy(angle: Float, power: Float) {
        if (!versus || turn != Turn.ENEMY) return
        val f = focus()
        f.body.aim = angle.coerceIn(-1.2f, 1.45f)
        f.body.draw = power.coerceIn(0f, 1f)
    }

    fun releaseEnemy(angle: Float, power: Float, events: MutableList<DuelEvent>, kind: Int = 0): Boolean {
        if (!versus || turn != Turn.ENEMY) return false
        val f = focus()
        if (power < 0.08f) { f.body.draw = 0f; return false }
        f.body.aim = angle.coerceIn(-1.2f, 1.45f)
        fire(f.body, f.body.aim, power, false, kind, events)
        f.body.draw = 0f
        f.shots++
        turn = Turn.ENEMY_ARROW
        return true
    }

    private fun fire(r: Ragdoll, aim: Float, power: Float, byPlayer: Boolean, kind: Int, events: MutableList<DuelEvent>) {
        if (kind == ArrowKind.TRIPLE) {
            for (k in -1..1) shoot(r, aim + k * 0.055f, power, byPlayer, kind, 0.6f, events, k == 0)
        } else {
            shoot(r, aim, power, byPlayer, kind, if (kind == ArrowKind.FIRE) 1.5f else if (kind == ArrowKind.ICE) 0.8f else 1f, events, true)
        }
    }

    private fun shoot(r: Ragdoll, aim: Float, power: Float, byPlayer: Boolean, kind: Int, mul: Float, events: MutableList<DuelEvent>, announce: Boolean) {
        val speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power.coerceIn(0f, 1f)
        val dir = r.facing
        val a = Arrow(r.bowX(), r.bowY(), cos(aim) * speed * dir, sin(aim) * speed, byPlayer, kind, mul)
        r.recoil = 1f
        arrows.add(a)
        if (arrows.size > 34) {
            val old = arrows.firstOrNull { !it.flying }
            if (old != null) arrows.remove(old)
        }
        inFlight.add(a)
        if (announce) events.add(DuelEvent.Shot(byPlayer, kind))
    }

    /** Rewarded "continue" after the player died: back up with [hp] and it's the player's turn. */
    fun revivePlayer(hp: Int) {
        if (turn != Turn.OVER || playerWon) return
        playerHp = hp
        player.revive()
        for (f in foes) f.body.cheer = false
        arrows.removeAll { it.stuckIn === player }
        inFlight.clear()
        killed = false
        finished = false
        turn = Turn.PLAYER
    }

    // ------------------------------------------------------------------ simulation

    fun step(dt: Float, events: MutableList<DuelEvent>) {
        time += dt
        movePlatforms()
        val solid = if (liquid) towers else platforms
        val wy = if (liquid) groundY else Float.NaN
        player.waterY = wy
        player.step(dt, solid)
        for (f in foes) {
            f.body.waterY = wy
            f.body.step(dt, solid)
        }
        checkFalls(events)
        var i = inFlight.size - 1
        while (i >= 0) {
            if (i < inFlight.size) stepArrow(inFlight[i], dt, events)
            i--
        }
        when (turn) {
            Turn.ENEMY -> if (!versus && foes.isNotEmpty()) {
                val f = foes[activeFoe]
                val e = f.body
                enemyWait += dt
                e.draw = (enemyWait / 0.9f).coerceIn(0f, 1f)
                val (ang, pow) = f.plan
                e.aim = e.aim + (ang - e.aim) * min(1f, dt * 4f)
                if (enemyWait > 1.1f) {
                    if (f.spec.power == BossPower.DOUBLE) {
                        shoot(e, ang - 0.03f, pow, false, 0, 0.6f, events, true)
                        shoot(e, ang + 0.03f, pow, false, 0, 0.6f, events, false)
                    } else {
                        // In a group every archer hits softer (they take turns, so it adds up).
                        shoot(e, ang, pow, false, 0, if (foes.size > 1 && !f.spec.boss) GROUP_DAMAGE else 1f, events, true)
                    }
                    e.draw = 0f
                    f.shots++
                    turn = Turn.ENEMY_ARROW
                }
            }
            Turn.OVER -> {
                overTime += dt
                val loser = if (playerWon) (if (lastFallen >= 0) foes[lastFallen].body else enemy) else player
                // A body that fell into the water: let it splash and start sinking before the panel.
                val done = if (liquid && loser.underwater > 0f) loser.underwater > 1.6f
                else loser.settled(dt) || overTime > (if (liquid && loser.y[Ragdoll.PELVIS] > groundY) 3.5f else 2.2f)
                if (!finished && done) {
                    finished = true
                    events.add(DuelEvent.Over(playerWon))
                }
            }
            else -> Unit
        }
        // Arrows stuck in bodies follow them; arrows stuck in moving obstacles too.
        for (ar in arrows) {
            val body = ar.stuckIn ?: continue
            val b = Ragdoll.BONES[ar.bone]
            val x0 = body.x[b[0]]; val y0 = body.y[b[0]]; val x1 = body.x[b[1]]; val y1 = body.y[b[1]]
            val boneAng = atan2(y1 - y0, x1 - x0)
            ar.angle = boneAng + ar.relAngle
            val cx = x0 + (x1 - x0) * ar.along
            val cy = y0 + (y1 - y0) * ar.along
            ar.x = cx + cos(ar.angle) * ar.depth
            ar.y = cy + sin(ar.angle) * ar.depth
        }
    }

    /** Elevators go up and down, boats bob; whoever stands there (and obstacles on them) follow. */
    private fun movePlatforms() {
        for ((q, p) in platforms.withIndex()) {
            if (p.lift <= 0f && !p.boat) continue
            val old = p.top
            p.top = p.baseTop + if (p.boat) sin(time * 1.7f + q) * 0.12f else (sin(time * 0.9f + q * 1.7f) * 0.5f + 0.5f) * p.lift
            val dy = p.top - old
            if (dy == 0f) continue
            val foe = foes.getOrNull(q - 1)
            if (foe != null && foe.body.alive) foe.body.rootY = p.top
            for (o in obstacles) if (o.platform == q) { o.y0 = p.top + o.relY0; o.y1 = p.top + o.relY1 }
            for (a in arrows) if (!a.flying && a.stuckIn == null && a.stuckPlatform == q) a.y += dy
        }
    }

    /** Damage an arrow does on [part] of [foe] (null = the player), before shields. */
    fun damageFor(a: Arrow, part: Part, foe: Foe?): Int {
        val helmet = foe?.helmetOn ?: false
        val base = when {
            part == Part.HEAD && foe != null && helmet -> 50
            part == Part.HEAD && foe == null -> PLAYER_HEAD_DAMAGE
            else -> part.damage
        }
        // A clean headshot always kills an enemy, whatever the arrow.
        if (part == Part.HEAD && foe != null && !helmet && !versus) return 100
        if (part == Part.HEAD && versus) return (60 * a.dmgMul).roundToInt()
        return (base * a.dmgMul).roundToInt()
    }

    private fun stepArrow(a: Arrow, dt: Float, events: MutableList<DuelEvent>) {
        a.age += dt
        val ox = a.x; val oy = a.y
        a.vx += spec.wind * dt
        a.vy -= GRAVITY * dt
        a.x += a.vx * dt
        a.y += a.vy * dt
        a.angle = atan2(a.vy, a.vx)
        val tipX = a.x + cos(a.angle) * 0.35f
        val tipY = a.y + sin(a.angle) * 0.35f
        val oTipX = ox + cos(a.angle) * 0.35f
        val oTipY = oy + sin(a.angle) * 0.35f
        // Obstacles first (they stand in front of the archers)
        if (hitObstacles(a, oTipX, oTipY, tipX, tipY, events)) return
        if (a.byPlayer) {
            // The knight's bubble stops player arrows first.
            for (f in foes) {
                if (f.shield <= 0 || a.pierced || !f.alive) continue
                val t = segCircle(oTipX, oTipY, tipX, tipY, f.body.x[Ragdoll.CHEST], f.body.y[Ragdoll.CHEST] + 0.1f, SHIELD_R) ?: continue
                f.shield--
                val hx = oTipX + (tipX - oTipX) * t
                val hy = oTipY + (tipY - oTipY) * t
                events.add(DuelEvent.ShieldHit(hx, hy, f.shield, f.index))
                if (a.kind == ArrowKind.BOMB) {
                    events.add(DuelEvent.Explode(true, hx, hy, 0, false))
                    a.flying = false
                    arrows.remove(a)
                    land(a, events)
                    return
                }
                a.pierced = true
                a.vx = -a.vx * 0.25f
                a.vy = 2.5f
                return
            }
            // The closest enemy body along the segment
            var best: Foe? = null
            var bestHit: Pair<Int, Float>? = null
            if (!a.pierced) for (f in foes) {
                if (!f.body.alive && a.age > 6f) continue
                if (a.passed == f.index) continue
                val h = f.body.hitTest(oTipX, oTipY, tipX, tipY) ?: continue
                if (bestHit == null || h.second < bestHit.second) { bestHit = h; best = f }
            }
            if (best != null && bestHit != null) {
                bodyHit(a, best.body, best, bestHit.first, bestHit.second, oTipX, oTipY, tipX, tipY, events)
                return
            }
        } else if (!a.pierced) {
            val h = player.hitTest(oTipX, oTipY, tipX, tipY)
            if (h != null && (player.alive || a.age < 6f)) {
                bodyHit(a, player, null, h.first, h.second, oTipX, oTipY, tipX, tipY, events)
                return
            }
        }
        // Platform or out of the world
        for ((q, p) in platforms.withIndex()) {
            if (p.contains(tipX, tipY)) {
                if (liquid && q == platforms.size - 1) {
                    // Into the water: it sinks (the splash is drawn by the game).
                    a.flying = false
                    arrows.remove(a)
                    events.add(DuelEvent.Miss(a.byPlayer, tipX, groundY, true))
                    land(a, events)
                    return
                }
                if (a.kind == ArrowKind.BOMB) {
                    blast(a, tipX, tipY, events, direct = null, barrel = false)
                    return
                }
                a.flying = false
                a.stuckTime = time
                a.stuckPlatform = q
                events.add(DuelEvent.Miss(a.byPlayer, tipX, tipY, true))
                land(a, events)
                return
            }
        }
        val far = (foes.maxOfOrNull { it.spec.x } ?: spec.distance)
        if (a.y < groundY - 40f || a.x < -60f || a.x > far + 60f) {
            a.flying = false
            events.add(DuelEvent.Miss(a.byPlayer, a.x, a.y, false))
            land(a, events)
        }
    }

    /** True when the arrow was stopped (or redirected) by an obstacle this step. */
    private fun hitObstacles(a: Arrow, ax: Float, ay: Float, bx: Float, by: Float, events: MutableList<DuelEvent>): Boolean {
        for (o in obstacles) {
            if (!o.alive) continue
            val t: Float = if (o.kind == ObstacleKind.TARGET) {
                segCircle(ax, ay, bx, by, o.cx, o.cy, (o.x1 - o.x0) / 2f) ?: continue
            } else {
                segRect(ax, ay, bx, by, o.x0, o.y0, o.x1, o.y1) ?: continue
            }
            val hx = ax + (bx - ax) * t
            val hy = ay + (by - ay) * t
            o.hitAt = time
            when (o.kind) {
                ObstacleKind.METAL -> {
                    // Bounce: off the long faces vertically, off the ends horizontally.
                    if (hx <= o.x0 + 0.05f || hx >= o.x1 - 0.05f) a.vx = -a.vx * 0.8f else a.vy = -a.vy * 0.8f
                    a.x += a.vx * 0.02f; a.y += a.vy * 0.02f
                    a.ricochet = true
                    events.add(DuelEvent.Ricochet(hx, hy))
                    return true
                }
                ObstacleKind.BARREL -> {
                    o.alive = false
                    events.add(DuelEvent.ObstacleHit(o.kind, o.cx, o.cy, true, a.byPlayer))
                    blast(a, o.cx, o.cy, events, direct = null, barrel = true)
                    return true
                }
                ObstacleKind.CRATE -> {
                    // Smashed: the arrow flies on, slowed.
                    o.alive = false
                    a.vx *= 0.7f; a.vy *= 0.7f
                    events.add(DuelEvent.ObstacleHit(o.kind, o.cx, o.cy, true, a.byPlayer))
                    return false
                }
                ObstacleKind.TARGET -> {
                    val r = (o.x1 - o.x0) / 2f
                    val dist = sqrt((hx - o.cx) * (hx - o.cx) + (hy - o.cy) * (hy - o.cy)) / r
                    val pts = when {
                        dist < 0.2f -> 10
                        dist < 0.45f -> 7
                        dist < 0.7f -> 5
                        else -> 2
                    }
                    o.alive = false
                    events.add(DuelEvent.TargetHit(pts, hx, hy))
                    a.flying = false
                    a.stuckTime = time
                    a.x = hx - cos(a.angle) * 0.3f; a.y = hy - sin(a.angle) * 0.3f
                    land(a, events)
                    return true
                }
                else -> {
                    // Wood or stone: the arrow sticks; wood breaks after a few.
                    a.flying = false
                    a.stuckTime = time
                    a.stuckPlatform = o.platform
                    a.x = hx - cos(a.angle) * 0.28f; a.y = hy - sin(a.angle) * 0.28f
                    var broken = false
                    if (o.kind == ObstacleKind.WOOD) {
                        o.hp -= if (a.kind == ArrowKind.FIRE || a.kind == ArrowKind.BOMB) 3 else 1
                        if (o.hp <= 0) {
                            o.alive = false
                            broken = true
                            // Arrows stuck in it fall
                            arrows.removeAll { !it.flying && it.stuckIn == null && it.x in o.x0 - 0.5f..o.x1 + 0.5f && it.y in o.y0..o.y1 + 0.3f }
                        }
                    }
                    events.add(DuelEvent.ObstacleHit(o.kind, hx, hy, broken, a.byPlayer))
                    if (a.kind == ArrowKind.BOMB) { blast(a, hx, hy, events, direct = null, barrel = false); return true }
                    land(a, events)
                    return true
                }
            }
        }
        return false
    }

    private fun bodyHit(a: Arrow, target: Ragdoll, foe: Foe?, bone: Int, t: Float, ax: Float, ay: Float, bx: Float, by: Float, events: MutableList<DuelEvent>) {
        val hx = ax + (bx - ax) * t
        val hy = ay + (by - ay) * t
        val part = Ragdoll.BONE_PART[bone]
        val wasAlive = target.alive
        var dmg = if (wasAlive) damageFor(a, part, foe) else 0
        if (foe == null && shields > 0 && dmg > 0) {
            shields--
            dmg = 0
        }
        if (part == Part.HEAD && foe != null && foe.helmetOn) {
            foe.helmetOn = false
            events.add(DuelEvent.HelmetOff(target.x[Ragdoll.HEAD], target.y[Ragdoll.HEAD] + 0.1f, a.vx * 0.25f, 4f, foe.index))
        }
        if (a.kind == ArrowKind.BOMB) dmg += if (wasAlive) 20 else 0
        val speed = sqrt(a.vx * a.vx + a.vy * a.vy)
        val near = if (bone == 0) Ragdoll.HEAD else Ragdoll.BONES[bone][1]
        target.impulse(near, a.vx / speed * 3.5f, a.vy / speed * 3.5f)
        val died = wasAlive && applyDamage(foe, dmg)
        val final = died && killed
        // A fast killing arrow goes right through and keeps flying.
        val pierce = died && speed > PIERCE_SPEED && part != Part.ARM && a.kind != ArrowKind.BOMB
        if (died) target.kill(a.vx * (if (pierce) 1.2f else 0.9f), a.vy * 0.9f + 2f, near)
        else if (wasAlive && target.alive) target.hurtBy(hx, hy, if (a.vx >= 0f) 1f else -1f, knockFor(part, a) * (if (dmg == 0) 0.4f else 1f))
        events.add(DuelEvent.Hit(a.byPlayer, part, dmg, hx, hy, died, a.kind, pierce, foe?.index ?: -1, final, a.ricochet))
        if (a.kind == ArrowKind.ICE && foe != null && !died && wasAlive) {
            foe.frozen = true
            events.add(DuelEvent.Frozen(target.x[Ragdoll.CHEST], target.y[Ragdoll.CHEST], foe.index))
        }
        if (a.kind == ArrowKind.BOMB) {
            blast(a, hx, hy, events, direct = foe, barrel = false)
            return
        }
        if (pierce) {
            a.pierced = foe == null
            a.passed = foe?.index ?: -1
            a.vx *= 0.6f
            a.vy *= 0.6f
            return
        }
        stick(a, target, bone, hx, hy)
        land(a, events)
    }

    /** How far a non-lethal hit shoves the archer back (meters). */
    private fun knockFor(part: Part, a: Arrow): Float {
        val base = when (part) {
            Part.HEAD -> 0.55f
            Part.TORSO -> 0.42f
            Part.LEG -> 0.3f
            Part.ARM -> 0.22f
        }
        val speed = sqrt(a.vx * a.vx + a.vy * a.vy)
        return base * (0.6f + 0.4f * (speed / MAX_SPEED)) * (if (a.kind == ArrowKind.FIRE) 1.2f else 1f) * (if (a.byPlayer) 1f else 0.65f)
    }

    /** An archer shoved past the edge of a high platform falls and dies. */
    private fun checkFalls(events: MutableList<DuelEvent>) {
        if (killed) return
        for (q in 0..foes.size) {
            val foe = if (q == 0) null else foes[q - 1]
            val r = foe?.body ?: player
            if (!r.alive) continue
            val p = platforms[q]
            val high = liquid || p.top - groundY > 0.5f
            if (!high) continue
            val out = r.rootX < p.x0 + 0.02f || r.rootX > p.x1 - 0.02f
            if (!out) continue
            val dir = if (r.rootX < (p.x0 + p.x1) / 2f) -1f else 1f
            if (foe == null) playerHp = 0 else foe.hp = 0
            r.kill(dir * 2.5f, 1.5f, Ragdoll.PELVIS)
            val final = settleKill(foe)
            events.add(DuelEvent.Fell(foe != null, r.x[Ragdoll.PELVIS], r.y[Ragdoll.PELVIS], foe?.index ?: -1, final))
            if (final && inFlight.isEmpty() && turn != Turn.OVER) endDuel()
            if (killed) return
        }
    }

    /** After an archer died: is the duel decided? */
    private fun settleKill(foe: Foe?): Boolean {
        if (foe == null) {
            killed = true
            killByPlayer = false
        } else {
            lastFallen = foe.index
            if (foes.none { it.alive }) {
                killed = true
                killByPlayer = true
            }
        }
        return killed
    }

    private fun endDuel() {
        playerWon = killByPlayer
        if (killByPlayer) player.cheer = true else foes.firstOrNull { it.alive }?.body?.cheer = true
        turn = Turn.OVER
        overTime = 0f
    }

    /** Takes [dmg] off [foe] (null = the player); true when that archer died. */
    private fun applyDamage(foe: Foe?, dmg: Int): Boolean {
        if (killed) return false
        if (foe == null) {
            playerHp = (playerHp - dmg).coerceAtLeast(0)
            if (playerHp == 0) { settleKill(null); return true }
        } else {
            if (foe.hp <= 0) return false
            foe.hp = (foe.hp - dmg).coerceAtLeast(0)
            if (foe.hp == 0) { settleKill(foe); return true }
        }
        return false
    }

    /**
     * Explosion (bomb arrow or barrel): area damage to everyone in range on the other side (except the one hit
     * directly, [direct]) and a shove away from the blast. A barrel hurts whoever is near, enemy or player.
     */
    private fun blast(a: Arrow, x: Float, y: Float, events: MutableList<DuelEvent>, direct: Foe?, barrel: Boolean) {
        a.flying = false
        arrows.remove(a)
        var total = 0
        var kills = 0
        var anyKill = false
        val victims = ArrayList<Pair<Ragdoll, Foe?>>()
        if (a.byPlayer || barrel) for (f in foes) victims.add(f.body to f)
        if (!a.byPlayer || barrel) victims.add(player to null)
        for ((body, foe) in victims) {
            if (!body.alive) continue
            val dx = body.x[Ragdoll.CHEST] - x
            val dy = body.y[Ragdoll.CHEST] - y
            val d = sqrt(dx * dx + dy * dy)
            if (d > BLAST_R * 1.3f) continue
            var died = false
            if (!(direct != null && foe === direct) && d < BLAST_R) {
                val dmg = (BLAST_DAMAGE * (1f - d / BLAST_R) * (if (barrel) 1.2f else 1f)).roundToInt().coerceAtLeast(5)
                total += dmg
                if (foe == null && shields > 0) shields-- else died = applyDamage(foe, dmg)
            }
            val push = 9f * (1f - d / (BLAST_R * 1.3f))
            val nx = if (d > 1e-3f) dx / d else 0f
            val ny = if (d > 1e-3f) dy / d else 1f
            if (died) {
                body.kill(nx * push * 1.5f, ny * push + 5f, Ragdoll.CHEST)
                kills++
                anyKill = true
            } else if (body.alive) {
                for (p in 0 until Ragdoll.N) body.impulse(p, nx * push * 0.4f, ny * push * 0.4f + 1f)
                body.hurtBy(x, y, if (nx >= 0f) 1f else -1f, 0.35f + push * 0.06f)
            }
        }
        // Crates and wood nearby break too, other barrels go off later in a chain (next step).
        for (o in obstacles) {
            if (!o.alive || (o.kind != ObstacleKind.CRATE && o.kind != ObstacleKind.WOOD)) continue
            val dx = o.cx - x; val dy = o.cy - y
            if (dx * dx + dy * dy < BLAST_R * BLAST_R) {
                o.alive = false
                events.add(DuelEvent.ObstacleHit(o.kind, o.cx, o.cy, true, a.byPlayer))
            }
        }
        events.add(DuelEvent.Explode(a.byPlayer, x, y, total, anyKill, kills, anyKill && killed, barrel))
        land(a, events)
    }

    private fun stick(a: Arrow, body: Ragdoll, bone: Int, hx: Float, hy: Float) {
        a.flying = false
        a.stuckTime = time
        a.stuckIn = body
        a.bone = bone
        val b = Ragdoll.BONES[bone]
        val x0 = body.x[b[0]]; val y0 = body.y[b[0]]; val x1 = body.x[b[1]]; val y1 = body.y[b[1]]
        val ex = x1 - x0; val ey = y1 - y0
        val l2 = (ex * ex + ey * ey).coerceAtLeast(1e-6f)
        a.along = (((hx - x0) * ex + (hy - y0) * ey) / l2).coerceIn(0f, 1f)
        val boneAng = atan2(ey, ex)
        a.relAngle = a.angle - boneAng
        a.depth = -0.22f
    }

    /** An arrow finished its flight; when none is left in the air the turn resolves. */
    private fun land(a: Arrow, events: MutableList<DuelEvent>) {
        inFlight.remove(a)
        if (inFlight.isNotEmpty() || turn == Turn.OVER) return
        if (killed) {
            endDuel()
            return
        }
        if (practice) {
            turn = Turn.PLAYER
            events.add(DuelEvent.TurnStart(true))
            return
        }
        if (a.byPlayer) nextEnemyTurn(events)
        else {
            turn = Turn.PLAYER
            events.add(DuelEvent.TurnStart(true))
        }
    }

    /** The next living enemy (round robin) shoots; frozen ones lose their turn. */
    private fun nextEnemyTurn(events: MutableList<DuelEvent>) {
        val n = foes.size
        var idx = activeFoe
        for (k in 1..n) {
            val i = (activeFoe + k) % n
            val f = foes[i]
            if (!f.alive) continue
            if (f.frozen) {
                f.frozen = false
                events.add(DuelEvent.Thaw(f.body.x[Ragdoll.CHEST], f.body.y[Ragdoll.CHEST], i))
                // Single enemy frozen: straight back to the player.
                if (foes.count { it.alive } == 1) {
                    activeFoe = i
                    turn = Turn.PLAYER
                    events.add(DuelEvent.TurnStart(true))
                    return
                }
                continue
            }
            idx = i
            break
        }
        val f = foes[idx]
        if (!f.alive) {
            turn = Turn.PLAYER
            events.add(DuelEvent.TurnStart(true))
            return
        }
        activeFoe = idx
        turn = Turn.ENEMY
        enemyWait = 0f
        if (f.spec.power == BossPower.HEAL && f.hp < f.maxHp) {
            val heal = min(HEAL_AMOUNT, f.maxHp - f.hp)
            f.hp += heal
            events.add(DuelEvent.Heal(heal, f.body.x[Ragdoll.HEAD], f.body.y[Ragdoll.HEAD] + 0.4f, idx))
        }
        if (!versus) f.plan = planShot(f)
        events.add(DuelEvent.TurnStart(false, idx))
    }

    /**
     * Would this arrow land the duel-deciding blow within [seconds]? Used for the kill cam (bodies are taken
     * as they are now).
     */
    fun predictKill(a: Arrow, seconds: Float): Boolean {
        if (a.pierced || killed || practice) return false
        val targets: List<Pair<Ragdoll, Foe?>> = if (a.byPlayer) {
            if (aliveFoes != 1) return false
            val f = foes.first { it.alive }
            if (f.shield > 0) return false
            listOf(f.body to f)
        } else listOf(player to null)
        var x = a.x; var y = a.y; var vx = a.vx; var vy = a.vy
        val dt = 1f / 60f
        var t = 0f
        while (t < seconds) {
            val ox = x; val oy = y
            vx += spec.wind * dt; vy -= GRAVITY * dt
            x += vx * dt; y += vy * dt
            val ang = atan2(vy, vx)
            val c = cos(ang) * 0.35f; val s = sin(ang) * 0.35f
            for (o in obstacles) if (o.alive && o.kind != ObstacleKind.CRATE && segRect(ox + c, oy + s, x + c, y + s, o.x0, o.y0, o.x1, o.y1) != null) return false
            for ((body, foe) in targets) {
                if (!body.alive) continue
                val hit = body.hitTest(ox + c, oy + s, x + c, y + s) ?: continue
                val part = Ragdoll.BONE_PART[hit.first]
                var dmg = damageFor(a, part, foe)
                if (a.kind == ArrowKind.BOMB) dmg += 20
                if (foe == null && shields > 0) dmg = 0
                return dmg >= (foe?.hp ?: playerHp)
            }
            for (p in platforms) if (p.contains(x + c, y + s)) return false
            t += dt
        }
        return false
    }

    // ------------------------------------------------------------------ enemy AI

    /**
     * Finds an angle/power that would hit the player's chest (wind included), then adds an error that
     * shrinks with skill and with every shot (the enemy "learns"). If a wall is in the way, lobs over it.
     */
    private fun planShot(f: Foe): Pair<Float, Float> {
        val learn = (1f - f.shots * ENEMY_LEARN).coerceAtLeast(ENEMY_LEARN_FLOOR)
        val focusMul = if (f.spec.power == BossPower.FOCUS) 0.8f else 1f
        // In a group each enemy aims worse (there are several of them shooting in turns).
        val groupMul = if (foes.size > 1) 1.45f + 0.12f * (foes.size - 2) else 1f
        val sigma = (ENEMY_ERR_MAX + (ENEMY_ERR_MIN - ENEMY_ERR_MAX) * f.spec.skill) * learn * focusMul * groupMul
        val tx = player.x[Ragdoll.CHEST] + gauss() * 0.3f
        val ty = player.y[Ragdoll.CHEST] - 0.15f + gauss() * sigma
        val e = f.body
        return aimAround(e.bowX(), e.bowY(), -1f, tx, ty, player.rootX)
    }

    /** The best shot at (tx, ty) that clears the obstacles in between: flat if possible, else higher and higher lobs. */
    fun aimAround(sx: Float, sy: Float, dir: Float, tx: Float, ty: Float, untilX: Float): Pair<Float, Float> {
        val first = solveShot(sx, sy, dir, tx, ty, spec.wind)
        if (!blocked(sx, sy, dir, first.first, first.second, untilX)) return first
        for (m in LOB_ANGLES) {
            val s = solveShot(sx, sy, dir, tx, ty, spec.wind, minAngle = m)
            if (!blocked(sx, sy, dir, s.first, s.second, untilX)) return s
        }
        return first
    }

    /** Does this shot run into a solid obstacle before reaching [untilX]? (Also used by the test bot.) */
    fun blocked(sx: Float, sy: Float, dir: Float, aim: Float, power: Float, untilX: Float): Boolean {
        val speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power
        var x = sx; var y = sy
        var vx = cos(aim) * speed * dir; var vy = sin(aim) * speed
        val dt = 1f / 60f
        var t = 0f
        while (t < 5f) {
            val ox = x; val oy = y
            vx += spec.wind * dt; vy -= GRAVITY * dt
            x += vx * dt; y += vy * dt
            for (o in obstacles) {
                if (!o.alive || o.kind == ObstacleKind.CRATE || o.kind == ObstacleKind.BARREL) continue
                if (dir < 0f && o.platform > 0) continue // its own shield/props
                if (o.kind == ObstacleKind.TARGET) continue
                if (segRect(ox, oy, x, y, o.x0, o.y0, o.x1, o.y1) != null) return true
            }
            if ((x - untilX) * dir > 0f) return false
            // Towers standing in between (not the shooter's nor the target's).
            if (abs(x - sx) > 2.2f && (untilX - x) * dir > 2.2f) {
                for (q in 0 until platforms.size - 1) if (platforms[q].contains(x, y)) return true
            }
            t += dt
        }
        return false
    }

    private fun gauss(): Float {
        var s = 0f
        repeat(4) { s += rnd.nextFloat() }
        return (s - 2f) * 1.732f
    }

    companion object {
        private val LOB_ANGLES = floatArrayOf(0.6f, 0.8f, 0.95f, 1.1f, 1.22f)
        /** Enemy miss spread (meters, 1 sd) for skill 0 and skill 1. */
        var ENEMY_ERR_MAX = 2.6f
        var ENEMY_ERR_MIN = 1.3f
        var ENEMY_LEARN = 0.07f
        var ENEMY_LEARN_FLOOR = 0.75f
        /** Damage the player takes on a headshot (not an instant kill: that would feel unfair). */
        var PLAYER_HEAD_DAMAGE = 50
        const val MIN_SPEED = 9f
        const val PIERCE_SPEED = 20f
        const val GROUND_DEPTH = 10f
        /** Height of the rock an archer stands on when "on the ground" over water. */
        const val LOW_ROCK = 1.3f
        /** Height of a boat's deck above the water. */
        const val BOAT_DECK = 0.45f
        const val SHIELD_R = 1.25f
        const val BLAST_R = 2.2f
        const val BLAST_DAMAGE = 55
        const val HEAL_AMOUNT = 12
        /** Damage multiplier of a group archer's arrows. */
        const val GROUP_DAMAGE = 0.6f
        const val MAX_SPEED = 30f
        /** Farthest an enemy may stand (bow range with some margin). */
        const val MAX_FOE_X = 55f
        private val DUMMY = Foe(0, FoeSpec(20f, 0f, 1, 0f, false, 0, false), Ragdoll(9999f, -9999f, -1f), 1)

        /** Searches angle/power so an arrow from (sx, sy) facing [dir] passes closest to (tx, ty). */
        fun solveShot(sx: Float, sy: Float, dir: Float, tx: Float, ty: Float, wind: Float, minAngle: Float = -0.9f): Pair<Float, Float> {
            var bestA = max(0.3f, minAngle)
            var bestP = 0.7f
            var bestErr = Float.MAX_VALUE
            var a = minAngle
            while (a <= 1.3f) {
                var p = 0.15f
                while (p <= 1.0001f) {
                    val e = missDistance(sx, sy, dir, a, p, tx, ty, wind)
                    if (e < bestErr) { bestErr = e; bestA = a; bestP = p }
                    p += 0.05f
                }
                a += 0.05f
            }
            var step = 0.025f
            repeat(4) {
                for (da in -2..2) for (dp in -2..2) {
                    val aa = max(minAngle, bestA + da * step)
                    val pp = (bestP + dp * step).coerceIn(0.1f, 1f)
                    val e = missDistance(sx, sy, dir, aa, pp, tx, ty, wind)
                    if (e < bestErr) { bestErr = e; bestA = aa; bestP = pp }
                }
                step *= 0.5f
            }
            return bestA to bestP
        }

        private fun missDistance(sx: Float, sy: Float, dir: Float, aim: Float, power: Float, tx: Float, ty: Float, wind: Float): Float {
            val speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power
            var x = sx; var y = sy
            var vx = cos(aim) * speed * dir; var vy = sin(aim) * speed
            val dt = 1f / 60f
            var best = Float.MAX_VALUE
            var t = 0f
            while (t < 6f) {
                vx += wind * dt; vy -= GRAVITY * dt
                x += vx * dt; y += vy * dt
                val d = (x - tx) * (x - tx) + (y - ty) * (y - ty)
                if (d < best) best = d
                if (y < ty - 15f) break
                t += dt
            }
            return best
        }

        /** Points of the first part of a shot, for the aiming guide. */
        fun preview(sx: Float, sy: Float, dir: Float, aim: Float, power: Float, wind: Float, seconds: Float, out: FloatArray): Int {
            val speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power
            var x = sx; var y = sy
            var vx = cos(aim) * speed * dir; var vy = sin(aim) * speed
            val dt = 1f / 60f
            var n = 0
            var t = 0f
            var k = 0
            while (t < seconds && n * 2 + 1 < out.size) {
                vx += wind * dt; vy -= GRAVITY * dt
                x += vx * dt; y += vy * dt
                if (k++ % 3 == 0) { out[n * 2] = x; out[n * 2 + 1] = y; n++ }
                t += dt
            }
            return n
        }
    }

    /** Rounded helpers used by the UI. */
    fun windKmh(): Int = (spec.wind * 3.6f * 2.5f).roundToInt()
}

/** Fraction along a→b where it enters the box, or null (Liang–Barsky, no allocations). */
fun segRect(ax: Float, ay: Float, bx: Float, by: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float? {
    var t0 = 0f
    var t1 = 1f
    val dx = bx - ax
    val dy = by - ay
    for (i in 0 until 4) {
        val p = when (i) { 0 -> -dx; 1 -> dx; 2 -> -dy; else -> dy }
        val q = when (i) { 0 -> ax - x0; 1 -> x1 - ax; 2 -> ay - y0; else -> y1 - ay }
        if (p == 0f) {
            if (q < 0f) return null
        } else {
            val r = q / p
            if (p < 0f) { if (r > t1) return null; if (r > t0) t0 = r }
            else { if (r < t0) return null; if (r < t1) t1 = r }
        }
    }
    return t0
}

/** Free, not used for logic: ease for the slow motion. */
fun easeOut(t: Float): Float = 1f - exp(-5f * t)

const val TWO_PI = (2 * PI).toFloat()
