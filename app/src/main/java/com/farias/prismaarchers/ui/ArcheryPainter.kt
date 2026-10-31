package com.farias.prismaarchers.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.farias.prismaarchers.data.Bow
import com.farias.prismaarchers.data.BowStyle
import com.farias.prismaarchers.data.Hat
import com.farias.prismaarchers.data.Outfit
import com.farias.prismaarchers.data.Trails
import com.farias.prismaarchers.data.World
import com.farias.prismaarchers.game.Arrow
import com.farias.prismaarchers.game.Duel
import com.farias.prismaarchers.game.GameHost
import com.farias.prismaarchers.game.Ragdoll
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 0xRRGGBB → opaque Compose colour. */
fun rgb(c: Long, alpha: Float = 1f): Color = Color(0xFF000000L or c).let { if (alpha < 1f) it.copy(alpha = alpha) else it }

/**
 * The duel view: drawn every frame on a plain Canvas. The game is advanced from the frame loop and
 * the drawing reads a tick state, so nothing recomposes while playing.
 */
@Composable
fun GameView(host: GameHost, modifier: Modifier) {
    val painter = remember { ArcheryPainter() }
    val tick = remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer(cacheSize = 0)
    LaunchedEffect(host) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (!host.renderPaused) {
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f)
                    host.frame(dt)
                    tick.longValue = now
                }
                last = now
            }
        }
    }
    Canvas(modifier) {
        tick.longValue
        painter.draw(this, host, measurer)
    }
}

class ArcheryPainter {
    // World → screen: sx = (x - camX) * k + cx, sy = cy - (y - camY) * k
    private var camX = 0f
    private var camY = 0f
    var k = 100f
        private set
    private var cx = 0f
    private var cy = 0f

    fun setTransform(camX: Float, camY: Float, k: Float, cx: Float, cy: Float) {
        this.camX = camX; this.camY = camY; this.k = k; this.cx = cx; this.cy = cy
        strokes(k)
    }

    fun sx(x: Float) = (x - camX) * k + cx
    fun sy(y: Float) = cy - (y - camY) * k

    private val path = Path()
    private val path2 = Path()

    // ------------------------------------------------------------------ scenery cache
    private var sceneWorld: World? = null
    private var sceneW = 0f
    private var sceneH = 0f
    private val farPath = Path()
    private val nearPath = Path()
    private var tileW = 1f
    private var skyBrush: Brush = Brush.verticalGradient(listOf(Color.Black, Color.Black))
    private var sunBrush: Brush = skyBrush
    private var vignette: Brush = skyBrush
    private var farColor = Color.Gray
    private var nearColor = Color.DarkGray
    private var sunPos = Offset.Zero
    private var sunR = 1f

    // Ambient particles (screen space, positions derived from the clock: no state)
    private val airA = FloatArray(AIR)
    private val airB = FloatArray(AIR)
    private val airS = FloatArray(AIR)
    private val airV = FloatArray(AIR)

    // Trail of the flying arrow
    private val trailX = FloatArray(TRAIL)
    private val trailY = FloatArray(TRAIL)
    private var trailN = 0
    private var trailOwner: Arrow? = null
    private val guide = FloatArray(80)

