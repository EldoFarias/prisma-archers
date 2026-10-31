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
class Platform(val x0: Float, val x1: Float, val top: Float, val depth: Float = 30f) {
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
    companion object {
        const val HEAD = 0; const val NECK = 1; const val CHEST = 2; const val PELVIS = 3
        const val L_ELBOW = 4; const val L_HAND = 5; const val R_ELBOW = 6; const val R_HAND = 7
        const val L_KNEE = 8; const val L_FOOT = 9; const val R_KNEE = 10; const val R_FOOT = 11
        const val N = 12
        const val HEAD_R = 0.17f

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
    /** Strongest landing impact since last read (for dust); [thudX]/[thudY] where. */
    var thud = 0f
    var thudX = 0f
    var thudY = 0f

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
        val hop = if (cheer) abs(sin(time * 7f)) * 0.12f else 0f
        set(L_FOOT, -0.17f, hop); set(R_FOOT, 0.2f, hop)
        set(L_KNEE, -0.1f + sway, 0.45f - crouch + hop); set(R_KNEE, 0.15f + sway, 0.45f - crouch + hop)
        set(PELVIS, sway, 0.9f - crouch + hop)
        set(CHEST, 0.02f + sway - 0.03f * draw, 1.24f + breathe - crouch + hop)
        set(NECK, 0.03f + sway - 0.03f * draw, 1.52f + breathe - crouch + hop)
        if (cheer) {
            // Victory: bow raised high, other fist pumping.
            val pump = sin(time * 7f) * 0.08f
            set(HEAD, 0.04f + sway, 1.75f + breathe + hop)
            set(R_ELBOW, 0.18f, 1.78f + hop); set(R_HAND, 0.24f, 2.1f + hop)
            set(L_ELBOW, -0.2f, 1.72f + hop + pump); set(L_HAND, -0.26f, 2.0f + hop + pump * 1.5f)
            return
        }
        val ca = cos(aim); val sa = sin(aim)
        // The head leans into the aim, cheek near the string.
        set(HEAD, 0.05f + sway + ca * 0.02f - 0.03f * draw, 1.74f + breathe - crouch + sa * 0.05f)
        // Front arm holds the bow along the aim (kicks back a bit on release); back arm draws the string.
        val reach = 0.58f - 0.07f * recoil
        set(R_ELBOW, 0.03f + sway + ca * 0.3f, 1.47f - crouch + sa * 0.3f)
        set(R_HAND, 0.03f + sway + ca * reach, 1.47f - crouch + sa * reach)
        val pull = (0.58f - 0.5f * draw - 0.62f * recoil).coerceAtLeast(-0.12f)
        set(L_HAND, 0.03f + sway + ca * pull - sa * 0.05f * recoil, 1.47f - crouch + sa * pull + 0.08f * recoil)
        set(L_ELBOW, 0.03f + sway + ca * pull * 0.5f - sa * 0.18f * draw - 0.12f * (1 - draw), 1.47f - crouch + sa * pull * 0.5f + ca * 0.18f * draw - 0.1f * (1 - draw) + 0.06f * recoil)
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

    /** Back on its feet at the root (rewarded "continue"). */
    fun revive() {
        alive = true
        cheer = false
        sinceHit = 9f
        buildPose()
        for (i in 0 until N) { x[i] = poseX[i]; y[i] = poseY[i]; px[i] = x[i]; py[i] = y[i] }
    }

    fun step(dt: Float, platforms: List<Platform>) {
        sinceHit += dt
        time += dt
        recoil *= exp(-dt * 7f)
        if (alive) {
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
            for (i in 0 until N) {
                val vx = (x[i] - px[i]) * 0.995f
                val vy = (y[i] - py[i]) * 0.995f
                px[i] = x[i]; py[i] = y[i]
                x[i] += vx
                y[i] += vy - GRAVITY * dt * dt
            }
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
}

/** Something that happened, for sounds, effects and the UI. */
sealed class DuelEvent {
    data class Shot(val byPlayer: Boolean, val kind: Int = 0) : DuelEvent()
    data class Hit(
        val byPlayer: Boolean, val part: Part, val damage: Int, val x: Float, val y: Float, val kill: Boolean,
        val kind: Int = 0, val pierce: Boolean = false,
    ) : DuelEvent()
    data class Miss(val byPlayer: Boolean, val x: Float, val y: Float, val ground: Boolean) : DuelEvent()
    data class TurnStart(val player: Boolean) : DuelEvent()
    data class Over(val playerWon: Boolean) : DuelEvent()
    data class Explode(val byPlayer: Boolean, val x: Float, val y: Float, val damage: Int, val kill: Boolean) : DuelEvent()
    data class HelmetOff(val x: Float, val y: Float, val vx: Float, val vy: Float) : DuelEvent()
    data class ShieldHit(val x: Float, val y: Float, val left: Int) : DuelEvent()
    data class Frozen(val x: Float, val y: Float) : DuelEvent()
    data class Thaw(val x: Float, val y: Float) : DuelEvent()
    data class Heal(val amount: Int, val x: Float, val y: Float) : DuelEvent()
    /** Scenery bonus hit (0 bird, 1 balloon), from [ArcheryGame]. */
    data class Bonus(val kind: Int, val x: Float, val y: Float) : DuelEvent()
}

/** One duel (a level): two platforms, two archers, wind, whose turn it is. */
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

    fun level(n: Int, salt: Long = 0L): LevelSpec {
        val rnd = Random(n * 7_919L + 31 + salt)
        val d = ((n - 1) / 80f).coerceIn(0f, 1f)
        val boss = isBoss(n)
        val power = bossPower(n)
        val distance = 13f + 14f * d + rnd.nextFloat() * (4f + 6f * d)
        val heightDiff = (rnd.nextFloat() * 2f - 1f) * (0.5f + 4f * d)
        val wind = if (n < WIND_FROM) 0f else (rnd.nextFloat() * 2f - 1f) * (0.6f + 3.2f * d)
        var hp = if (boss) 150 + n else 100 + (n / 5) * 5
        if (power == BossPower.SHIELD || power == BossPower.HEAL) hp -= 30 else if (boss) hp -= 25
        // 0 = clumsy, 1 = deadly. Bosses aim better.
        val skill = (0.15f + 0.7f * d + if (boss) 0.05f else 0f).coerceAtMost(0.9f)
        return LevelSpec(n, distance, heightDiff, wind, hp, boss || n >= 25 && rnd.nextFloat() < 0.25f, skill, boss, n * 131L + salt, power)
    }

    fun daily(day: Int): LevelSpec = level(30 + day % 40, salt = day * 2_654_435_761L)
}

/**
 * The duel simulation: turns, arrows, hits and the enemy AI. The UI feeds aim/release for the
 * player; [step] advances everything. Several arrows can fly at once (triple shot, double-shooting boss):
 * the turn passes when all of them have landed.
 */
class Duel(val spec: LevelSpec, var playerHp: Int = 100, seed: Long = System.nanoTime()) {
    enum class Turn { PLAYER, PLAYER_ARROW, ENEMY, ENEMY_ARROW, OVER }

    val platforms: List<Platform>
    /** Height of the ground below the two towers. */
    val groundY: Float
    val player: Ragdoll
    val enemy: Ragdoll
    var enemyHp = spec.enemyHp
    val maxEnemyHp = spec.enemyHp
    var maxPlayerHp = 100
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
    /** Seconds the enemy has been "thinking". */
    private var enemyWait = 0f
    private var enemyShots = 0
    private val rnd = Random(seed)
    var shotsFired = 0
        private set
    /** Player shields left (blocks the next enemy hit). */
    var shields = 0
    /** Over: set when the loser's body stopped falling. */
    var finished = false
        private set
    private var overTime = 0f
    /** The enemy still wears the helmet (it flies off at the first head hit). */
    var helmetOn = spec.enemyHelmet
        private set
    /** Hits the knight boss's bubble can still take. */
    var bossShield = if (spec.power == BossPower.SHIELD) 2 else 0
        private set
    /** Hit by an ice arrow: skips the next turn. */
    var enemyFrozen = false
        private set
    private var killed = false
    private var killByPlayer = false

    init {
        val ex = spec.distance
        val ey = spec.heightDiff
        groundY = min(0f, ey) - GROUND_DEPTH
        platforms = listOf(
            Platform(-3.5f, 1.2f, 0f),
            Platform(ex - 1.2f, ex + 3.5f, ey),
            // The ground (street, sea, sand…) far below: arrows and fallen bodies end up there.
            Platform(-80f, ex + 80f, groundY, 60f),
        )
        player = Ragdoll(0f, 0f, 1f)
        enemy = Ragdoll(ex, ey, -1f)
        player.aim = 0.3f
        enemy.aim = 0.3f
    }

    // ------------------------------------------------------------------ player input

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
        if (kind == ArrowKind.TRIPLE) {
            for (k in -1..1) shoot(player, player.aim + k * 0.055f, power, true, kind, 0.6f, events, k == 0)
        } else {
            shoot(player, player.aim, power, true, kind, if (kind == ArrowKind.FIRE) 1.5f else if (kind == ArrowKind.ICE) 0.8f else 1f, events, true)
        }
        player.draw = 0f
        shotsFired++
        turn = Turn.PLAYER_ARROW
        return true
    }

    private fun shoot(r: Ragdoll, aim: Float, power: Float, byPlayer: Boolean, kind: Int, mul: Float, events: MutableList<DuelEvent>, announce: Boolean) {
        val speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power.coerceIn(0f, 1f)
        val dir = r.facing
        val a = Arrow(r.bowX(), r.bowY(), cos(aim) * speed * dir, sin(aim) * speed, byPlayer, kind, mul)
        r.recoil = 1f
        arrows.add(a)
        if (arrows.size > 28) {
            // Drop the oldest arrow that isn't in the air.
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
        enemy.cheer = false
        arrows.removeAll { it.stuckIn === player }
        inFlight.clear()
        killed = false
        finished = false
        turn = Turn.PLAYER
    }

    // ------------------------------------------------------------------ simulation

    fun step(dt: Float, events: MutableList<DuelEvent>) {
        time += dt
        player.step(dt, platforms)
        enemy.step(dt, platforms)
        var i = inFlight.size - 1
        while (i >= 0) {
            if (i < inFlight.size) stepArrow(inFlight[i], dt, events)
            i--
        }
        when (turn) {
            Turn.ENEMY -> {
                enemyWait += dt
                enemy.draw = (enemyWait / 0.9f).coerceIn(0f, 1f)
                val (ang, pow) = enemyPlan
                enemy.aim = enemy.aim + (ang - enemy.aim) * min(1f, dt * 4f)
                if (enemyWait > 1.1f) {
                    if (spec.power == BossPower.DOUBLE) {
                        shoot(enemy, ang - 0.03f, pow, false, 0, 0.6f, events, true)
                        shoot(enemy, ang + 0.03f, pow, false, 0, 0.6f, events, false)
                    } else {
                        shoot(enemy, ang, pow, false, 0, 1f, events, true)
                    }
                    enemy.draw = 0f
                    enemyShots++
                    turn = Turn.ENEMY_ARROW
                }
            }
            Turn.OVER -> {
                overTime += dt
                val loser = if (playerWon) enemy else player
                if (!finished && (loser.settled(dt) || overTime > 2.2f)) {
                    finished = true
                    events.add(DuelEvent.Over(playerWon))
                }
            }
            else -> Unit
        }
        // Arrows stuck in bodies follow them.
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

    /** Damage an arrow does on [part] (before shields). */
    fun damageFor(a: Arrow, part: Part): Int {
        val base = when {
            part == Part.HEAD && a.byPlayer && helmetOn -> 50
            part == Part.HEAD && !a.byPlayer -> PLAYER_HEAD_DAMAGE
            else -> part.damage
        }
        // A clean headshot always kills, whatever the arrow.
        if (part == Part.HEAD && a.byPlayer && !helmetOn) return 100
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
        val target = if (a.byPlayer) enemy else player
        // The knight's bubble stops player arrows first.
        if (a.byPlayer && bossShield > 0 && !a.pierced) {
            val t = segCircle(oTipX, oTipY, tipX, tipY, enemy.x[Ragdoll.CHEST], enemy.y[Ragdoll.CHEST] + 0.1f, SHIELD_R)
            if (t != null) {
                bossShield--
                val hx = oTipX + (tipX - oTipX) * t
                val hy = oTipY + (tipY - oTipY) * t
                events.add(DuelEvent.ShieldHit(hx, hy, bossShield))
                if (a.kind == ArrowKind.BOMB) {
                    events.add(DuelEvent.Explode(true, hx, hy, 0, false))
                    a.flying = false
                    arrows.remove(a)
                    land(a, events)
                    return
                }
                // Bounce back and fall.
                a.pierced = true
                a.vx = -a.vx * 0.25f
                a.vy = 2.5f
                return
            }
        }
        // Body hit
        val hit = if (!a.pierced && (target.alive || a.age < 6f)) target.hitTest(oTipX, oTipY, tipX, tipY) else null
        if (hit != null) {
            val (bone, t) = hit
            val hx = oTipX + (tipX - oTipX) * t
            val hy = oTipY + (tipY - oTipY) * t
            val part = Ragdoll.BONE_PART[bone]
            val wasAlive = target.alive
            var dmg = if (wasAlive) damageFor(a, part) else 0
            if (!a.byPlayer && shields > 0 && dmg > 0) {
                shields--
                dmg = 0
            }
            if (part == Part.HEAD && a.byPlayer && helmetOn) {
                helmetOn = false
                events.add(DuelEvent.HelmetOff(enemy.x[Ragdoll.HEAD], enemy.y[Ragdoll.HEAD] + 0.1f, a.vx * 0.25f, 4f))
            }
            if (a.kind == ArrowKind.BOMB) dmg += if (wasAlive) 20 else 0
            val speed = sqrt(a.vx * a.vx + a.vy * a.vy)
            // Push the body: the nearest point of the bone takes most of it.
            val near = if (bone == 0) Ragdoll.HEAD else Ragdoll.BONES[bone][1]
            target.impulse(near, a.vx / speed * 3.5f, a.vy / speed * 3.5f)
            val kill = wasAlive && applyDamage(a.byPlayer, dmg)
            // A fast killing arrow goes right through and keeps flying.
            val pierce = kill && speed > PIERCE_SPEED && part != Part.ARM && a.kind != ArrowKind.BOMB
            if (kill) target.kill(a.vx * (if (pierce) 1.2f else 0.9f), a.vy * 0.9f + 2f, near)
            events.add(DuelEvent.Hit(a.byPlayer, part, dmg, hx, hy, kill, a.kind, pierce))
            if (a.kind == ArrowKind.ICE && a.byPlayer && !kill && wasAlive) {
                enemyFrozen = true
                events.add(DuelEvent.Frozen(enemy.x[Ragdoll.CHEST], enemy.y[Ragdoll.CHEST]))
            }
            if (a.kind == ArrowKind.BOMB) {
                blast(a, hx, hy, events, direct = true)
                return
            }
            if (pierce) {
                a.pierced = true
                a.vx *= 0.6f
                a.vy *= 0.6f
                return
            }
            stick(a, target, bone, hx, hy)
            land(a, events)
            return
        }
        // Platform or out of the world
        for (p in platforms) {
            if (p.contains(tipX, tipY)) {
                if (a.kind == ArrowKind.BOMB) {
                    blast(a, tipX, tipY, events, direct = false)
                    return
                }
                a.flying = false
                a.stuckTime = time
                events.add(DuelEvent.Miss(a.byPlayer, tipX, tipY, true))
                land(a, events)
                return
            }
        }
        if (a.y < -40f || abs(a.x - spec.distance / 2f) > spec.distance + 60f) {
            a.flying = false
            events.add(DuelEvent.Miss(a.byPlayer, a.x, a.y, false))
            land(a, events)
        }
    }

    /** Takes [dmg] off the other archer; true when it was the killing blow. */
    private fun applyDamage(byPlayer: Boolean, dmg: Int): Boolean {
        if (killed) return false
        if (byPlayer) {
            enemyHp = (enemyHp - dmg).coerceAtLeast(0)
            if (enemyHp == 0) { killed = true; killByPlayer = true }
        } else {
            playerHp = (playerHp - dmg).coerceAtLeast(0)
            if (playerHp == 0) { killed = true; killByPlayer = false }
        }
        return killed && killByPlayer == byPlayer
    }

    /** Bomb arrow: area damage (unless it already hit the body directly) and a shove away from the blast. */
    private fun blast(a: Arrow, x: Float, y: Float, events: MutableList<DuelEvent>, direct: Boolean) {
        a.flying = false
        arrows.remove(a)
        val target = if (a.byPlayer) enemy else player
        val dx = target.x[Ragdoll.CHEST] - x
        val dy = target.y[Ragdoll.CHEST] - y
        val d = sqrt(dx * dx + dy * dy)
        var dmg = 0
        var kill = false
        if (!direct && d < BLAST_R && target.alive) {
            dmg = (BLAST_DAMAGE * (1f - d / BLAST_R)).roundToInt().coerceAtLeast(5)
            kill = applyDamage(a.byPlayer, dmg)
        }
        if (d < BLAST_R * 1.3f) {
            val push = 9f * (1f - d / (BLAST_R * 1.3f))
            val nx = if (d > 1e-3f) dx / d else 0f
            val ny = if (d > 1e-3f) dy / d else 1f
            if (kill) target.kill(nx * push * 1.5f, ny * push + 5f, Ragdoll.CHEST)
            else for (p in 0 until Ragdoll.N) target.impulse(p, nx * push * 0.4f, ny * push * 0.4f + 1f)
        }
        events.add(DuelEvent.Explode(a.byPlayer, x, y, dmg, kill))
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
        // The arrow shaft sits mostly outside the body: its centre is behind the tip.
        a.depth = -0.22f
    }

    /** An arrow finished its flight; when none is left in the air the turn resolves. */
    private fun land(a: Arrow, events: MutableList<DuelEvent>) {
        inFlight.remove(a)
        if (inFlight.isNotEmpty() || turn == Turn.OVER) return
        if (killed) {
            playerWon = killByPlayer
            (if (killByPlayer) player else enemy).cheer = true
            turn = Turn.OVER
            overTime = 0f
            return
        }
        if (a.byPlayer) {
            if (enemyFrozen) {
                // The frozen enemy loses this turn.
                enemyFrozen = false
                events.add(DuelEvent.Thaw(enemy.x[Ragdoll.CHEST], enemy.y[Ragdoll.CHEST]))
                turn = Turn.PLAYER
                events.add(DuelEvent.TurnStart(true))
                return
            }
            turn = Turn.ENEMY
            enemyWait = 0f
            if (spec.power == BossPower.HEAL && enemyHp < maxEnemyHp) {
                val heal = min(HEAL_AMOUNT, maxEnemyHp - enemyHp)
                enemyHp += heal
                events.add(DuelEvent.Heal(heal, enemy.x[Ragdoll.HEAD], enemy.y[Ragdoll.HEAD] + 0.4f))
            }
            enemyPlan = planEnemyShot()
            events.add(DuelEvent.TurnStart(false))
        } else {
            turn = Turn.PLAYER
            events.add(DuelEvent.TurnStart(true))
        }
    }

    /**
     * Would this arrow land a killing blow within [seconds]? Used for the kill cam (bodies are taken as
     * they are now).
     */
    fun predictKill(a: Arrow, seconds: Float): Boolean {
        if (a.pierced || killed) return false
        if (a.byPlayer && bossShield > 0) return false
        val target = if (a.byPlayer) enemy else player
        if (!target.alive) return false
        var x = a.x; var y = a.y; var vx = a.vx; var vy = a.vy
        val dt = 1f / 60f
        var t = 0f
        while (t < seconds) {
            val ox = x; val oy = y
            vx += spec.wind * dt; vy -= GRAVITY * dt
            x += vx * dt; y += vy * dt
            val ang = atan2(vy, vx)
            val c = cos(ang) * 0.35f; val s = sin(ang) * 0.35f
            val hit = target.hitTest(ox + c, oy + s, x + c, y + s)
            if (hit != null) {
                val part = Ragdoll.BONE_PART[hit.first]
                var dmg = damageFor(a, part)
                if (a.kind == ArrowKind.BOMB) dmg += 20
                if (!a.byPlayer && shields > 0) dmg = 0
                return dmg >= (if (a.byPlayer) enemyHp else playerHp)
            }
            for (p in platforms) if (p.contains(x + c, y + s)) return false
            t += dt
        }
        return false
    }

    // ------------------------------------------------------------------ enemy AI

    private var enemyPlan: Pair<Float, Float> = 0.3f to 0.7f

    /**
     * Finds an angle/power that would hit the player's chest (wind included), then adds an error that
     * shrinks with skill and with every shot (the enemy "learns").
     */
    private fun planEnemyShot(): Pair<Float, Float> {
        // Aim at a point near the player's chest: the miss distance (meters) shrinks with skill and a bit
        // with every shot, but never to zero. Same feel at any distance.
        val learn = (1f - enemyShots * ENEMY_LEARN).coerceAtLeast(ENEMY_LEARN_FLOOR)
        val focus = if (spec.power == BossPower.FOCUS) 0.8f else 1f
        val sigma = (ENEMY_ERR_MAX + (ENEMY_ERR_MIN - ENEMY_ERR_MAX) * spec.enemySkill) * learn * focus
        // Height is what decides a hit (the arc crosses the body anyway), so the error is vertical.
        val tx = player.x[Ragdoll.CHEST] + gauss() * 0.3f
        val ty = player.y[Ragdoll.CHEST] - 0.15f + gauss() * sigma
        return solveShot(enemy.bowX(), enemy.bowY(), -1f, tx, ty, spec.wind)
    }

    private fun gauss(): Float {
        // Sum of uniforms ≈ normal (mean 0, sd 1).
        var s = 0f
        repeat(4) { s += rnd.nextFloat() }
        return (s - 2f) * 1.732f
    }

    companion object {
        /** Enemy miss spread (meters, 1 sd) for skill 0 and skill 1. */
        var ENEMY_ERR_MAX = 2.6f
        var ENEMY_ERR_MIN = 1.3f
        var ENEMY_LEARN = 0.07f
        var ENEMY_LEARN_FLOOR = 0.75f
        /** Damage the player takes on a headshot (not an instant kill: that would feel unfair). */
        var PLAYER_HEAD_DAMAGE = 50
        const val MIN_SPEED = 9f
        const val PIERCE_SPEED = 20f
        /** How far below the lower tower top the ground is (meters). */
        const val GROUND_DEPTH = 10f
        const val SHIELD_R = 1.25f
        const val BLAST_R = 2.2f
        const val BLAST_DAMAGE = 55
        const val HEAL_AMOUNT = 12
        const val MAX_SPEED = 30f

        /** Searches angle/power so an arrow from (sx, sy) facing [dir] passes closest to (tx, ty). */
        fun solveShot(sx: Float, sy: Float, dir: Float, tx: Float, ty: Float, wind: Float): Pair<Float, Float> {
            var bestA = 0.3f
            var bestP = 0.7f
            var bestErr = Float.MAX_VALUE
            var a = -0.4f
            while (a <= 1.2f) {
                var p = 0.15f
                while (p <= 1.0001f) {
                    val e = missDistance(sx, sy, dir, a, p, tx, ty, wind)
                    if (e < bestErr) { bestErr = e; bestA = a; bestP = p }
                    p += 0.05f
                }
                a += 0.05f
            }
            // Refine around the best.
            var step = 0.025f
            repeat(4) {
                for (da in -2..2) for (dp in -2..2) {
                    val aa = bestA + da * step
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

/** Free, not used for logic: ease for the slow motion. */
fun easeOut(t: Float): Float = 1f - exp(-5f * t)

const val TWO_PI = (2 * PI).toFloat()