    // Text layouts cache
    private val texts = arrayOf(HashMap<String, TextLayoutResult>(), HashMap(), HashMap(), HashMap())
    private var distInt = -1
    private var distStr = ""
    private val textStyleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 4f))
    private val textStyleDmg = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 5f))
    private val textStyleHead = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD34D), shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 6f))
    private val textStyleBig = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF8A2B), shadow = Shadow(Color(0xDD000000), Offset(0f, 3f), 8f))

    private var strokeW = -1f
    private var bowStroke = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private var glowStroke = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private var thinStroke = Stroke(1f, cap = StrokeCap.Round)

    init {
        val rnd = Random(5)
        for (i in 0 until AIR) {
            airA[i] = rnd.nextFloat(); airB[i] = rnd.nextFloat(); airS[i] = 0.5f + rnd.nextFloat(); airV[i] = rnd.nextFloat()
        }
    }

    private fun layout(m: TextMeasurer, text: String, style: TextStyle): TextLayoutResult {
        val map = texts[when (style) { textStyleSmall -> 0; textStyleDmg -> 1; textStyleHead -> 2; else -> 3 }]
        map[text]?.let { return it }
        if (map.size > 60) map.clear()
        return m.measure(text, style).also { map[text] = it }
    }

    private fun strokes(k: Float) {
        if (abs(k - strokeW) < 0.5f) return
        strokeW = k
        bowStroke = Stroke(0.065f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        glowStroke = Stroke(0.17f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        thinStroke = Stroke(max(1f, 0.018f * k), cap = StrokeCap.Round)
    }

    // ------------------------------------------------------------------ frame

    fun draw(d: DrawScope, host: GameHost, m: TextMeasurer) {
        val w = d.size.width
        val h = d.size.height
        if (w < 2f || h < 2f) return
        val g = host.game
        val duel = g.duel
        val world = host.world
        val kk = w / g.viewW
        val sh = g.shake * kk * 0.12f
        setTransform(g.camX, g.camY, kk, w / 2f + sin(g.clock * 53f) * sh, h * 0.56f + cos(g.clock * 47f) * sh)
        if (world !== sceneWorld || w != sceneW || h != sceneH) buildScene(world, w, h)

        drawSky(d, w, h, g.clock, world, host.windKmh)
        // Platforms
        for (p in duel.platforms) drawPlatform(d, p.x0, p.x1, p.top, h, world)
        // Arrows in the ground / bodies
        for (a in duel.arrows) if (a !== duel.flying) drawArrow(d, a.x, a.y, a.angle, a.byPlayer, if (a.byPlayer) host.playerBow else host.enemyBow, 1f)
        // Archers
        val enemyHelmet = duel.spec.enemyHelmet
        drawArcher(d, duel.enemy, host.enemyOutfit, host.enemyBow, enemyHelmet, duel.turn == Duel.Turn.ENEMY && duel.enemy.alive, g.clock)
        drawArcher(d, duel.player, host.playerOutfit, host.playerBow, false, duel.turn == Duel.Turn.PLAYER && duel.player.alive, g.clock)
        // Flying arrow + trail
        val fl = duel.flying
        if (fl != null) {
            if (fl !== trailOwner) { trailOwner = fl; trailN = 0 }
            pushTrail(fl.x, fl.y)
            drawTrail(d, if (fl.byPlayer) host.trailStyle else Trails.CLASSIC, if (fl.byPlayer) host.playerBow.trail else 0xFF9A9A, g.clock)
            drawArrow(d, fl.x, fl.y, fl.angle, fl.byPlayer, if (fl.byPlayer) host.playerBow else host.enemyBow, 1f)
        } else if (trailN > 0) {
            trailN = max(0, trailN - 2)
            drawTrail(d, host.trailStyle, host.playerBow.trail, g.clock)
        }
        // Aim guide
        if (g.aiming && g.aimPower > 0.04f && duel.turn == Duel.Turn.PLAYER) {
            val n = g.guide(guide)
            for (i in 0 until n) {
                val f = 1f - i / n.toFloat()
                d.drawCircle(Color.White.copy(alpha = 0.85f * f), (0.035f + 0.03f * f) * k, Offset(sx(guide[i * 2]), sy(guide[i * 2 + 1])))
            }
            val pl = duel.player
            val deg = Math.round(Math.toDegrees(g.aimAngle.toDouble())).toInt()
            val pct = Math.round(g.aimPower * 100f)
            val t = layout(m, "$deg°  $pct%", textStyleSmall)
            d.drawText(t, topLeft = Offset(sx(pl.rootX) - t.size.width / 2f, sy(pl.rootY + 2.55f) - t.size.height))
        }
        // Particles
        val pt = g.particles
        for (i in 0 until pt.count) {
            val a = (pt.life[i] / pt.maxLife[i]).coerceIn(0f, 1f)
            val r = pt.size[i] * k
            if (r < 0.5f) continue
            d.drawCircle(Color(pt.r[i], pt.g[i], pt.b[i], a), r, Offset(sx(pt.x[i]), sy(pt.y[i])))
        }
        // HP bars
        hpBar(d, duel.player, duel.playerHp, duel.maxPlayerHp, Color(0xFF34D399), duel.turn == Duel.Turn.PLAYER, g.clock, host.shields)
        hpBar(d, duel.enemy, duel.enemyHp, duel.maxEnemyHp, Color(0xFFF43F5E), false, g.clock, 0)
        // Floating texts
        for (t in g.texts) {
            val age = g.clock - t.born
            val style = when (t.kind) { 1 -> textStyleHead; 2 -> textStyleBig; else -> textStyleDmg }
            val lay = layout(m, if (t.kind == 2) HEADSHOT else t.text, style)
            val alpha = (1f - (age - 0.9f) / 0.5f).coerceIn(0f, 1f)
            val pop = if (age < 0.12f) 0.5f + age / 0.12f * 0.7f else 1.2f - min(0.2f, (age - 0.12f))
            val px = sx(t.x)
            val py = sy(t.y + age * 0.9f)
            d.scale(pop, pop, Offset(px, py)) {
                drawText(lay, topLeft = Offset(px - lay.size.width / 2f, py - lay.size.height / 2f), alpha = alpha)
            }
        }
        // Off-screen markers
        if (duel.turn != Duel.Turn.OVER) {
            offscreen(d, m, duel.enemy, w, h, Color(0xFFF43F5E), duel.spec.distance)
            offscreen(d, m, duel.player, w, h, Color(0xFF34D399), duel.spec.distance)
        }
        // Slow motion vignette
        if (g.slow > 0.02f) d.drawRect(vignette, alpha = (g.slow * 0.75f).coerceAtMost(1f))
    }

    // ------------------------------------------------------------------ scenery

    private fun buildScene(world: World, w: Float, h: Float) {
        sceneWorld = world; sceneW = w; sceneH = h
        tileW = w * 1.6f
        skyBrush = Brush.verticalGradient(listOf(rgb(world.skyTop), rgb(world.skyBottom)), startY = 0f, endY = h * 0.75f)
        sunPos = Offset(w * 0.74f, h * 0.17f)
        sunR = w * 0.085f
        sunBrush = Brush.radialGradient(listOf(rgb(world.sun, 0.55f), rgb(world.sun, 0f)), sunPos, max(1f, sunR * 3.2f))
        vignette = Brush.radialGradient(
            0.45f to Color.Transparent, 1f to Color(0xAA05010F),
            center = Offset(w / 2f, h / 2f), radius = max(1f, max(w, h) * 0.75f),
        )
        farColor = rgb(world.far)
        nearColor = rgb(world.near)
        val rnd = Random(world.nameRes)
        val jag = FloatArray(17) { 0.35f + rnd.nextFloat() * 0.65f }
        jag[16] = jag[0]
        val tw = tileW
        fun jagged(u: Float): Float {
            val p = u * 16f
            val i = floor(p).toInt().coerceIn(0, 15)
            val f = p - i
            return jag[i] + (jag[i + 1] - jag[i]) * f
        }
        val TAU = 6.2831855f
        val sc = world.scenery
        fun far(u: Float): Float = when (sc) {
            1 -> w * (0.12f + 0.2f * jagged(u))
            2 -> w * (0.06f + 0.03f * sin(u * TAU * 2) + 0.02f * sin(u * TAU * 5 + 1f))
            3 -> {
                val v = 1f - abs(u - 0.5f) * 3.2f
                val cone = if (v > 0f) min(v, 0.86f) * 0.42f else 0f
                w * (0.07f + 0.03f * sin(u * TAU * 3) + cone)
            }
            else -> w * (0.1f + 0.05f * sin(u * TAU * 2 + 0.5f) + 0.03f * sin(u * TAU * 5))
        }
        fun near(u: Float): Float = when (sc) {
            1 -> w * (0.04f + 0.09f * jagged((u * 2f + 0.37f) % 1f))
            2 -> w * (0.04f + 0.035f * sin(u * TAU * 3) + 0.015f * sin(u * TAU * 7))
            3 -> w * (0.03f + 0.05f * jagged((u * 3f) % 1f))
            4 -> {
                val base = w * (0.035f + 0.01f * sin(u * TAU * 2))
                val q = (u * 3f) % 1f
                val cren = ((u * 300).toInt()) % 2 == 0
                when {
                    q in 0.1f..0.18f -> w * 0.24f + (if (cren) w * 0.018f else 0f)
                    q in 0.18f..0.42f -> w * 0.14f + (if (cren) w * 0.014f else 0f)
                    q in 0.42f..0.5f -> w * 0.2f + (if (cren) w * 0.018f else 0f)
                    else -> base
                }
            }
            5 -> {
                var best = w * (0.03f + 0.01f * sin(u * TAU * 3))
                val trees = 18
                val p = u * trees
                for (j in -1..1) {
                    val c = floor(p).toInt() + j
                    val jit = (((c * 7919 + 13) % 100 + 100) % 100) / 100f
                    val center = (c + 0.5f + (jit - 0.5f) * 0.4f)
                    val dd = abs(p - center) / 0.32f
                    if (dd < 1f) best = max(best, w * (0.1f + 0.08f * jit) * (1f - dd) + w * 0.03f)
                }
                best
            }
            else -> w * (0.04f + 0.025f * sin(u * TAU * 3 + 2f) + 0.012f * sin(u * TAU * 8))
        }
        fill(farPath, tw, h, if (sc == 1 || sc == 3) 64 else 96, ::far)
        fill(nearPath, tw, h, if (sc >= 4) 420 else 140, ::near)
    }

    private fun fill(p: Path, tw: Float, h: Float, n: Int, fn: (Float) -> Float) {
        p.reset()
        p.moveTo(0f, h * 2f)
        for (i in 0..n) {
            val u = i / n.toFloat()
            p.lineTo(u * tw, -fn(u))
        }
        p.lineTo(tw, h * 2f)
        p.close()
    }

    private fun drawSky(d: DrawScope, w: Float, h: Float, clock: Float, world: World, windKmh: Int) {
        d.drawRect(skyBrush)
        if (world.air == 4) {
            // Stars
            for (i in 0 until AIR) {
                val tw = 0.4f + 0.6f * ((sin(clock * 2f * airS[i] + airV[i] * 6f) + 1f) / 2f)
                d.drawCircle(Color.White.copy(alpha = 0.75f * tw), 1.4f + airS[i] * 1.6f, Offset(airA[i] * w, airB[i] * h * 0.55f))
            }
        }
        d.drawCircle(sunBrush, sunR * 3.2f, sunPos)
        d.drawCircle(rgb(world.sun), sunR, sunPos)
        val detailed = com.farias.prismaarchers.data.Progress.detailedScenery
        // Far layer
        val farY = h * 0.62f + (g0(camY) * k * 0.12f)
        if (detailed) {
            layer(d, farPath, farColor, camX * k * 0.1f, farY)
            // Haze between the layers
            d.drawRect(rgb(world.skyBottom, 0.28f), topLeft = Offset(0f, farY - w * 0.25f), size = Size(w, h))
        }
        val nearY = h * 0.72f + (g0(camY) * k * 0.3f)
        layer(d, nearPath, nearColor, camX * k * 0.28f, nearY)
        // Ambient particles
        if (detailed && world.air != 0 && world.air != 4) {
            val wind = windKmh / 30f
            for (i in 0 until AIR) {
                val sp = airS[i]
                // Snow falls, embers rise, petals and leaves drift with the wind.
                val vx = when (world.air) { 1 -> 0.02f + wind * 0.08f; 2 -> 0.01f + wind * 0.06f; else -> 0.05f + wind * 0.08f }
                val vy = when (world.air) { 1 -> 0.06f * sp; 2 -> -0.07f * sp; else -> 0.035f * sp }
                val x = frac(airA[i] + clock * vx * sp + sin(clock * 0.9f + i) * 0.01f) * w
                val y = frac(airB[i] + clock * vy) * h
                when (world.air) {
                    1 -> d.drawCircle(Color.White.copy(alpha = 0.8f), (0.02f + sp * 0.02f) * k, Offset(x, y))
                    2 -> d.drawCircle(Color(1f, 0.55f + 0.3f * airV[i], 0.15f, 0.55f + 0.4f * sin(clock * 5f + i).coerceAtLeast(0f)), (0.015f + sp * 0.015f) * k, Offset(x, y))
                    3 -> d.drawOval(Color(0xFFFBCFE8).copy(alpha = 0.85f), Offset(x, y), Size((0.06f + sp * 0.04f) * k, (0.035f + sp * 0.025f) * k))
                    else -> d.drawOval(Color(0xFF84CC16).copy(alpha = 0.7f), Offset(x, y), Size((0.06f + sp * 0.04f) * k, (0.03f + sp * 0.02f) * k))
                }
            }
        }
    }

    private fun g0(camY: Float) = camY - 1.6f

    private fun layer(d: DrawScope, p: Path, color: Color, scroll: Float, baseY: Float) {
        var off = -(scroll % tileW)
        if (off > 0f) off -= tileW
        var x = off
        while (x < d.size.width) {
            d.translate(x, baseY) { drawPath(p, color) }
            x += tileW
        }
    }

    private fun drawPlatform(d: DrawScope, x0: Float, x1: Float, top: Float, h: Float, world: World) {
        val l = sx(x0)
        val r = sx(x1)
        val t = sy(top)
        if (r < 0f || l > d.size.width || t > h) return
        val rock = rgb(world.rock)
        d.drawRect(rock, Offset(l, t), Size(r - l, h - t + 4f))
        // Shading and stone courses
        d.drawRect(Color(0x33000000), Offset(r - 0.35f * k, t), Size(0.35f * k, h - t))
        d.drawRect(Color(0x1FFFFFFF), Offset(l, t), Size(0.25f * k, h - t))
        var yy = t + 0.6f * k
        var row = 0
        while (yy < h) {
            d.drawLine(Color(0x26000000), Offset(l, yy), Offset(r, yy), max(1f, 0.04f * k))
            val shift = if (row % 2 == 0) 0.4f else 1.1f
            var xx = l + shift * k
            while (xx < r) {
                d.drawLine(Color(0x1F000000), Offset(xx, yy - 0.6f * k), Offset(xx, yy), max(1f, 0.035f * k))
                xx += 1.4f * k
            }
            yy += 0.6f * k
            row++
        }
        // Grass / snow / sand cap
        d.drawRect(rgb(world.rockTop), Offset(l - 0.06f * k, t - 0.04f * k), Size(r - l + 0.12f * k, 0.2f * k))
        d.drawRect(Color(0x33000000), Offset(l - 0.06f * k, t + 0.14f * k), Size(r - l + 0.12f * k, 0.05f * k))
    }

    // ------------------------------------------------------------------ archers

    /** Draws a ragdoll archer with its look; [nock] puts an arrow on the string. */
    fun drawArcher(d: DrawScope, r: Ragdoll, o: Outfit, b: Bow, helmet: Boolean, nock: Boolean, clock: Float) {
        val x = r.x
        val y = r.y
        val limbs = rgb(o.limbs)
        val limbsBack = Palette.darker(limbs, 0.3f)
        val body = rgb(o.body)
        val lw = 0.12f * k
        // Back limbs
        line(d, limbsBack, x[Ragdoll.PELVIS], y[Ragdoll.PELVIS], x[Ragdoll.L_KNEE], y[Ragdoll.L_KNEE], lw)
        line(d, limbsBack, x[Ragdoll.L_KNEE], y[Ragdoll.L_KNEE], x[Ragdoll.L_FOOT], y[Ragdoll.L_FOOT], lw * 0.9f)
        // Torso
        line(d, Palette.darker(body, 0.12f), x[Ragdoll.CHEST], y[Ragdoll.CHEST], x[Ragdoll.PELVIS], y[Ragdoll.PELVIS], 0.26f * k)
        line(d, body, x[Ragdoll.NECK], y[Ragdoll.NECK], x[Ragdoll.CHEST], y[Ragdoll.CHEST], 0.28f * k)
        // Belt
        val bx = x[Ragdoll.CHEST] + (x[Ragdoll.PELVIS] - x[Ragdoll.CHEST]) * 0.72f
        val by = y[Ragdoll.CHEST] + (y[Ragdoll.PELVIS] - y[Ragdoll.CHEST]) * 0.72f
        d.drawCircle(Palette.darker(body, 0.45f), 0.13f * k, Offset(sx(bx), sy(by)))
        // Front leg
        line(d, limbs, x[Ragdoll.PELVIS], y[Ragdoll.PELVIS], x[Ragdoll.R_KNEE], y[Ragdoll.R_KNEE], lw)
        line(d, limbs, x[Ragdoll.R_KNEE], y[Ragdoll.R_KNEE], x[Ragdoll.R_FOOT], y[Ragdoll.R_FOOT], lw * 0.9f)
        // Boots
        d.drawCircle(Palette.darker(limbs, 0.5f), 0.075f * k, Offset(sx(x[Ragdoll.L_FOOT]), sy(y[Ragdoll.L_FOOT])))
        d.drawCircle(Palette.darker(limbs, 0.5f), 0.075f * k, Offset(sx(x[Ragdoll.R_FOOT]), sy(y[Ragdoll.R_FOOT])))
        // Draw arm (back hand on the string)
        line(d, Palette.darker(body, 0.2f), x[Ragdoll.NECK], y[Ragdoll.NECK], x[Ragdoll.L_ELBOW], y[Ragdoll.L_ELBOW], lw * 0.95f)
        line(d, Palette.darker(body, 0.2f), x[Ragdoll.L_ELBOW], y[Ragdoll.L_ELBOW], x[Ragdoll.L_HAND], y[Ragdoll.L_HAND], lw * 0.85f)
        // Head
        drawHead(d, r, o, helmet)
        // Bow, string and nocked arrow
        drawBow(d, r, b, nock, clock)
        // Bow arm
        line(d, body, x[Ragdoll.NECK], y[Ragdoll.NECK], x[Ragdoll.R_ELBOW], y[Ragdoll.R_ELBOW], lw * 0.95f)
        line(d, body, x[Ragdoll.R_ELBOW], y[Ragdoll.R_ELBOW], x[Ragdoll.R_HAND], y[Ragdoll.R_HAND], lw * 0.85f)
        d.drawCircle(rgb(o.head), 0.06f * k, Offset(sx(x[Ragdoll.R_HAND]), sy(y[Ragdoll.R_HAND])))
        d.drawCircle(rgb(o.head), 0.055f * k, Offset(sx(x[Ragdoll.L_HAND]), sy(y[Ragdoll.L_HAND])))
    }

    private fun line(d: DrawScope, c: Color, x0: Float, y0: Float, x1: Float, y1: Float, wpx: Float) {
        d.drawLine(c, Offset(sx(x0), sy(y0)), Offset(sx(x1), sy(y1)), wpx, StrokeCap.Round)
    }

    private fun drawHead(d: DrawScope, r: Ragdoll, o: Outfit, helmet: Boolean) {
        val hx = sx(r.x[Ragdoll.HEAD])
        val hy = sy(r.y[Ragdoll.HEAD])
        val nx = sx(r.x[Ragdoll.NECK])
        val ny = sy(r.y[Ragdoll.NECK])
        val hr = Ragdoll.HEAD_R * k * 1.12f
        val tilt = Math.toDegrees(atan2((hx - nx).toDouble(), (ny - hy).toDouble())).toFloat()
        val f = r.facing
        val c = Offset(hx, hy)
        val hat = if (helmet) Hat.HELMET else o.hat
        val hatC = if (helmet) Color(0xFFCBD5E1) else rgb(o.hatColor)
        d.rotate(tilt, c) {
            scale(f, 1f, c) {
                // Hood goes behind the head
                if (hat == Hat.HOOD) drawCircle(hatC, hr * 1.3f, Offset(hx - hr * 0.15f, hy - hr * 0.08f))
                drawCircle(rgb(o.head), hr, c)
                // Face
                if (hat == Hat.SKULL) {
                    drawCircle(Color(0xFF111827), hr * 0.2f, Offset(hx + hr * 0.18f, hy - hr * 0.08f))
                    drawCircle(Color(0xFF111827), hr * 0.2f, Offset(hx + hr * 0.62f, hy - hr * 0.08f))
                    drawRect(Color(0xFF111827), Offset(hx + hr * 0.28f, hy + hr * 0.38f), Size(hr * 0.42f, hr * 0.1f))
                } else if (hat != Hat.VISOR) {
                    drawCircle(Color(0xFF1F2937), hr * 0.13f, Offset(hx + hr * 0.5f, hy - hr * 0.1f))
                    drawLine(Color(0x66000000), Offset(hx + hr * 0.3f, hy + hr * 0.42f), Offset(hx + hr * 0.62f, hy + hr * 0.38f), hr * 0.08f, StrokeCap.Round)
                }
                drawHat(this, hat, hatC, o, hx, hy, hr)
            }
        }
    }

    private fun drawHat(d: DrawScope, hat: Int, c: Color, o: Outfit, hx: Float, hy: Float, hr: Float) {
        when (hat) {
            Hat.CAP -> {
                d.drawArc(c, 180f, 180f, true, Offset(hx - hr * 1.02f, hy - hr * 1.08f), Size(hr * 2.04f, hr * 1.7f))
                d.drawRoundRect(Palette.darker(c, 0.2f), Offset(hx, hy - hr * 0.3f), Size(hr * 1.3f, hr * 0.24f), androidx.compose.ui.geometry.CornerRadius(hr * 0.12f))
            }
            Hat.HOOD -> {
                d.drawArc(c, 150f, 220f, false, Offset(hx - hr * 1.15f, hy - hr * 1.2f), Size(hr * 2.3f, hr * 2.3f), style = Stroke(hr * 0.35f))
            }
            Hat.HELMET -> {
                d.drawArc(c, 175f, 190f, true, Offset(hx - hr * 1.12f, hy - hr * 1.15f), Size(hr * 2.24f, hr * 2f))
                d.drawRect(Palette.darker(c, 0.35f), Offset(hx - hr * 1.1f, hy - hr * 0.2f), Size(hr * 2.2f, hr * 0.18f))
                d.drawRect(Palette.darker(c, 0.15f), Offset(hx - hr * 0.08f, hy - hr * 1.12f), Size(hr * 0.16f, hr * 0.95f))
                d.drawArc(Color.White.copy(alpha = 0.35f), 200f, 60f, false, Offset(hx - hr * 0.8f, hy - hr * 0.95f), Size(hr * 1.4f, hr * 1.4f), style = Stroke(hr * 0.12f))
            }
            Hat.CROWN -> {
                path2.reset()
                path2.moveTo(hx - hr * 0.8f, hy - hr * 0.65f)
                path2.lineTo(hx - hr * 0.85f, hy - hr * 1.55f)
                path2.lineTo(hx - hr * 0.42f, hy - hr * 1.05f)
                path2.lineTo(hx, hy - hr * 1.75f)
                path2.lineTo(hx + hr * 0.42f, hy - hr * 1.05f)
                path2.lineTo(hx + hr * 0.85f, hy - hr * 1.55f)
                path2.lineTo(hx + hr * 0.8f, hy - hr * 0.65f)
                path2.close()
                d.drawPath(path2, c)
                d.drawCircle(Color(0xFFE11D48), hr * 0.13f, Offset(hx, hy - hr * 0.9f))
                d.drawCircle(Color(0xFF22D3EE), hr * 0.1f, Offset(hx - hr * 0.5f, hy - hr * 0.85f))
                d.drawCircle(Color(0xFF22D3EE), hr * 0.1f, Offset(hx + hr * 0.5f, hy - hr * 0.85f))
            }
            Hat.VIKING -> {
                d.drawArc(c, 180f, 180f, true, Offset(hx - hr * 1.05f, hy - hr * 1.1f), Size(hr * 2.1f, hr * 1.8f))
                d.drawRect(Palette.darker(c, 0.3f), Offset(hx - hr * 1.05f, hy - hr * 0.3f), Size(hr * 2.1f, hr * 0.16f))
                for (si in 0..1) {
                    val s = if (si == 0) -1f else 1f
                    path2.reset()
                    path2.moveTo(hx + s * hr * 0.8f, hy - hr * 0.55f)
                    path2.quadraticTo(hx + s * hr * 1.7f, hy - hr * 0.7f, hx + s * hr * 1.5f, hy - hr * 1.7f)
                    path2.quadraticTo(hx + s * hr * 1.3f, hy - hr * 1.0f, hx + s * hr * 0.7f, hy - hr * 0.95f)
                    path2.close()
                    d.drawPath(path2, Color(0xFFFFF7E0))
                }
            }
            Hat.BAND -> {
                d.drawRect(c, Offset(hx - hr * 1.0f, hy - hr * 0.55f), Size(hr * 2f, hr * 0.28f))
                d.drawLine(c, Offset(hx - hr * 0.95f, hy - hr * 0.42f), Offset(hx - hr * 1.8f, hy - hr * 0.1f), hr * 0.2f, StrokeCap.Round)
                d.drawLine(c, Offset(hx - hr * 0.95f, hy - hr * 0.42f), Offset(hx - hr * 1.7f, hy + hr * 0.25f), hr * 0.18f, StrokeCap.Round)
                // Ninja mask
                d.drawArc(Color(0xFF0B0F1A), 0f, 180f, true, Offset(hx - hr, hy - hr * 0.95f), Size(hr * 2f, hr * 1.9f))
            }
            Hat.PIRATE -> {
                path2.reset()
                path2.moveTo(hx - hr * 1.5f, hy - hr * 0.55f)
                path2.quadraticTo(hx, hy - hr * 2.4f, hx + hr * 1.5f, hy - hr * 0.55f)
                path2.quadraticTo(hx, hy - hr * 0.95f, hx - hr * 1.5f, hy - hr * 0.55f)
                path2.close()
                d.drawPath(path2, c)
                d.drawCircle(Color.White, hr * 0.16f, Offset(hx, hy - hr * 1.15f))
                // Eye patch
                d.drawCircle(Color(0xFF111827), hr * 0.2f, Offset(hx + hr * 0.5f, hy - hr * 0.1f))
                d.drawLine(Color(0xFF111827), Offset(hx - hr * 0.9f, hy - hr * 0.55f), Offset(hx + hr * 0.75f, hy + hr * 0.1f), hr * 0.07f)
            }
            Hat.WIZARD -> {
                path2.reset()
                path2.moveTo(hx - hr * 0.95f, hy - hr * 0.5f)
                path2.lineTo(hx - hr * 0.6f, hy - hr * 2.6f)
                path2.lineTo(hx + hr * 0.95f, hy - hr * 0.5f)
                path2.close()
                d.drawPath(path2, c)
                d.drawOval(Palette.darker(c, 0.25f), Offset(hx - hr * 1.5f, hy - hr * 0.7f), Size(hr * 3f, hr * 0.42f))
                d.drawCircle(Color(0xFFFDE047), hr * 0.14f, Offset(hx - hr * 0.1f, hy - hr * 1.4f))
            }
            Hat.COWBOY -> {
                d.drawOval(c, Offset(hx - hr * 1.7f, hy - hr * 0.78f), Size(hr * 3.4f, hr * 0.42f))
                d.drawRoundRect(c, Offset(hx - hr * 0.8f, hy - hr * 1.55f), Size(hr * 1.6f, hr * 0.95f), androidx.compose.ui.geometry.CornerRadius(hr * 0.35f))
                d.drawRect(Palette.darker(c, 0.4f), Offset(hx - hr * 0.8f, hy - hr * 0.86f), Size(hr * 1.6f, hr * 0.16f))
            }
            Hat.SAMURAI -> {
                d.drawArc(c, 180f, 180f, true, Offset(hx - hr * 1.08f, hy - hr * 1.1f), Size(hr * 2.16f, hr * 1.8f))
                d.drawRect(c, Offset(hx - hr * 1.4f, hy - hr * 0.3f), Size(hr * 0.6f, hr * 0.5f))
                d.drawArc(Color(0xFFFACC15), 200f, 140f, false, Offset(hx - hr * 0.9f, hy - hr * 2.1f), Size(hr * 1.8f, hr * 1.6f), style = Stroke(hr * 0.16f, cap = StrokeCap.Round))
            }
            Hat.VISOR -> {
                d.drawRoundRect(c, Offset(hx - hr * 0.2f, hy - hr * 0.35f), Size(hr * 1.15f, hr * 0.4f), androidx.compose.ui.geometry.CornerRadius(hr * 0.2f))
                d.drawLine(Color(0xFF94A3B8), Offset(hx - hr * 0.2f, hy - hr * 0.95f), Offset(hx - hr * 0.35f, hy - hr * 1.6f), hr * 0.08f)
                d.drawCircle(c, hr * 0.14f, Offset(hx - hr * 0.35f, hy - hr * 1.62f))
            }
            Hat.HALO -> {
                d.drawOval(c, Offset(hx - hr * 0.85f, hy - hr * 1.65f), Size(hr * 1.7f, hr * 0.42f), style = Stroke(hr * 0.16f))
            }
            Hat.SKULL -> Unit
            else -> Unit
        }
    }

    fun drawBow(d: DrawScope, r: Ragdoll, b: Bow, nock: Boolean, clock: Float) {
        val hxW = r.x[Ragdoll.R_HAND]; val hyW = r.y[Ragdoll.R_HAND]
        var dx = hxW - r.x[Ragdoll.R_ELBOW]; var dy = hyW - r.y[Ragdoll.R_ELBOW]
        val l = sqrt(dx * dx + dy * dy)
        if (l < 1e-4f) { dx = r.facing; dy = 0f } else { dx /= l; dy /= l }
        // Screen directions (y down)
        val sdx = dx; val sdy = -dy
        val pxv = -sdy; val pyv = sdx
        val hx = sx(hxW); val hy = sy(hyW)
        val half = 0.62f * k
        val back = 0.2f * k
        val t1x = hx + pxv * half - sdx * back; val t1y = hy + pyv * half - sdy * back
        val t2x = hx - pxv * half - sdx * back; val t2y = hy - pyv * half - sdy * back
        val cX = hx + sdx * 0.34f * k; val cY = hy + sdy * 0.34f * k
        path.reset()
        path.moveTo(t1x, t1y)
        path.quadraticTo(cX, cY, t2x, t2y)
        val wood = rgb(b.wood)
        val acc = rgb(b.accent)
        // String point
        val drawing = r.alive && r.draw > 0.02f
        val spx = if (drawing) sx(r.x[Ragdoll.L_HAND]) else (t1x + t2x) / 2f
        val spy = if (drawing) sy(r.y[Ragdoll.L_HAND]) else (t1y + t2y) / 2f
        val stringC = if (b.style == BowStyle.LASER) acc else Color(0xFFF1F5F9)
        if (b.style == BowStyle.LASER) {
            d.drawLine(acc.copy(alpha = 0.35f), Offset(t1x, t1y), Offset(spx, spy), 0.08f * k, StrokeCap.Round)
            d.drawLine(acc.copy(alpha = 0.35f), Offset(t2x, t2y), Offset(spx, spy), 0.08f * k, StrokeCap.Round)
        }
        d.drawLine(stringC, Offset(t1x, t1y), Offset(spx, spy), max(1f, 0.02f * k))
        d.drawLine(stringC, Offset(t2x, t2y), Offset(spx, spy), max(1f, 0.02f * k))
        // Nocked arrow
        if (nock) {
            val ax = (spx + sdx * 0.35f * k)
            val ay = (spy + sdy * 0.35f * k)
            val ang = atan2(-sdy, sdx)
            drawArrowScreen(d, ax, ay, ang, true, b, 1f)
        }
        // Limbs
        when (b.style) {
            BowStyle.NEON, BowStyle.FIRE, BowStyle.LASER -> {
                val pulse = if (b.style == BowStyle.FIRE) 0.3f + 0.2f * sin(clock * 11f) else 0.35f
                d.drawPath(path, acc.copy(alpha = pulse), style = glowStroke)
            }
            else -> Unit
        }
        d.drawPath(path, wood, style = bowStroke)
        when (b.style) {
            BowStyle.CRYSTAL, BowStyle.NEON -> d.drawPath(path, Color.White.copy(alpha = 0.7f), style = thinStroke)
            BowStyle.COMPOUND -> {
                d.drawCircle(acc, 0.07f * k, Offset(t1x, t1y))
                d.drawCircle(acc, 0.07f * k, Offset(t2x, t2y))
                d.drawLine(acc.copy(alpha = 0.7f), Offset(t1x, t1y), Offset(t2x + sdx * 0.05f * k, t2y + sdy * 0.05f * k), max(1f, 0.015f * k))
            }
            BowStyle.RECURVE, BowStyle.DRAGON -> {
                d.drawLine(acc, Offset(t1x, t1y), Offset(t1x + sdx * 0.1f * k + pxv * 0.05f * k, t1y + sdy * 0.1f * k + pyv * 0.05f * k), 0.05f * k, StrokeCap.Round)
                d.drawLine(acc, Offset(t2x, t2y), Offset(t2x + sdx * 0.1f * k - pxv * 0.05f * k, t2y + sdy * 0.1f * k - pyv * 0.05f * k), 0.05f * k, StrokeCap.Round)
                if (b.style == BowStyle.DRAGON) d.drawCircle(acc, 0.06f * k, Offset(cX - sdx * 0.12f * k, cY - sdy * 0.12f * k))
            }
            else -> Unit
        }
        // Grip
        d.drawLine(acc, Offset(hx + pxv * 0.08f * k, hy + pyv * 0.08f * k), Offset(hx - pxv * 0.08f * k, hy - pyv * 0.08f * k), 0.08f * k, StrokeCap.Round)
    }

    // ------------------------------------------------------------------ arrows

    private fun drawArrow(d: DrawScope, x: Float, y: Float, angle: Float, byPlayer: Boolean, b: Bow, alpha: Float) {
        drawArrowScreen(d, sx(x), sy(y), angle, byPlayer, b, alpha)
    }

    /** Arrow centred at screen (x, y), world angle [angle] (up positive). */
    fun drawArrowScreen(d: DrawScope, x: Float, y: Float, angle: Float, byPlayer: Boolean, b: Bow, alpha: Float) {
        val dx = cos(angle); val dy = -sin(angle)
        val px = -dy; val py = dx
        val half = 0.36f * k
        val tipX = x + dx * half; val tipY = y + dy * half
        val tailX = x - dx * half; val tailY = y - dy * half
        if (max(tipX, tailX) < -50f || min(tipX, tailX) > d.size.width + 50f) return
        d.drawLine(Color(0xFF8B5A2B).copy(alpha = alpha), Offset(tailX, tailY), Offset(tipX, tipY), max(1.5f, 0.04f * k), StrokeCap.Round)
        // Head
        path.reset()
        path.moveTo(tipX + dx * 0.11f * k, tipY + dy * 0.11f * k)
        path.lineTo(tipX + px * 0.055f * k, tipY + py * 0.055f * k)
        path.lineTo(tipX - px * 0.055f * k, tipY - py * 0.055f * k)
        path.close()
        d.drawPath(path, Color(0xFFD1D5DB).copy(alpha = alpha))
        // Fletching
        val fc = if (byPlayer) rgb(b.trail) else Color(0xFFEF4444)
        for (s in -1..1 step 2) {
            path.reset()
            path.moveTo(tailX + dx * 0.02f * k, tailY + dy * 0.02f * k)
            path.lineTo(tailX + dx * 0.15f * k, tailY + dy * 0.15f * k)
            path.lineTo(tailX + dx * 0.05f * k + px * s * 0.07f * k, tailY + dy * 0.05f * k + py * s * 0.07f * k)
            path.lineTo(tailX - dx * 0.04f * k + px * s * 0.07f * k, tailY - dy * 0.04f * k + py * s * 0.07f * k)
            path.close()
            d.drawPath(path, fc.copy(alpha = alpha))
        }
    }

    /** Shop preview: an arrow in flight with its trail ([reset] starts a new trail). */
    fun drawFlyingArrow(d: DrawScope, x: Float, y: Float, angle: Float, b: Bow, trailStyle: Int, clock: Float, reset: Boolean) {
        if (reset) trailN = 0
        pushTrail(x, y)
        drawTrail(d, trailStyle, b.trail, clock)
        drawArrow(d, x, y, angle, true, b, 1f)
    }

    fun trailPoint(x: Float, y: Float, reset: Boolean) {
        if (reset) trailN = 0
        pushTrail(x, y)
    }

    private fun pushTrail(x: Float, y: Float) {
        if (trailN > 0) {
            val lx = trailX[trailN - 1]; val ly = trailY[trailN - 1]
            if ((x - lx) * (x - lx) + (y - ly) * (y - ly) < 0.09f) return
        }
        if (trailN == TRAIL) {
            System.arraycopy(trailX, 1, trailX, 0, TRAIL - 1)
            System.arraycopy(trailY, 1, trailY, 0, TRAIL - 1)
            trailN--
        }
        trailX[trailN] = x; trailY[trailN] = y; trailN++
    }

    private fun drawTrail(d: DrawScope, style: Int, color: Long, clock: Float) {
        if (trailN < 2) return
        val base = when (style) {
            Trails.BLUE_FIRE -> Color(0xFF4D9BFF)
            Trails.GOLD -> Color(0xFFFFD34D)
            Trails.GHOST -> Color.White
            Trails.NEON -> Color(0xFFF0ABFC)
            else -> rgb(color)
        }
        for (i in 1 until trailN) {
            val f = i / trailN.toFloat()
            val c = if (style == Trails.RAINBOW) Color.hsv(((i * 24f + clock * 200f) % 360f + 360f) % 360f, 0.8f, 1f) else base
            val a = when (style) { Trails.GHOST -> 0.22f; Trails.NEON -> 0.75f; else -> 0.5f } * f
            val wpx = (when (style) { Trails.GHOST, Trails.NEON -> 0.14f; Trails.BLUE_FIRE -> 0.12f; else -> 0.08f }) * k * (0.35f + 0.65f * f)
            d.drawLine(c.copy(alpha = a), Offset(sx(trailX[i - 1]), sy(trailY[i - 1])), Offset(sx(trailX[i]), sy(trailY[i])), wpx, StrokeCap.Round)
            if ((style == Trails.STARS || style == Trails.GOLD) && i % 3 == 0) {
                val j = sin(i * 12.9898f + clock * 3f) * 0.12f
                d.drawCircle(if (style == Trails.GOLD) Color(0xFFFFE680) else Color.White, 0.035f * k, Offset(sx(trailX[i] + j), sy(trailY[i] - j)), alpha = f)
            }
        }
    }

    // ------------------------------------------------------------------ HUD in the world

    private fun hpBar(d: DrawScope, r: Ragdoll, hp: Int, maxHp: Int, color: Color, myTurn: Boolean, clock: Float, shields: Int) {
        if (!r.alive && hp <= 0) return
        val bw = 1.05f * k
        val bh = 0.13f * k
        val x = sx(r.rootX) - bw / 2f
        val y = sy(r.rootY + 2.3f)
        if (x > d.size.width || x + bw < 0f) return
        d.drawRoundRect(Color(0x99000000), Offset(x - 2f, y - 2f), Size(bw + 4f, bh + 4f), androidx.compose.ui.geometry.CornerRadius(bh))
        val f = (hp / maxHp.toFloat()).coerceIn(0f, 1f)
        if (f > 0f) d.drawRoundRect(color, Offset(x, y), Size(max(bh, bw * f), bh), androidx.compose.ui.geometry.CornerRadius(bh / 2f))
        d.drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(x + bh * 0.3f, y + bh * 0.15f), Size(max(0f, bw * f - bh * 0.6f), bh * 0.25f), androidx.compose.ui.geometry.CornerRadius(bh / 2f))
        if (shields > 0) {
            d.drawCircle(Color(0xFF38BDF8), bh * 1.1f, Offset(x - bh * 1.6f, y + bh / 2f))
            d.drawCircle(Color.White.copy(alpha = 0.6f), bh * 0.5f, Offset(x - bh * 1.6f, y + bh / 2f))
        }
        if (myTurn) {
            val bob = sin(clock * 6f) * 0.06f * k
            path.reset()
            val tx = x + bw / 2f
            val ty = y - 0.12f * k + bob
            path.moveTo(tx - 0.1f * k, ty - 0.14f * k)
            path.lineTo(tx + 0.1f * k, ty - 0.14f * k)
            path.lineTo(tx, ty)
            path.close()
            d.drawPath(path, Color(0xFFFACC15))
        }
    }

    private fun offscreen(d: DrawScope, m: TextMeasurer, r: Ragdoll, w: Float, h: Float, color: Color, distance: Float) {
        val x = sx(r.x[Ragdoll.CHEST])
        val y = sy(r.y[Ragdoll.CHEST])
        val margin = 0.4f * k
        val right = x > w + margin
        val left = x < -margin
        if (!right && !left) return
        val rad = 0.32f * k
        val mx = if (right) w - rad * 1.6f else rad * 1.6f
        val my = y.coerceIn(h * 0.25f, h * 0.8f)
        d.drawCircle(Color(0xAA000000), rad, Offset(mx, my))
        d.drawCircle(color, rad, Offset(mx, my), style = thinStroke)
        val dir = if (right) 1f else -1f
        path.reset()
        path.moveTo(mx + dir * rad * 1.45f, my)
        path.lineTo(mx + dir * rad * 1.0f, my - rad * 0.35f)
        path.lineTo(mx + dir * rad * 1.0f, my + rad * 0.35f)
        path.close()
        d.drawPath(path, color)
        // Little head icon
        d.drawCircle(color, rad * 0.3f, Offset(mx, my - rad * 0.2f))
        d.drawArc(color, 180f, 180f, true, Offset(mx - rad * 0.5f, my + rad * 0.1f), Size(rad, rad * 0.8f))
        val di = Math.round(distance)
        if (di != distInt) { distInt = di; distStr = "$di m" }
        val t = layout(m, distStr, textStyleSmall)
        d.drawText(t, topLeft = Offset(mx - t.size.width / 2f, my + rad * 1.1f))
    }

    companion object {
        const val AIR = 40
        const val TRAIL = 22
        const val HEADSHOT = "HEADSHOT!"
        private fun frac(v: Float) = v - floor(v)
    }
}
