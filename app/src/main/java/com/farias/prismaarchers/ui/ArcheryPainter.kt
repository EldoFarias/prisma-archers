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
import com.farias.prismaarchers.data.GroundStyle
import com.farias.prismaarchers.data.Outfit
import com.farias.prismaarchers.data.TowerStyle
import com.farias.prismaarchers.data.Trails
import com.farias.prismaarchers.data.World
import com.farias.prismaarchers.game.Arrow
import com.farias.prismaarchers.game.ArrowKind
import com.farias.prismaarchers.game.Debris
import com.farias.prismaarchers.game.Prop
import com.farias.prismaarchers.game.Recorder
import com.farias.prismaarchers.game.Weather
import com.farias.prismaarchers.game.Duel
import com.farias.prismaarchers.game.GameHost
import com.farias.prismaarchers.game.Ragdoll
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
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
    private val texts = Array(7) { HashMap<String, TextLayoutResult>() }
    private var distInt = -1
    private var distStr = ""
    private val textStyleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 4f))
    private val textStyleDmg = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 5f))
    private val textStyleHead = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD34D), shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 6f))
    private val textStyleHeal = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF4ADE80), shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 5f))
    private val textStyleLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, shadow = Shadow(Color(0xDD000000), Offset(0f, 1.5f), 4f))
    private val textStyleBubble = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1226))
    private val textStyleBig = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF8A2B), shadow = Shadow(Color(0xDD000000), Offset(0f, 3f), 8f))

    private var strokeW = -1f
    private var bowStroke = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private var glowStroke = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private var thinStroke = Stroke(1f, cap = StrokeCap.Round)
    private var outlineStroke = Stroke(1f, join = StrokeJoin.Round)
    private var roundStroke = Stroke(1f, join = StrokeJoin.Round)
    private var gaugeStroke = Stroke(1f, cap = StrokeCap.Round)
    private var bowOutline = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    init {
        val rnd = Random(5)
        for (i in 0 until AIR) {
            airA[i] = rnd.nextFloat(); airB[i] = rnd.nextFloat(); airS[i] = 0.5f + rnd.nextFloat(); airV[i] = rnd.nextFloat()
        }
    }

    private fun layout(m: TextMeasurer, text: String, style: TextStyle): TextLayoutResult {
        val map = texts[when (style) { textStyleSmall -> 0; textStyleDmg -> 1; textStyleHead -> 2; textStyleBig -> 3; textStyleHeal -> 4; textStyleLabel -> 5; else -> 6 }]
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
        outlineStroke = Stroke(max(2.4f, OUTLINE * k * 2f) + 0.06f * k, join = StrokeJoin.Round)
        roundStroke = Stroke(0.06f * k, join = StrokeJoin.Round)
        gaugeStroke = Stroke(max(4f, 0.075f * k), cap = StrokeCap.Round)
        bowOutline = Stroke(0.065f * k + max(2.4f, OUTLINE * k * 2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        val weather = host.weather

        drawSky(d, w, h, g.clock, world, host.windKmh)
        if (weather == Weather.STORM || weather == Weather.RAIN) d.drawRect(if (weather == Weather.STORM) Color(0x552A3348) else Color(0x33202A40))
        if (host.lightning > 0.02f) d.drawRect(Color(0xFFE0E7FF), alpha = (host.lightning * 0.35f).coerceAtMost(1f))
        if (host.lightning > 0.45f) bolt(d, w, h, host.boltX)
        // Birds and balloons
        for (p in g.props) drawProp(d, p, g.clock)
        // Platforms with their flags (and torches at night)
        val torches = world.scenery == 3 || world.base == TowerStyle.CASTLE
        drawGround(d, duel.groundY, world, g.clock)
        for (i in 0..1) {
            val p = duel.platforms[i]
            drawPlatform(d, p.x0, p.x1, p.top, duel.groundY, world, g.clock)
            val fx = if (i == 0) p.x0 + 0.55f else p.x1 - 0.55f
            flag(d, fx, p.top, duel.spec.wind, g.clock, if (i == 0) Color(0xFF34D399) else Color(0xFFF43F5E))
            if (torches) torch(d, if (i == 0) p.x1 - 0.25f else p.x0 + 0.25f, p.top, g.clock + i * 1.7f)
        }
        // Arrows in the ground / bodies
        for (a in duel.arrows) {
            if (a.flying) continue
            var ang = a.angle
            var axw = a.x
            var ayw = a.y
            if (a.stuckIn == null && a.stuckTime >= 0f) {
                // Arrows that hit the ground quiver for a moment around their tip.
                val t = duel.time - a.stuckTime
                if (t < 0.9f) {
                    val tipX = a.x + cos(a.angle) * 0.36f
                    val tipY = a.y + sin(a.angle) * 0.36f
                    ang += sin(t * 48f) * exp(-t * 6f) * 0.14f
                    axw = tipX - cos(ang) * 0.36f
                    ayw = tipY - sin(ang) * 0.36f
                }
            }
            drawArrow(d, axw, ayw, ang, a.byPlayer, if (a.byPlayer) host.playerBow else host.enemyBow, 1f, a.kind, g.clock)
        }
        // Archers
        drawArcher(d, duel.enemy, host.enemyOutfit, host.enemyBow, duel.helmetOn, duel.turn == Duel.Turn.ENEMY && duel.enemy.alive, g.clock)
        if (duel.enemyFrozen) iceBlock(d, duel.enemy, g.clock)
        if (duel.bossShield > 0 && duel.enemy.alive) shieldBubble(d, duel.enemy, duel.bossShield, g.clock)
        drawArcher(d, duel.player, host.playerOutfit, host.playerBow, false, duel.turn == Duel.Turn.PLAYER && duel.player.alive, g.clock, host.selectedArrow)
        // Arrows in the air (+ trail of the lead one)
        val fl = duel.flying
        if (fl != null) {
            if (fl !== trailOwner) { trailOwner = fl; trailN = 0 }
            pushTrail(fl.x, fl.y)
            val style = if (!fl.byPlayer) Trails.CLASSIC else if (fl.kind == ArrowKind.FIRE) FIRE_TRAIL else host.trailStyle
            drawTrail(d, style, if (fl.byPlayer) host.playerBow.trail else 0xFF9A9A, g.clock)
        } else if (trailN > 0) {
            trailN = max(0, trailN - 2)
            drawTrail(d, host.trailStyle, host.playerBow.trail, g.clock)
        }
        for (a in duel.inFlight) drawArrow(d, a.x, a.y, a.angle, a.byPlayer, if (a.byPlayer) host.playerBow else host.enemyBow, 1f, a.kind, g.clock)
        // Helmet knocked off
        if (g.helmet.active) helmetDebris(d, g.helmet)
        // Whatever fell into the water/lava is under its surface
        liquidFront(d, duel.groundY, world, g.clock)
        // Aim guide
        if (g.aiming && g.aimPower > 0.04f && duel.turn == Duel.Turn.PLAYER) {
            val n = g.guide(guide)
            val dotK = max(k, w / 22f)
            for (i in 0 until n) {
                val f = 1f - i / n.toFloat()
                val o = Offset(sx(guide[i * 2]), sy(guide[i * 2 + 1]))
                val rr = (0.035f + 0.03f * f) * dotK
                d.drawCircle(OUTLINE_C.copy(alpha = 0.45f * f), rr + 2f, o)
                d.drawCircle(Color.White.copy(alpha = 0.9f * f), rr, o)
            }
            val pl = duel.player
            // Power gauge: an arc around the archer filling green → yellow → red
            val gc = Offset(sx(pl.x[Ragdoll.CHEST]), sy(pl.y[Ragdoll.CHEST]))
            val gr = max(1.15f * k, w * 0.12f)
            val p = g.aimPower.coerceIn(0f, 1f)
            val gcol = if (p < 0.5f) androidx.compose.ui.graphics.lerp(Color(0xFF34D399), Color(0xFFFACC15), p * 2f)
            else androidx.compose.ui.graphics.lerp(Color(0xFFFACC15), Color(0xFFF43F5E), (p - 0.5f) * 2f)
            d.drawArc(Color(0x55000000), 150f, 240f, false, Offset(gc.x - gr, gc.y - gr), Size(gr * 2f, gr * 2f), style = gaugeStroke)
            if (p > 0.01f) d.drawArc(gcol, 150f, 240f * p, false, Offset(gc.x - gr, gc.y - gr), Size(gr * 2f, gr * 2f), style = gaugeStroke)
            val deg = Math.round(Math.toDegrees(g.aimAngle.toDouble())).toInt()
            val pct = Math.round(g.aimPower * 100f)
            val t = layout(m, "$deg°  $pct%", textStyleSmall)
            d.drawText(t, topLeft = Offset(sx(pl.rootX) - t.size.width / 2f, sy(pl.rootY + 2.55f) - t.size.height - gr * 0.15f))
        }
        // Particles
        val pt = g.particles
        for (i in 0 until pt.count) {
            val a = (pt.life[i] / pt.maxLife[i]).coerceIn(0f, 1f)
            val r = pt.size[i] * k
            if (r < 0.5f) continue
            d.drawCircle(Color(pt.r[i], pt.g[i], pt.b[i], a), r, Offset(sx(pt.x[i]), sy(pt.y[i])))
        }
        // Rain and fog in front
        if (weather == Weather.RAIN || weather == Weather.STORM) rain(d, w, h, g.clock, host.windKmh, weather == Weather.STORM)
        if (weather == Weather.FOG) fog(d, w, h, g.clock, world)
        // HP bars (with the lost chunk draining behind) and the enemy's name
        val dtc = (g.clock - lastClock).coerceIn(0f, 0.1f)
        lastClock = g.clock
        if (ghostDuel !== duel) {
            ghostDuel = duel
            ghostP = duel.playerHp.toFloat()
            ghostE = duel.enemyHp.toFloat()
        }
        ghostP = drain(ghostP, duel.playerHp.toFloat(), dtc)
        ghostE = drain(ghostE, duel.enemyHp.toFloat(), dtc)
        hpBar(d, duel.player, duel.playerHp, duel.maxPlayerHp, ghostP, Color(0xFF34D399), duel.turn == Duel.Turn.PLAYER, g.clock, host.shields)
        val ey = hpBar(d, duel.enemy, duel.enemyHp, duel.maxEnemyHp, ghostE, Color(0xFFF43F5E), false, g.clock, 0)
        if (ey != null && host.enemyLabel.isNotEmpty() && host.phase != GameHost.Phase.MENU) {
            val lay = layout(m, host.enemyLabel, textStyleLabel)
            d.drawText(lay, topLeft = Offset(sx(duel.enemy.rootX) - lay.size.width / 2f, ey - lay.size.height - 3f))
        }
        // Speech bubble
        val ba = g.clock - host.bubbleAt
        if (host.bubbleText.isNotEmpty() && ba in 0f..2.4f && host.phase == GameHost.Phase.PLAYING) {
            val who = if (host.bubbleEnemy) duel.enemy else duel.player
            if (who.alive) bubble(d, m, host.bubbleText, who, ba, w)
        }
        // Floating texts
        for (t in g.texts) {
            val age = g.clock - t.born
            val style = when (t.kind) { 1 -> textStyleHead; 2 -> textStyleBig; 3 -> textStyleHeal; else -> textStyleDmg }
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
        // Slow motion vignette, flashes and the kill cam's cinema bars
        if (g.slow > 0.02f) d.drawRect(vignette, alpha = (g.slow * 0.75f).coerceAtMost(1f))
        if (g.flash > 0.02f) d.drawRect(Color.White, alpha = (g.flash * 0.45f).coerceAtMost(1f))
        if (g.blastFlash > 0.02f) d.drawRect(Color(0xFFFF8A2B), alpha = (g.blastFlash * 0.35f).coerceAtMost(1f))
        if (g.killCam > 0.01f) {
            val bar = g.killCam * h * 0.11f
            d.drawRect(Color.Black, Offset.Zero, Size(w, bar))
            d.drawRect(Color.Black, Offset(0f, h - bar), Size(w, bar))
        }
    }

    // Lost-HP chunk that drains after a short pause
    private var ghostDuel: Duel? = null
    private var ghostP = 100f
    private var ghostE = 100f
    private var lastClock = 0f

    private fun drain(ghost: Float, cur: Float, dt: Float): Float {
        if (ghost <= cur) return cur
        return max(cur, ghost - max(8f, (ghost - cur) * 2.2f) * dt)
    }

    // ------------------------------------------------------------------ replay

    private var replayP: Ragdoll? = null
    private var replayE: Ragdoll? = null

    /** Draws frame [frame] of the recorded killing blow. */
    fun drawReplay(d: DrawScope, host: GameHost, frame: Int, clock: Float) {
        val w = d.size.width
        val h = d.size.height
        if (w < 2f || h < 2f) return
        val rec = host.game.recorder
        val i = frame % Recorder.CAP
        val world = host.world
        val vw = rec.cam[i * 3 + 2].coerceAtLeast(3f)
        setTransform(rec.cam[i * 3], rec.cam[i * 3 + 1], w / vw, w / 2f, h * 0.6f)
        if (world !== sceneWorld || w != sceneW || h != sceneH) buildScene(world, w, h)
        drawSky(d, w, h, clock, world, host.windKmh)
        val rd = host.game.duel
        drawGround(d, rd.groundY, world, clock)
        for (q in 0..1) drawPlatform(d, rd.platforms[q].x0, rd.platforms[q].x1, rd.platforms[q].top, rd.groundY, world, clock)
        val rp = replayP ?: Ragdoll(0f, 0f, 1f).also { replayP = it }
        val re = replayE ?: Ragdoll(0f, 0f, -1f).also { replayE = it }
        System.arraycopy(rec.pX, i * Ragdoll.N, rp.x, 0, Ragdoll.N)
        System.arraycopy(rec.pY, i * Ragdoll.N, rp.y, 0, Ragdoll.N)
        System.arraycopy(rec.eX, i * Ragdoll.N, re.x, 0, Ragdoll.N)
        System.arraycopy(rec.eY, i * Ragdoll.N, re.y, 0, Ragdoll.N)
        rp.alive = rec.alive[i * 2]
        re.alive = rec.alive[i * 2 + 1]
        rp.rootX = 0f; rp.rootY = 0f
        drawArcher(d, re, host.enemyOutfit, host.enemyBow, rec.helmetOn[i], false, clock)
        drawArcher(d, rp, host.playerOutfit, host.playerBow, false, false, clock)
        for (j in 0 until rec.aCount[i]) {
            val q = i * Recorder.ARROWS + j
            val kind = rec.aKind[q]
            drawArrow(d, rec.ax[q], rec.ay[q], rec.aa[q], kind >= 0, if (kind >= 0) host.playerBow else host.enemyBow, 1f, max(0, kind), clock)
        }
        liquidFront(d, rd.groundY, world, clock)
    }

    // ------------------------------------------------------------------ battle extras

    private fun drawProp(d: DrawScope, p: Prop, clock: Float) {
        val x = sx(p.x)
        val y = sy(p.y)
        if (x < -100f || x > d.size.width + 100f) return
        val s = 0.3f * k
        if (p.kind == Prop.BIRD) {
            val dir = if (p.vx >= 0f) 1f else -1f
            d.rotate(if (p.alive) 0f else p.spin * 57f, Offset(x, y)) {
                val flap = if (p.alive) sin(clock * 14f + p.phase) else -0.3f
                // Wings
                drawLine(OUTLINE_C, Offset(x, y), Offset(x - s * 0.9f * dir, y - s * (0.2f + flap * 0.7f)), s * 0.26f, StrokeCap.Round)
                drawLine(Color(0xFF475569), Offset(x, y), Offset(x - s * 0.9f * dir, y - s * (0.2f + flap * 0.7f)), s * 0.16f, StrokeCap.Round)
                // Body and head
                drawOval(OUTLINE_C, Offset(x - s * 0.62f, y - s * 0.3f), Size(s * 1.24f, s * 0.6f))
                drawOval(Color(0xFF64748B), Offset(x - s * 0.55f, y - s * 0.24f), Size(s * 1.1f, s * 0.48f))
                drawCircle(OUTLINE_C, s * 0.28f, Offset(x + s * 0.55f * dir, y - s * 0.18f))
                drawCircle(Color(0xFF64748B), s * 0.22f, Offset(x + s * 0.55f * dir, y - s * 0.18f))
                drawLine(Color(0xFFF59E0B), Offset(x + s * 0.72f * dir, y - s * 0.16f), Offset(x + s * 0.98f * dir, y - s * 0.1f), s * 0.12f, StrokeCap.Round)
                if (p.alive) drawCircle(Color.White, s * 0.07f, Offset(x + s * 0.62f * dir, y - s * 0.24f))
                drawLine(OUTLINE_C, Offset(x, y), Offset(x - s * 0.5f * dir, y - s * (0.1f - flap * 0.5f)), s * 0.22f, StrokeCap.Round)
                drawLine(Color(0xFF94A3B8), Offset(x, y), Offset(x - s * 0.5f * dir, y - s * (0.1f - flap * 0.5f)), s * 0.13f, StrokeCap.Round)
            }
        } else {
            val c = BALLOONS[((p.phase * 10).toInt() % BALLOONS.size + BALLOONS.size) % BALLOONS.size]
            val by = y - s * 0.5f
            d.drawLine(Color(0x99FFFFFF), Offset(x, by + s * 1.1f), Offset(x + sin(clock * 2f + p.phase) * s * 0.3f, by + s * 3f), max(1f, 0.015f * k))
            d.drawOval(OUTLINE_C, Offset(x - s * 0.85f, by - s * 1.05f), Size(s * 1.7f, s * 2.15f))
            d.drawOval(c, Offset(x - s * 0.78f, by - s), Size(s * 1.56f, s * 2f))
            d.drawOval(Color.White.copy(alpha = 0.45f), Offset(x - s * 0.5f, by - s * 0.75f), Size(s * 0.4f, s * 0.7f))
            path.reset()
            path.moveTo(x - s * 0.15f, by + s * 1.08f)
            path.lineTo(x + s * 0.15f, by + s * 1.08f)
            path.lineTo(x, by + s * 0.9f)
            path.close()
            d.drawPath(path, Palette.darker(c, 0.2f))
        }
    }

    /** Pole with a pennant that streams with the wind (hangs when there is none). */
    private fun flag(d: DrawScope, x: Float, top: Float, wind: Float, clock: Float, c: Color) {
        val px = sx(x)
        val base = sy(top)
        val tp = sy(top + 2.4f)
        if (px < -0.5f * d.size.width || px > d.size.width * 1.5f) return
        d.drawLine(OUTLINE_C, Offset(px, base), Offset(px, tp), max(2f, 0.07f * k), StrokeCap.Round)
        d.drawLine(Color(0xFF8B5A2B), Offset(px, base), Offset(px, tp), max(1.2f, 0.04f * k), StrokeCap.Round)
        d.drawCircle(Color(0xFFFACC15), max(2f, 0.05f * k), Offset(px, tp))
        val strength = (abs(wind) / 3f).coerceIn(0f, 1f)
        val dir = if (wind >= 0f) 1f else -1f
        // 0 = hanging straight down, 1 = flying straight out
        val ang = (-1.35f + 1.3f * strength)
        // Flag direction on screen and the perpendicular its width extends along
        val ux = cos(ang) * dir
        val uy = -sin(ang)
        val nx = -uy * dir
        val ny = ux * dir
        val len = 0.8f * k
        val wid = 0.42f * k
        val ox = px
        val oy = tp + 0.03f * k
        val speed = 5f + 6f * strength
        path2.reset()
        path2.moveTo(ox, oy)
        for (i in 1..6) {
            val u = i / 6f
            val wave = sin(clock * speed - u * 4f) * 0.09f * k * u
            path2.lineTo(ox + ux * len * u + nx * wave, oy + uy * len * u + ny * wave)
        }
        for (i in 6 downTo 0) {
            val u = i / 6f
            val wave = sin(clock * speed - u * 4f) * 0.09f * k * u
            val wd = wid * (1f - u * 0.75f)
            path2.lineTo(ox + ux * len * u + nx * (wave + wd), oy + uy * len * u + ny * (wave + wd))
        }
        path2.close()
        d.drawPath(path2, OUTLINE_C, style = thinStroke)
        d.drawPath(path2, c)
    }

    private fun torch(d: DrawScope, x: Float, top: Float, clock: Float) {
        val px = sx(x)
        if (px < -100f || px > d.size.width + 100f) return
        val base = sy(top)
        val tp = sy(top + 1.5f)
        val f = 0.85f + 0.15f * sin(clock * 17f) + 0.08f * sin(clock * 31f)
        // Warm glow
        for (r in 0..2) d.drawCircle(Color(0xFFFFA94D).copy(alpha = 0.07f), (1.6f - r * 0.45f) * k * f, Offset(px, tp - 0.15f * k))
        d.drawLine(OUTLINE_C, Offset(px, base), Offset(px, tp), max(2f, 0.09f * k), StrokeCap.Round)
        d.drawLine(Color(0xFF6B4423), Offset(px, base), Offset(px, tp), max(1.2f, 0.06f * k), StrokeCap.Round)
        d.drawRect(Color(0xFF3F3F46), Offset(px - 0.09f * k, tp - 0.05f * k), Size(0.18f * k, 0.1f * k))
        d.drawOval(Color(0xFFF97316), Offset(px - 0.12f * k * f, tp - 0.42f * k * f), Size(0.24f * k * f, 0.4f * k * f))
        d.drawOval(Color(0xFFFDE047), Offset(px - 0.07f * k * f, tp - 0.3f * k * f), Size(0.14f * k * f, 0.26f * k * f))
    }

    private fun rain(d: DrawScope, w: Float, h: Float, clock: Float, windKmh: Int, storm: Boolean) {
        val n = if (storm) 80 else 55
        val slant = windKmh / 25f
        val len = h * 0.035f
        for (i in 0 until n) {
            val a = airA[i % AIR] + i * 0.618f
            val b = airB[i % AIR] + i * 0.37f
            val sp = 1.6f + airS[i % AIR] * 0.6f
            val x = frac(a + clock * slant * 0.15f) * w * 1.1f - w * 0.05f
            val y = frac(b + clock * sp) * (h + len) - len
            d.drawLine(Color(0x66DCE6F5), Offset(x, y), Offset(x + slant * len * 0.4f, y + len), max(1f, w * 0.0018f))
        }
    }

    private fun fog(d: DrawScope, w: Float, h: Float, clock: Float, world: World) {
        val c = Palette.lighter(rgb(world.skyBottom), 0.4f)
        for (i in 0 until 6) {
            val r = w * (0.35f + airS[i] * 0.2f)
            val x = frac(airA[i] + clock * 0.012f * (0.5f + airS[i])) * (w + 2f * r) - r
            val y = h * (0.55f + airB[i] * 0.4f)
            d.drawOval(c.copy(alpha = 0.16f), Offset(x - r, y - r * 0.3f), Size(r * 2f, r * 0.6f))
        }
        d.drawRect(c.copy(alpha = 0.14f), Offset(0f, h * 0.5f), Size(w, h * 0.5f))
    }

    private fun bolt(d: DrawScope, w: Float, h: Float, bx: Float) {
        path2.reset()
        var x = bx * w
        var y = 0f
        path2.moveTo(x, y)
        var s = (bx * 1000f).toInt()
        while (y < h * 0.5f) {
            s = (s * 1103515245 + 12345) and 0x7fffffff
            x += ((s % 1000) / 1000f - 0.5f) * w * 0.12f
            y += h * 0.05f
            path2.lineTo(x, y)
        }
        d.drawPath(path2, Color.White.copy(alpha = 0.35f), style = glowStroke)
        d.drawPath(path2, Color.White, style = thinStroke)
    }

    /** The knight's magic bubble, cracking after the first hit. */
    private fun shieldBubble(d: DrawScope, r: Ragdoll, left: Int, clock: Float) {
        val c = Offset(sx(r.x[Ragdoll.CHEST]), sy(r.y[Ragdoll.CHEST] + 0.1f))
        val rad = Duel.SHIELD_R * k
        val pulse = 1f + sin(clock * 3f) * 0.02f
        d.drawCircle(Color(0xFF38BDF8).copy(alpha = 0.14f), rad * pulse, c)
        d.drawCircle(Color(0xFF7DD3FC).copy(alpha = 0.75f), rad * pulse, c, style = thinStroke)
        d.drawArc(Color.White.copy(alpha = 0.5f), (clock * 40f) % 360f, 50f, false, Offset(c.x - rad * 0.85f, c.y - rad * 0.85f), Size(rad * 1.7f, rad * 1.7f), style = thinStroke)
        if (left == 1) {
            // Cracks spreading from where it was hit
            for (j in 0 until 5) {
                path2.reset()
                val a0 = -0.6f + j * 0.35f
                var px = c.x + cos(a0) * rad * 0.95f * -r.facing
                var py = c.y - sin(a0) * rad * 0.95f
                path2.moveTo(px, py)
                for (q in 1..3) {
                    px -= cos(a0 + q * 0.5f * (if (j % 2 == 0) 1f else -1f)) * rad * 0.16f * -r.facing
                    py += sin(a0 + q * 0.4f) * rad * 0.12f
                    path2.lineTo(px, py)
                }
                d.drawPath(path2, Color.White.copy(alpha = 0.85f), style = thinStroke)
            }
        }
    }

    /** Frozen enemy: a block of ice around the body. */
    private fun iceBlock(d: DrawScope, r: Ragdoll, clock: Float) {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until Ragdoll.N) {
            x0 = min(x0, r.x[i]); x1 = max(x1, r.x[i]); y0 = min(y0, r.y[i]); y1 = max(y1, r.y[i])
        }
        val l = sx(x0 - 0.3f); val rr = sx(x1 + 0.3f); val t = sy(y1 + 0.35f); val b = sy(y0 - 0.05f)
        val cr = androidx.compose.ui.geometry.CornerRadius(0.12f * k)
        d.drawRoundRect(Color(0xFF67E8F9).copy(alpha = 0.38f), Offset(l, t), Size(rr - l, b - t), cr)
        d.drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(l, t), Size(rr - l, b - t), cr, style = thinStroke)
        val sh = (clock * 0.6f) % 1f
        d.drawLine(Color.White.copy(alpha = 0.55f), Offset(l + (rr - l) * (0.1f + sh * 0.5f), t + 4f), Offset(l + (rr - l) * (sh * 0.5f), b - 4f), max(2f, 0.05f * k))
        d.drawLine(Color.White.copy(alpha = 0.35f), Offset(l + (rr - l) * 0.8f, t + 6f), Offset(l + (rr - l) * 0.62f, t + (b - t) * 0.4f), max(1.5f, 0.03f * k))
    }

    private fun helmetDebris(d: DrawScope, hd: Debris) {
        val c = Offset(sx(hd.x), sy(hd.y))
        val hr = Ragdoll.HEAD_R * k * 1.2f
        d.rotate(hd.rot, c) {
            drawArc(OUTLINE_C, 175f, 190f, true, Offset(c.x - hr * 1.12f - outPx, c.y - hr * 1.15f - outPx), Size(hr * 2.24f + outPx * 2f, hr * 2f + outPx * 2f))
            drawArc(Color(0xFFCBD5E1), 175f, 190f, true, Offset(c.x - hr * 1.12f, c.y - hr * 1.15f), Size(hr * 2.24f, hr * 2f))
            drawRect(Color(0xFF64748B), Offset(c.x - hr * 1.1f, c.y - hr * 0.2f), Size(hr * 2.2f, hr * 0.18f))
        }
    }

    private fun bubble(d: DrawScope, m: TextMeasurer, text: String, r: Ragdoll, age: Float, w: Float) {
        val lay = layout(m, text, textStyleBubble)
        val pad = w * 0.02f
        val bw = lay.size.width + pad * 2f
        val bh = lay.size.height + pad * 1.2f
        val hx = sx(r.x[Ragdoll.HEAD])
        val hy = sy(r.y[Ragdoll.HEAD]) - Ragdoll.HEAD_R * k * 2.2f - max(0.6f * k, w * 0.075f)
        var left = hx - bw / 2f
        left = left.coerceIn(8f, w - bw - 8f)
        val top = hy - bh
        val pop = if (age < 0.15f) 0.6f + age / 0.15f * 0.4f else 1f
        val alpha = if (age > 2.1f) ((2.4f - age) / 0.3f).coerceIn(0f, 1f) else 1f
        d.scale(pop, pop, Offset(hx, hy)) {
            val cr = androidx.compose.ui.geometry.CornerRadius(bh * 0.45f)
            drawRoundRect(OUTLINE_C.copy(alpha = alpha), Offset(left - 2.5f, top - 2.5f), Size(bw + 5f, bh + 5f), cr)
            path.reset()
            path.moveTo(hx - pad * 0.6f, top + bh - 1f)
            path.lineTo(hx + pad * 0.6f, top + bh - 1f)
            path.lineTo(hx, top + bh + pad * 1.1f)
            path.close()
            drawPath(path, OUTLINE_C.copy(alpha = alpha), style = thinStroke)
            drawPath(path, Color.White.copy(alpha = alpha))
            drawRoundRect(Color.White.copy(alpha = alpha), Offset(left, top), Size(bw, bh), cr)
            drawText(lay, topLeft = Offset(left + pad, top + pad * 0.6f), alpha = alpha)
        }
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
        fun hash(i: Int) = (((i * 7919 + 13) % 100 + 100) % 100) / 100f
        fun far(u: Float): Float = when (sc) {
            6 -> w * (0.12f + 0.22f * hash(floor(u * 28f).toInt()))
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
            6 -> w * (0.05f + 0.15f * hash(floor(u * 18f).toInt() + 50))
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
        fill(farPath, tw, h, if (sc == 6) 336 else if (sc == 1 || sc == 3) 64 else 96, ::far)
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
        if (detailed) clouds(d, w, h, clock, world, windKmh)
        // Far layer
        val farY = h * 0.62f + (g0(camY) * k * 0.12f)
        if (detailed) {
            layer(d, farPath, farColor, camX * k * 0.1f, farY)
            // Haze between the layers
            d.drawRect(rgb(world.skyBottom, 0.28f), topLeft = Offset(0f, farY - w * 0.25f), size = Size(w, h))
        }
        val nearY = h * 0.72f + (g0(camY) * k * 0.3f)
        layer(d, nearPath, nearColor, camX * k * 0.28f, nearY)
        // A little atmospheric haze at the foot of the hills
        d.drawRect(rgb(world.skyBottom, 0.12f), topLeft = Offset(0f, nearY - w * 0.05f), size = Size(w, h))
        // Wind gusts: thin streaks blowing across the screen
        if (windKmh != 0) {
            val n = (abs(windKmh) / 3).coerceIn(3, 14)
            val dir = if (windKmh > 0) 1f else -1f
            val len = w * (0.05f + abs(windKmh) * 0.004f)
            for (i in 0 until n) {
                val sp = 0.25f + airS[i] * 0.25f + abs(windKmh) * 0.01f
                val u = frac(airA[i] + clock * sp * dir)
                val x = -len + u * (w + 2f * len)
                val y = h * (0.12f + airB[i] * 0.6f) + sin(clock * 2f + i) * 6f
                val a = 0.25f * sin(u * PI_F).coerceAtLeast(0f)
                d.drawLine(Color.White.copy(alpha = a), Offset(x, y), Offset(x - dir * len, y), max(1.5f, w * 0.0025f), StrokeCap.Round)
            }
        }
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

    /** Soft clouds made of a few overlapping circles, drifting slowly (faint wisps at night). */
    private fun clouds(d: DrawScope, w: Float, h: Float, clock: Float, world: World, windKmh: Int) {
        val night = world.air == 4 || world.scenery == 3
        val c = if (night) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f)
        val shade = if (night) Color.Transparent else rgb(world.skyBottom, 0.5f)
        val drift = 0.006f + windKmh * 0.0006f
        for (i in 0 until 5) {
            val s = w * (0.09f + airS[i] * 0.05f)
            val span = w + s * 6f
            val u = frac(airA[i + 7] + clock * drift * (0.6f + airS[i] * 0.5f) - camX * k * 0.04f / span)
            val x = -s * 3f + u * span
            val y = h * (0.07f + airB[i + 7] * 0.28f)
            d.drawOval(shade, Offset(x - s * 1.6f, y + s * 0.1f), Size(s * 3.4f, s * 0.9f))
            d.drawCircle(c, s * 0.75f, Offset(x - s * 0.9f, y + s * 0.15f))
            d.drawCircle(c, s, Offset(x, y - s * 0.1f))
            d.drawCircle(c, s * 0.8f, Offset(x + s * 0.95f, y + s * 0.1f))
            d.drawOval(c, Offset(x - s * 1.6f, y + s * 0.05f), Size(s * 3.4f, s * 0.75f))
        }
    }

    private fun layer(d: DrawScope, p: Path, color: Color, scroll: Float, baseY: Float) {
        var off = -(scroll % tileW)
        if (off > 0f) off -= tileW
        var x = off
        while (x < d.size.width) {
            d.translate(x, baseY) { drawPath(p, color) }
            x += tileW
        }
    }

    /** One archer's tower, from its top down to the ground, in the world's style, with its top cap. */
    private fun drawPlatform(d: DrawScope, x0: Float, x1: Float, top: Float, groundY: Float, world: World, clock: Float) {
        val l = sx(x0)
        val r = sx(x1)
        val t = sy(top)
        val h = d.size.height
        val bottom = min(h + 4f, sy(groundY) + 4f)
        val heightM = top - groundY
        val spread = when (world.base) { TowerStyle.MOUNTAIN -> 0.5f; TowerStyle.CLIFF -> 0.2f; TowerStyle.MESA -> 0.08f; else -> 0f } * heightM
        if (r + spread * k < 0f || l - spread * k > d.size.width || t > h) return
        val ol = max(2f, OUTLINE * k * 1.6f)
        when (world.base) {
            TowerStyle.BUILDING -> building(d, l, r, t, bottom, world, x0)
            TowerStyle.CLIFF, TowerStyle.MOUNTAIN, TowerStyle.MESA -> rockPillar(d, x0, x1, top, groundY, spread, world, ol)
            else -> stoneTower(d, l, r, t, bottom, world, ol)
        }
        // Top cap
        when (world.base) {
            TowerStyle.BUILDING -> {
                // Flat roof with a ledge, an antenna and a vent box
                d.drawRect(OUTLINE_C, Offset(l - 0.15f * k - ol, t - 0.1f * k - ol), Size(r - l + 0.3f * k + ol * 2f, 0.26f * k + ol * 2f))
                d.drawRect(rgb(world.rockTop), Offset(l - 0.15f * k, t - 0.1f * k), Size(r - l + 0.3f * k, 0.26f * k))
                d.drawLine(Color.White.copy(alpha = 0.25f), Offset(l - 0.15f * k, t - 0.08f * k), Offset(r + 0.15f * k, t - 0.08f * k), max(1f, 0.03f * k))
                val outer = if (x0 < 0f) l + 0.35f * k else r - 0.35f * k
                d.drawLine(OUTLINE_C, Offset(outer, t - 0.1f * k), Offset(outer, t - 1.6f * k), max(2f, 0.06f * k))
                d.drawLine(Color(0xFF94A3B8), Offset(outer, t - 0.1f * k), Offset(outer, t - 1.6f * k), max(1f, 0.03f * k))
                val blink = if (sin(clock * 3f) > 0.3f) 1f else 0.25f
                d.drawCircle(Color(0xFFEF4444).copy(alpha = blink), max(2.5f, 0.07f * k), Offset(outer, t - 1.62f * k))
                val vx = if (x0 < 0f) l + 0.8f * k else r - 1.5f * k
                d.drawRect(OUTLINE_C, Offset(vx - 2f, t - 0.6f * k - 2f), Size(0.7f * k + 4f, 0.52f * k + 4f))
                d.drawRect(Color(0xFF64748B), Offset(vx, t - 0.6f * k), Size(0.7f * k, 0.52f * k))
                d.drawLine(Color(0xFF475569), Offset(vx + 0.1f * k, t - 0.35f * k), Offset(vx + 0.6f * k, t - 0.35f * k), max(1f, 0.04f * k))
            }
            TowerStyle.CASTLE -> {
                val stone = Palette.lighter(rgb(world.rock), 0.12f)
                d.drawRect(OUTLINE_C, Offset(l - 0.2f * k - ol, t - 0.12f * k - ol), Size(r - l + 0.4f * k + ol * 2f, 0.3f * k + ol * 2f))
                d.drawRect(stone, Offset(l - 0.2f * k, t - 0.12f * k), Size(r - l + 0.4f * k, 0.3f * k))
                // Merlons at both ends
                for (e in 0..1) {
                    val mx = if (e == 0) l - 0.2f * k else r - 0.35f * k
                    d.drawRect(OUTLINE_C, Offset(mx - ol, t - 0.62f * k - ol), Size(0.55f * k + ol * 2f, 0.5f * k + ol))
                    d.drawRect(stone, Offset(mx, t - 0.62f * k), Size(0.55f * k, 0.5f * k))
                }
            }
            else -> grassCap(d, l, r, t, world, ol)
        }
    }

    private fun grassCap(d: DrawScope, l: Float, r: Float, t: Float, world: World, ol: Float) {
        val cap = rgb(world.rockTop)
        val capH = 0.22f * k
        val cr = androidx.compose.ui.geometry.CornerRadius(capH * 0.5f)
        d.drawRoundRect(OUTLINE_C, Offset(l - 0.1f * k - ol, t - 0.06f * k - ol), Size(r - l + 0.2f * k + ol * 2f, capH + ol * 2f), cr)
        d.drawRoundRect(cap, Offset(l - 0.1f * k, t - 0.06f * k), Size(r - l + 0.2f * k, capH), cr)
        d.drawRoundRect(Palette.darker(cap, 0.25f), Offset(l - 0.1f * k, t + 0.08f * k), Size(r - l + 0.2f * k, capH * 0.36f), cr)
        d.drawLine(Palette.lighter(cap, 0.35f), Offset(l - 0.02f * k, t - 0.03f * k), Offset(r + 0.02f * k, t - 0.03f * k), max(1f, 0.03f * k), StrokeCap.Round)
        path.reset()
        val step = 0.16f * k
        var i = 0
        var gx = l - 0.06f * k
        path.moveTo(gx, t - 0.04f * k)
        while (gx < r + 0.06f * k) {
            val hgt = (0.06f + ((i * 37) % 5) * 0.022f) * k
            path.lineTo(gx + step * 0.5f, t - 0.04f * k - hgt)
            path.lineTo(gx + step, t - 0.04f * k)
            gx += step
            i++
        }
        path.close()
        d.drawPath(path, Palette.darker(cap, 0.08f))
        d.drawRoundRect(cap, Offset(l - 0.1f * k, t + 0.05f * k), Size(0.12f * k, 0.28f * k), cr)
        d.drawRoundRect(cap, Offset(r - 0.02f * k, t + 0.05f * k), Size(0.12f * k, 0.2f * k), cr)
    }

    /** Stone blocks tower (sakura) or castle keep (arrow slits and a banner). */
    private fun stoneTower(d: DrawScope, l: Float, r: Float, t: Float, bottom: Float, world: World, ol: Float) {
        val rock = rgb(world.rock)
        d.drawRect(OUTLINE_C, Offset(l - ol, t), Size(r - l + ol * 2f, bottom - t))
        d.drawRect(rock, Offset(l, t), Size(r - l, bottom - t))
        val rowH = 0.55f * k
        val blockW = 1.3f * k
        val lineW = max(1f, 0.035f * k)
        var yy = t + 0.2f * k
        var row = 0
        val hi = Color.White.copy(alpha = 0.13f)
        val lo = Color(0x38000000)
        while (yy < bottom && yy < d.size.height) {
            val shift = if (row % 2 == 0) 0.25f * k else 0.9f * k
            var xx = l - shift
            var col = 0
            while (xx < r) {
                val bx0 = max(l, xx + lineW)
                val bx1 = min(r, xx + blockW - lineW)
                if (bx1 > bx0) {
                    val v = (((row * 31 + col * 17) % 7) - 3) * 0.012f
                    if (v != 0f) d.drawRect(if (v > 0f) Color.White.copy(alpha = v) else Color.Black.copy(alpha = -v), Offset(bx0, yy), Size(bx1 - bx0, rowH - lineW))
                    d.drawLine(hi, Offset(bx0, yy + lineW), Offset(bx1, yy + lineW), lineW)
                    d.drawLine(lo, Offset(bx0, yy + rowH - lineW), Offset(bx1, yy + rowH - lineW), lineW * 1.4f)
                    d.drawLine(lo, Offset(bx1, yy), Offset(bx1, yy + rowH), lineW)
                }
                xx += blockW
                col++
            }
            yy += rowH
            row++
        }
        if (world.base == TowerStyle.CASTLE) {
            // Arrow slits and a hanging banner
            val cx = (l + r) / 2f
            for (j in 0..2) {
                val sy0 = t + (1.6f + j * 2.6f) * k
                if (sy0 > bottom - 1f * k) break
                d.drawRoundRect(Color(0xFF1C1917), Offset(cx - 0.08f * k, sy0), Size(0.16f * k, 0.7f * k), androidx.compose.ui.geometry.CornerRadius(0.08f * k))
            }
            val bx = l + (r - l) * 0.2f
            val bw = 0.55f * k
            path.reset()
            path.moveTo(bx, t + 0.3f * k)
            path.lineTo(bx + bw, t + 0.3f * k)
            path.lineTo(bx + bw, t + 2.4f * k)
            path.lineTo(bx + bw / 2f, t + 2.0f * k)
            path.lineTo(bx, t + 2.4f * k)
            path.close()
            d.drawPath(path, OUTLINE_C, style = thinStroke)
            d.drawPath(path, Color(0xFFB91C1C))
            d.drawCircle(Color(0xFFFACC15), 0.12f * k, Offset(bx + bw / 2f, t + 1.0f * k))
        }
        d.drawRect(Color(0x26000000), Offset(l, t + 1.4f * k), Size(r - l, max(0f, bottom - t - 1.4f * k)))
        d.drawRect(Color(0x26000000), Offset(l, t + 3.2f * k), Size(r - l, max(0f, bottom - t - 3.2f * k)))
        d.drawRect(Color(0x30000000), Offset(r - 0.3f * k, t), Size(0.3f * k, bottom - t))
    }

    /** City building: facade with rows of windows (some lit), a neon sign on one of them. */
    private fun building(d: DrawScope, l: Float, r: Float, t: Float, bottom: Float, world: World, x0: Float) {
        val ol = max(2f, OUTLINE * k * 1.6f)
        val facade = rgb(world.rock)
        d.drawRect(OUTLINE_C, Offset(l - ol, t), Size(r - l + ol * 2f, bottom - t))
        d.drawRect(facade, Offset(l, t), Size(r - l, bottom - t))
        d.drawRect(Color.White.copy(alpha = 0.06f), Offset(l, t), Size(0.3f * k, bottom - t))
        val cols = ((r - l) / (0.95f * k)).toInt().coerceAtLeast(1)
        val gap = (r - l) / cols
        var row = 0
        var yy = t + 0.7f * k
        val seed = if (x0 < 0f) 3 else 11
        while (yy < bottom - 0.8f * k && yy < d.size.height) {
            for (c in 0 until cols) {
                val hsh = ((row * 73 + c * 151 + seed * 37) * 2654435761L ushr 7).toInt() and 0xff
                val lit = hsh % 3 != 0
                val wx = l + c * gap + gap * 0.22f
                d.drawRect(if (lit) Color(0xFFFDE68A) else Color(0xFF1E293B), Offset(wx, yy), Size(gap * 0.56f, 0.62f * k))
                if (lit) d.drawRect(Color(0xFFFFF7D6).copy(alpha = 0.5f), Offset(wx, yy), Size(gap * 0.56f, 0.12f * k))
            }
            yy += 1.15f * k
            row++
        }
        // Floor lines and a neon sign
        d.drawRect(Color(0x30000000), Offset(r - 0.35f * k, t), Size(0.35f * k, bottom - t))
        if (x0 >= 0f) {
            val sx0 = l + (r - l) * 0.15f
            val sy0 = t + 1.5f * k
            d.drawRoundRect(Color(0xFFF0ABFC).copy(alpha = 0.3f), Offset(sx0 - 0.1f * k, sy0 - 0.1f * k), Size(1.5f * k, 0.75f * k), androidx.compose.ui.geometry.CornerRadius(0.2f * k))
            d.drawRoundRect(Color(0xFFE879F9), Offset(sx0, sy0), Size(1.3f * k, 0.55f * k), androidx.compose.ui.geometry.CornerRadius(0.15f * k), style = gaugeStroke)
        }
    }

    /** Cliff / mesa / mountain: a rock mass widening towards the ground, with jagged sides. */
    private fun rockPillar(d: DrawScope, x0: Float, x1: Float, top: Float, groundY: Float, spread: Float, world: World, ol: Float) {
        val rock = rgb(world.rock)
        val steps = ((top - groundY) / 1.1f).toInt().coerceIn(4, 40)
        val jag = when (world.base) { TowerStyle.MOUNTAIN -> 0.45f; TowerStyle.CLIFF -> 0.3f; else -> 0.12f }
        val seed = if (x0 < 0f) 5 else 17
        path.reset()
        path.moveTo(sx(x0), sy(top))
        // Left side going down
        for (i in 1..steps) {
            val u = i / steps.toFloat()
            val n = (((i * 92821 + seed * 68917) % 1000) / 1000f - 0.5f) * jag * (if (i == steps) 0f else 1f)
            path.lineTo(sx(x0 - spread * u + n), sy(top - (top - groundY) * u))
        }
        path.lineTo(sx(x0 - spread), sy(groundY) + 4f)
        path.lineTo(sx(x1 + spread), sy(groundY) + 4f)
        for (i in steps downTo 1) {
            val u = i / steps.toFloat()
            val n = (((i * 68917 + seed * 92821) % 1000) / 1000f - 0.5f) * jag * (if (i == steps) 0f else 1f)
            path.lineTo(sx(x1 + spread * u + n), sy(top - (top - groundY) * u))
        }
        path.lineTo(sx(x1), sy(top))
        path.close()
        d.drawPath(path, OUTLINE_C, style = outlineStroke)
        d.drawPath(path, rock)
        // Shaded right half
        path2.reset()
        val mid = (x0 + x1) / 2f
        path2.moveTo(sx(mid + 0.6f), sy(top))
        path2.lineTo(sx(x1), sy(top))
        path2.lineTo(sx(x1 + spread), sy(groundY))
        path2.lineTo(sx(mid + spread * 0.35f), sy(groundY))
        path2.close()
        d.drawPath(path2, Color(0x2E000000))
        // Rock strata / cracks
        val strata = if (world.base == TowerStyle.MESA) 0.9f else 1.7f
        var y = top - 0.8f
        var j = 0
        while (y > groundY + 0.3f) {
            val u = (top - y) / (top - groundY)
            val lx = x0 - spread * u + 0.15f
            val rx = x1 + spread * u - 0.15f
            if (world.base == TowerStyle.MESA) {
                d.drawLine(Color(0x33000000), Offset(sx(lx), sy(y)), Offset(sx(rx), sy(y)), max(1.5f, 0.06f * k))
                d.drawLine(Color.White.copy(alpha = 0.12f), Offset(sx(lx), sy(y) + max(1.5f, 0.06f * k)), Offset(sx(rx), sy(y) + max(1.5f, 0.06f * k)), max(1f, 0.03f * k))
            } else {
                val cx = lx + (rx - lx) * (((j * 37 + 11) % 10) / 10f)
                d.drawLine(Color(0x33000000), Offset(sx(cx), sy(y)), Offset(sx(cx + 0.5f), sy(y - 0.6f)), max(1.5f, 0.05f * k), StrokeCap.Round)
                d.drawLine(Color(0x33000000), Offset(sx(cx + 0.5f), sy(y - 0.6f)), Offset(sx(cx + 0.2f), sy(y - 1.1f)), max(1.5f, 0.05f * k), StrokeCap.Round)
            }
            y -= strata
            j++
        }
        // Snow on the upper slopes / moss on cliffs
        if (world.ground == GroundStyle.SNOW || world.base == TowerStyle.CLIFF) {
            val c = if (world.ground == GroundStyle.SNOW) Color(0xF2F8FAFC) else rgb(world.rockTop, 0.85f)
            val depth = if (world.ground == GroundStyle.SNOW) 1.6f else 0.6f
            path2.reset()
            path2.moveTo(sx(x0 - 0.05f), sy(top))
            path2.lineTo(sx(x1 + 0.05f), sy(top))
            for (i in 0..6) {
                val u = 1f - i / 6f
                val xx = x1 + spread * (depth / (top - groundY)) - (x1 - x0 + 2f * spread * (depth / (top - groundY))) * (1f - u)
                val yy = top - depth * (0.4f + 0.6f * (((i * 7) % 3) / 2f))
                path2.lineTo(sx(xx), sy(yy))
            }
            path2.close()
            d.drawPath(path2, c)
        }
    }

    // ------------------------------------------------------------------ ground far below

    /** The ground under the towers, across the whole screen. Drawn before the towers. */
    private fun drawGround(d: DrawScope, groundY: Float, world: World, clock: Float) {
        val w = d.size.width
        val h = d.size.height
        val gy = sy(groundY)
        if (gy > h + 2f) return
        val x0w = camX - (w / 2f) / k
        val x1w = camX + (w / 2f) / k
        when (world.ground) {
            GroundStyle.SEA -> {
                d.drawRect(Color(0xFF0E7490), Offset(0f, gy), Size(w, h - gy + 2f))
                d.drawRect(Color(0xFF0369A1).copy(alpha = 0.6f), Offset(0f, gy + 1.5f * k), Size(w, h))
                d.drawRect(Color(0xFF38BDF8).copy(alpha = 0.55f), Offset(0f, gy), Size(w, 0.45f * k))
                // A sailboat passing far out
                val bxw = groundY.let { -40f + ((clock * 0.9f) % 120f) }
                if (bxw in x0w - 4f..x1w + 4f) boat(d, sx(bxw), gy + 0.05f * k, clock)
                // Glints
                var xx = floor(x0w / 2.3f) * 2.3f
                var i = 0
                while (xx < x1w + 2f) {
                    val ph = sin(clock * 2f + xx * 1.3f)
                    if (ph > 0f) d.drawLine(Color.White.copy(alpha = 0.35f * ph), Offset(sx(xx), gy + (0.7f + (i % 3) * 0.6f) * k), Offset(sx(xx + 0.6f), gy + (0.7f + (i % 3) * 0.6f) * k), max(1.5f, 0.05f * k), StrokeCap.Round)
                    xx += 2.3f
                    i++
                }
            }
            GroundStyle.LAVA -> {
                d.drawRect(Color(0xFF7C2D12), Offset(0f, gy), Size(w, h - gy + 2f))
                d.drawRect(Color(0xFFEA580C), Offset(0f, gy), Size(w, 0.9f * k))
                d.drawRect(Color(0xFFFACC15).copy(alpha = 0.55f), Offset(0f, gy), Size(w, 0.25f * k))
                // Heat glow above the surface
                d.drawRect(Color(0xFFF97316).copy(alpha = 0.12f), Offset(0f, gy - 2.5f * k), Size(w, 2.5f * k))
                // Bubbles
                var xx = floor(x0w / 1.7f) * 1.7f
                while (xx < x1w + 2f) {
                    val ph = ((clock * 0.7f + xx * 0.37f) % 1f + 1f) % 1f
                    val r = 0.05f + ph * 0.18f
                    d.drawCircle(Color(0xFFFDE047).copy(alpha = 0.8f * (1f - ph)), r * k, Offset(sx(xx + 0.3f), gy + 0.2f * k), style = thinStroke)
                    xx += 1.7f
                }
            }
            GroundStyle.STREET -> street(d, gy, x0w, x1w, clock)
            else -> {
                val base = when (world.ground) {
                    GroundStyle.SAND -> Color(0xFFE9C46A)
                    GroundStyle.SNOW -> Color(0xFFE2E8F0)
                    else -> Color(0xFF4D7C0F)
                }
                d.drawRect(base, Offset(0f, gy), Size(w, h - gy + 2f))
                d.drawRect(Palette.lighter(base, 0.2f), Offset(0f, gy), Size(w, 0.3f * k))
                d.drawRect(Palette.darker(base, 0.15f), Offset(0f, gy + 1.8f * k), Size(w, h))
                // Little details along the ground: cacti, pines or bushes
                var xx = floor(x0w / 4.5f) * 4.5f
                while (xx < x1w + 4f) {
                    val v = ((xx * 13.7f).toInt() % 3 + 3) % 3
                    val px = sx(xx + v * 0.7f)
                    when (world.ground) {
                        GroundStyle.SAND -> if (v != 1) {
                            d.drawLine(Color(0xFF15803D), Offset(px, gy), Offset(px, gy - 1.2f * k), 0.22f * k, StrokeCap.Round)
                            d.drawLine(Color(0xFF15803D), Offset(px - 0.3f * k, gy - 0.5f * k), Offset(px - 0.3f * k, gy - 0.85f * k), 0.14f * k, StrokeCap.Round)
                            d.drawLine(Color(0xFF15803D), Offset(px - 0.3f * k, gy - 0.5f * k), Offset(px, gy - 0.5f * k), 0.14f * k, StrokeCap.Round)
                        }
                        GroundStyle.SNOW -> {
                            path2.reset()
                            path2.moveTo(px - 0.5f * k, gy)
                            path2.lineTo(px, gy - (1.4f + v * 0.3f) * k)
                            path2.lineTo(px + 0.5f * k, gy)
                            path2.close()
                            d.drawPath(path2, Color(0xFF166534))
                            d.drawCircle(Color.White.copy(alpha = 0.8f), 0.15f * k, Offset(px, gy - (1.3f + v * 0.3f) * k))
                        }
                        else -> {
                            d.drawCircle(Color(0xFF3F6212), 0.45f * k, Offset(px, gy - 0.1f * k))
                            d.drawCircle(Color(0xFF65A30D), 0.35f * k, Offset(px + 0.35f * k, gy - 0.05f * k))
                        }
                    }
                    xx += 4.5f
                }
            }
        }
        // A soft shadow where each tower meets the ground
    }

    /** Water/lava in front of whatever fell in (drawn after the bodies). */
    private fun liquidFront(d: DrawScope, groundY: Float, world: World, clock: Float) {
        if (!GroundStyle.liquid(world.ground)) return
        val w = d.size.width
        val h = d.size.height
        val gy = sy(groundY)
        if (gy > h + 2f) return
        val lava = world.ground == GroundStyle.LAVA
        val c = if (lava) Color(0xFFEA580C) else Color(0xFF0E7490)
        d.drawRect(c.copy(alpha = 0.72f), Offset(0f, gy + 0.18f * k), Size(w, h - gy))
        // Wave crest
        path2.reset()
        val step = w / 24f
        path2.moveTo(0f, gy + 0.3f * k)
        var x = 0f
        while (x <= w + step) {
            val wx = (x - (w / 2f)) / k + camX
            path2.lineTo(x, gy + (0.1f + 0.1f * sin(wx * 1.6f + clock * 2.4f)) * k)
            x += step
        }
        path2.lineTo(w, gy + 0.35f * k)
        path2.close()
        d.drawPath(path2, if (lava) Color(0xFFFDE047).copy(alpha = 0.8f) else Color(0xFFBAE6FD).copy(alpha = 0.85f))
    }

    private fun boat(d: DrawScope, x: Float, y: Float, clock: Float) {
        val s = 0.9f * k
        val bob = sin(clock * 1.5f) * 0.05f * k
        path2.reset()
        path2.moveTo(x - s, y - 0.25f * s + bob)
        path2.lineTo(x + s, y - 0.25f * s + bob)
        path2.lineTo(x + s * 0.7f, y + 0.15f * s + bob)
        path2.lineTo(x - s * 0.7f, y + 0.15f * s + bob)
        path2.close()
        d.drawPath(path2, Color(0xFF7C2D12))
        d.drawLine(Color(0xFF451A03), Offset(x, y - 0.25f * s + bob), Offset(x, y - 1.6f * s + bob), max(1.5f, 0.05f * k))
        path2.reset()
        path2.moveTo(x + 0.05f * s, y - 1.55f * s + bob)
        path2.lineTo(x + 0.85f * s, y - 0.4f * s + bob)
        path2.lineTo(x + 0.05f * s, y - 0.4f * s + bob)
        path2.close()
        d.drawPath(path2, Color(0xFFF8FAFC))
    }

    /** Street far below: sidewalk, two lanes with cars driving both ways, lamp posts. */
    private fun street(d: DrawScope, gy: Float, x0w: Float, x1w: Float, clock: Float) {
        val w = d.size.width
        val h = d.size.height
        d.drawRect(Color(0xFF94A3B8), Offset(0f, gy), Size(w, 0.3f * k))
        d.drawRect(Color(0xFF64748B), Offset(0f, gy + 0.3f * k), Size(w, 0.1f * k))
        d.drawRect(Color(0xFF1F2937), Offset(0f, gy + 0.4f * k), Size(w, 3f * k))
        d.drawRect(Color(0xFF111827), Offset(0f, gy + 3.4f * k), Size(w, h))
        // Lane dashes
        var xx = floor(x0w / 2f) * 2f
        while (xx < x1w + 2f) {
            d.drawLine(Color(0xFFFDE68A), Offset(sx(xx), gy + 1.9f * k), Offset(sx(xx + 1f), gy + 1.9f * k), max(1.5f, 0.08f * k))
            xx += 2f
        }
        // Lamp posts with a pool of light
        xx = floor(x0w / 9f) * 9f
        while (xx < x1w + 9f) {
            val px = sx(xx)
            d.drawCircle(Color(0xFFFDE68A).copy(alpha = 0.12f), 1.3f * k, Offset(px, gy - 2.3f * k))
            d.drawLine(Color(0xFF334155), Offset(px, gy), Offset(px, gy - 2.6f * k), max(2f, 0.08f * k))
            d.drawLine(Color(0xFF334155), Offset(px, gy - 2.6f * k), Offset(px + 0.5f * k, gy - 2.6f * k), max(2f, 0.08f * k))
            d.drawCircle(Color(0xFFFEF9C3), 0.12f * k, Offset(px + 0.5f * k, gy - 2.5f * k))
            xx += 9f
        }
        // Cars: far lane to the right, near lane to the left
        val span = 70f
        for (c in 0 until 5) {
            val right = c % 2 == 0
            val speed = if (right) 4.5f + c * 0.4f else -(5f + c * 0.3f)
            val base = camX - span / 2f
            val cxw = base + (((c * 17f + clock * speed) % span) + span) % span
            if (cxw < x0w - 3f || cxw > x1w + 3f) continue
            val lane = if (right) 1.35f else 2.8f
            car(d, sx(cxw), gy + lane * k, if (right) 1f else -1f, CAR_COLORS[c % CAR_COLORS.size])
        }
    }

    private fun car(d: DrawScope, x: Float, y: Float, dir: Float, c: Color) {
        val s = k
        val cr = androidx.compose.ui.geometry.CornerRadius(0.15f * s)
        d.drawRoundRect(OUTLINE_C, Offset(x - 1.05f * s, y - 0.55f * s), Size(2.1f * s, 0.55f * s), cr)
        d.drawRoundRect(c, Offset(x - 1f * s, y - 0.5f * s), Size(2f * s, 0.45f * s), cr)
        d.drawRoundRect(Palette.darker(c, 0.15f), Offset(x - 0.55f * s, y - 0.9f * s), Size(1.1f * s, 0.45f * s), cr)
        d.drawRect(Color(0xFF93C5FD), Offset(x - 0.45f * s, y - 0.82f * s), Size(0.4f * s, 0.3f * s))
        d.drawRect(Color(0xFF93C5FD), Offset(x + 0.05f * s, y - 0.82f * s), Size(0.4f * s, 0.3f * s))
        d.drawCircle(Color(0xFF111827), 0.2f * s, Offset(x - 0.6f * s, y - 0.05f * s))
        d.drawCircle(Color(0xFF111827), 0.2f * s, Offset(x + 0.6f * s, y - 0.05f * s))
        // Head- and tail-lights
        d.drawCircle(Color(0xFFFEF9C3), 0.08f * s, Offset(x + dir * 0.98f * s, y - 0.35f * s))
        d.drawCircle(Color(0xFFFEF9C3).copy(alpha = 0.25f), 0.35f * s, Offset(x + dir * 1.2f * s, y - 0.35f * s))
        d.drawCircle(Color(0xFFEF4444), 0.07f * s, Offset(x - dir * 0.98f * s, y - 0.35f * s))
    }

    // ------------------------------------------------------------------ archers

    private var pass = 0
    private var outPx = 1f

    /** Draws a ragdoll archer with its look; [nock] puts an arrow on the string. */
    fun drawArcher(d: DrawScope, r: Ragdoll, o: Outfit, b: Bow, helmet: Boolean, nock: Boolean, clock: Float, nockKind: Int = 0) {
        val x = r.x
        val y = r.y
        outPx = max(1.2f, OUTLINE * k)
        // Soft contact shadow under a standing archer
        if (r.alive) {
            val gx = sx(r.rootX + 0.02f * r.facing)
            val gy = sy(r.rootY)
            d.drawOval(Color(0x40000000), Offset(gx - 0.5f * k, gy - 0.06f * k), Size(1.0f * k, 0.13f * k))
        }
        // Torso frame: axis (pelvis → neck) and the front direction
        var ax = x[Ragdoll.NECK] - x[Ragdoll.PELVIS]
        var ay = y[Ragdoll.NECK] - y[Ragdoll.PELVIS]
        val al = sqrt(ax * ax + ay * ay).coerceAtLeast(1e-4f)
        ax /= al; ay /= al
        val f = r.facing
        val fx = ay * f
        val fy = -ax * f
        val limbs = rgb(o.limbs)
        val limbsBack = Palette.darker(limbs, 0.28f)
        val body = rgb(o.body)
        val sleeveBack = Palette.darker(body, 0.25f)
        val skin = rgb(o.head)
        val boot = Palette.darker(limbs, 0.55f)
        val shX = x[Ragdoll.NECK] - ax * 0.07f
        val shY = y[Ragdoll.NECK] - ay * 0.07f
        // Pass 0 draws every part fatter in the outline colour (one silhouette), pass 1 fills.
        for (p in 0..1) {
            pass = p
            // Quiver on the back with fletchings sticking out
            val qx0 = x[Ragdoll.NECK] - fx * 0.15f + ax * 0.12f
            val qy0 = y[Ragdoll.NECK] - fy * 0.15f + ay * 0.12f
            val qx1 = x[Ragdoll.PELVIS] - fx * 0.17f + ax * 0.2f
            val qy1 = y[Ragdoll.PELVIS] - fy * 0.17f + ay * 0.2f
            for (j in 0..2) {
                val off = (j - 1) * 0.045f
                seg(d, QUIVER_FLETCH[j], qx0 + fx * off, qy0 + fy * off, qx0 + fx * off * 1.4f + ax * 0.14f, qy0 + fy * off * 1.4f + ay * 0.14f, 0.045f)
            }
            seg(d, Color(0xFF7C4A21), qx0, qy0, qx1, qy1, 0.12f)
            if (p == 1) seg(d, Color(0xFF9A6332), qx0 - fx * 0.02f, qy0 - fy * 0.02f, qx1 - fx * 0.02f, qy1 - fy * 0.02f, 0.04f)
            // Back leg
            seg(d, limbsBack, x[Ragdoll.PELVIS], y[Ragdoll.PELVIS], x[Ragdoll.L_KNEE], y[Ragdoll.L_KNEE], 0.14f)
            seg(d, limbsBack, x[Ragdoll.L_KNEE], y[Ragdoll.L_KNEE], x[Ragdoll.L_FOOT], y[Ragdoll.L_FOOT], 0.115f)
            bootAt(d, r, Ragdoll.L_KNEE, Ragdoll.L_FOOT, Palette.darker(boot, 0.2f))
            // Drawing arm (behind the body)
            seg(d, sleeveBack, shX, shY, x[Ragdoll.L_ELBOW], y[Ragdoll.L_ELBOW], 0.11f)
            seg(d, sleeveBack, x[Ragdoll.L_ELBOW], y[Ragdoll.L_ELBOW], x[Ragdoll.L_HAND], y[Ragdoll.L_HAND], 0.09f)
            dot(d, Palette.darker(skin, 0.1f), x[Ragdoll.L_HAND], y[Ragdoll.L_HAND], 0.058f)
            // Tunic: wide at the shoulders, a flared hem below the belt
            tunic(d, r, ax, ay, fx, fy, body)
            // Neck
            seg(d, Palette.darker(skin, 0.08f), x[Ragdoll.NECK], y[Ragdoll.NECK], x[Ragdoll.NECK] + ax * 0.08f, y[Ragdoll.NECK] + ay * 0.08f, 0.09f)
            if (p == 1) {
                // Belt with a buckle, and a light edge on the chest
                val bx = x[Ragdoll.PELVIS] + ax * 0.1f
                val by = y[Ragdoll.PELVIS] + ay * 0.1f
                seg(d, Color(0xFF3B2414), bx - fx * 0.14f, by - fy * 0.14f, bx + fx * 0.14f, by + fy * 0.14f, 0.065f)
                dot(d, Color(0xFFFACC15), bx + fx * 0.07f, by + fy * 0.07f, 0.03f)
                seg(d, Palette.lighter(body, 0.25f), x[Ragdoll.NECK] + fx * 0.09f - ax * 0.1f, y[Ragdoll.NECK] + fy * 0.09f - ay * 0.1f, x[Ragdoll.PELVIS] + fx * 0.09f + ax * 0.2f, y[Ragdoll.PELVIS] + fy * 0.09f + ay * 0.2f, 0.035f)
            }
        }
        // Front leg with its own outline so it reads over the back one
        for (p in 0..1) {
            pass = p
            seg(d, limbs, x[Ragdoll.PELVIS], y[Ragdoll.PELVIS], x[Ragdoll.R_KNEE], y[Ragdoll.R_KNEE], 0.14f)
            seg(d, limbs, x[Ragdoll.R_KNEE], y[Ragdoll.R_KNEE], x[Ragdoll.R_FOOT], y[Ragdoll.R_FOOT], 0.115f)
            bootAt(d, r, Ragdoll.R_KNEE, Ragdoll.R_FOOT, boot)
        }
        pass = 1
        dot(d, Palette.lighter(limbs, 0.2f), x[Ragdoll.R_KNEE], y[Ragdoll.R_KNEE], 0.035f)
        // Head
        drawHead(d, r, o, helmet)
        // Bow, string and nocked arrow
        drawBow(d, r, b, nock, clock, nockKind)
        // Bow arm in front, with its outline
        for (p in 0..1) {
            pass = p
            seg(d, body, shX, shY, x[Ragdoll.R_ELBOW], y[Ragdoll.R_ELBOW], 0.11f)
            seg(d, body, x[Ragdoll.R_ELBOW], y[Ragdoll.R_ELBOW], x[Ragdoll.R_HAND], y[Ragdoll.R_HAND], 0.09f)
            seg(d, Palette.darker(body, 0.3f), x[Ragdoll.R_ELBOW] + (x[Ragdoll.R_HAND] - x[Ragdoll.R_ELBOW]) * 0.55f, y[Ragdoll.R_ELBOW] + (y[Ragdoll.R_HAND] - y[Ragdoll.R_ELBOW]) * 0.55f, x[Ragdoll.R_HAND], y[Ragdoll.R_HAND], 0.1f)
            dot(d, skin, x[Ragdoll.R_HAND], y[Ragdoll.R_HAND], 0.062f)
        }
        pass = 1
    }

    /** A limb segment (world coords, width in meters); in the outline pass it's fatter and dark. */
    private fun seg(d: DrawScope, c: Color, x0: Float, y0: Float, x1: Float, y1: Float, wm: Float) {
        val wpx = wm * k + if (pass == 0) outPx * 2f else 0f
        d.drawLine(if (pass == 0) OUTLINE_C else c, Offset(sx(x0), sy(y0)), Offset(sx(x1), sy(y1)), wpx, StrokeCap.Round)
    }

    private fun dot(d: DrawScope, c: Color, x: Float, y: Float, rm: Float) {
        d.drawCircle(if (pass == 0) OUTLINE_C else c, rm * k + if (pass == 0) outPx else 0f, Offset(sx(x), sy(y)))
    }

    /** Boot: the foot plus a short toe pointing forward (perpendicular to the shin). */
    private fun bootAt(d: DrawScope, r: Ragdoll, knee: Int, foot: Int, c: Color) {
        var dx = r.x[foot] - r.x[knee]
        var dy = r.y[foot] - r.y[knee]
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        dx /= l; dy /= l
        val tx = -dy * r.facing
        val ty = dx * r.facing
        val fx = r.x[foot] - dx * 0.04f
        val fy = r.y[foot] - dy * 0.04f
        seg(d, c, fx - dx * 0.1f, fy - dy * 0.1f, fx, fy, 0.13f)
        seg(d, c, fx, fy, fx + tx * 0.13f, fy + ty * 0.13f, 0.09f)
        if (pass == 1) seg(d, Color(0xFF1C1917), fx - tx * 0.02f + dx * 0.035f, fy - ty * 0.02f + dy * 0.035f, fx + tx * 0.15f + dx * 0.035f, fy + ty * 0.15f + dy * 0.035f, 0.025f)
    }

    private fun tunic(d: DrawScope, r: Ragdoll, ax: Float, ay: Float, fx: Float, fy: Float, c: Color) {
        val nx = r.x[Ragdoll.NECK] - ax * 0.04f
        val ny = r.y[Ragdoll.NECK] - ay * 0.04f
        val px = r.x[Ragdoll.PELVIS]
        val py = r.y[Ragdoll.PELVIS]
        path.reset()
        path.moveTo(sx(nx + fx * 0.15f), sy(ny + fy * 0.15f))
        path.lineTo(sx(px + fx * 0.13f + ax * 0.08f), sy(py + fy * 0.13f + ay * 0.08f))
        path.lineTo(sx(px + fx * 0.17f - ax * 0.17f), sy(py + fy * 0.17f - ay * 0.17f))
        path.lineTo(sx(px - fx * 0.16f - ax * 0.17f), sy(py - fy * 0.16f - ay * 0.17f))
        path.lineTo(sx(px - fx * 0.13f + ax * 0.08f), sy(py - fy * 0.13f + ay * 0.08f))
        path.lineTo(sx(nx - fx * 0.15f), sy(ny - fy * 0.15f))
        path.close()
        if (pass == 0) {
            d.drawPath(path, OUTLINE_C, style = outlineStroke)
        } else {
            d.drawPath(path, c)
            d.drawPath(path, c, style = roundStroke)
            // Shade the back half of the tunic
            path2.reset()
            path2.moveTo(sx(nx), sy(ny))
            path2.lineTo(sx(px - ax * 0.17f), sy(py - ay * 0.17f))
            path2.lineTo(sx(px - fx * 0.16f - ax * 0.17f), sy(py - fy * 0.16f - ay * 0.17f))
            path2.lineTo(sx(px - fx * 0.13f + ax * 0.08f), sy(py - fy * 0.13f + ay * 0.08f))
            path2.lineTo(sx(nx - fx * 0.15f), sy(ny - fy * 0.15f))
            path2.close()
            d.drawPath(path2, Color(0x24000000))
        }
    }

    private fun drawHead(d: DrawScope, r: Ragdoll, o: Outfit, helmet: Boolean) {
        val hx = sx(r.x[Ragdoll.HEAD])
        val hy = sy(r.y[Ragdoll.HEAD])
        val nx = sx(r.x[Ragdoll.NECK])
        val ny = sy(r.y[Ragdoll.NECK])
        val hr = Ragdoll.HEAD_R * k * 1.15f
        val tilt = Math.toDegrees(atan2((hx - nx).toDouble(), (ny - hy).toDouble())).toFloat()
        val f = r.facing
        val c = Offset(hx, hy)
        val hat = if (helmet) Hat.HELMET else o.hat
        val hatC = if (helmet) Color(0xFFCBD5E1) else rgb(o.hatColor)
        val skin = rgb(o.head)
        val aimLook = if (r.alive && !r.cheer) r.aim.coerceIn(-0.8f, 0.8f) else 0f
        d.rotate(tilt, c) {
            scale(f, 1f, c) {
                if (hat == Hat.HOOD) {
                    drawCircle(OUTLINE_C, hr * 1.3f + outPx, Offset(hx - hr * 0.15f, hy - hr * 0.08f))
                    drawCircle(hatC, hr * 1.3f, Offset(hx - hr * 0.15f, hy - hr * 0.08f))
                }
                drawCircle(OUTLINE_C, hr + outPx, c)
                drawCircle(skin, hr, c)
                // Ear and a shade on the back of the head
                drawCircle(Palette.darker(skin, 0.12f), hr * 0.2f, Offset(hx - hr * 0.12f, hy + hr * 0.05f))
                drawArc(Color(0x22000000), 100f, 160f, false, Offset(hx - hr, hy - hr), Size(hr * 2f, hr * 2f), style = Stroke(hr * 0.22f))
                // Hair under the hat
                if (hat != Hat.SKULL && hat != Hat.VISOR && hat != Hat.HELMET && hat != Hat.BAND) {
                    drawArc(HAIR_C, 150f, 150f, true, Offset(hx - hr * 1.02f, hy - hr * 1.05f), Size(hr * 2.04f, hr * 1.7f))
                }
                // Face
                if (hat == Hat.SKULL) {
                    drawCircle(Color(0xFF111827), hr * 0.2f, Offset(hx + hr * 0.18f, hy - hr * 0.08f))
                    drawCircle(Color(0xFF111827), hr * 0.2f, Offset(hx + hr * 0.62f, hy - hr * 0.08f))
                    drawRect(Color(0xFF111827), Offset(hx + hr * 0.28f, hy + hr * 0.38f), Size(hr * 0.42f, hr * 0.1f))
                } else if (hat != Hat.VISOR) {
                    val ex = hx + hr * 0.5f
                    val ey = hy - hr * 0.12f - aimLook * hr * 0.08f
                    if (!r.alive) {
                        // Knocked out: X eyes
                        val e = hr * 0.14f
                        drawLine(OUTLINE_C, Offset(ex - e, ey - e), Offset(ex + e, ey + e), hr * 0.08f, StrokeCap.Round)
                        drawLine(OUTLINE_C, Offset(ex - e, ey + e), Offset(ex + e, ey - e), hr * 0.08f, StrokeCap.Round)
                    } else if ((r.time % 3.7f) < 0.12f || r.sinceHit < 0.2f) {
                        drawLine(OUTLINE_C, Offset(ex - hr * 0.14f, ey), Offset(ex + hr * 0.14f, ey), hr * 0.07f, StrokeCap.Round)
                    } else {
                        drawOval(Color.White, Offset(ex - hr * 0.15f, ey - hr * 0.19f), Size(hr * 0.3f, hr * 0.38f))
                        drawCircle(OUTLINE_C, hr * 0.1f, Offset(ex + hr * 0.05f, ey - aimLook * hr * 0.06f))
                        drawCircle(Color.White, hr * 0.035f, Offset(ex + hr * 0.08f, ey - hr * 0.04f - aimLook * hr * 0.06f))
                    }
                    // Brow: frowning while drawing the bow
                    val frown = r.draw * hr * 0.1f
                    drawLine(HAIR_C, Offset(ex - hr * 0.18f, ey - hr * 0.3f - frown * 0.3f), Offset(ex + hr * 0.18f, ey - hr * 0.28f + frown), hr * 0.07f, StrokeCap.Round)
                    // Nose and mouth
                    drawCircle(Palette.darker(skin, 0.1f), hr * 0.1f, Offset(hx + hr * 0.92f, hy + hr * 0.1f))
                    if (r.cheer) {
                        drawArc(OUTLINE_C, 0f, 180f, true, Offset(hx + hr * 0.38f, hy + hr * 0.3f), Size(hr * 0.42f, hr * 0.3f))
                    } else if (!r.alive || r.sinceHit < 0.4f) {
                        drawOval(OUTLINE_C, Offset(hx + hr * 0.5f, hy + hr * 0.36f), Size(hr * 0.18f, hr * 0.2f))
                    } else {
                        drawLine(Color(0x88000000), Offset(hx + hr * 0.42f, hy + hr * 0.45f), Offset(hx + hr * 0.7f, hy + hr * 0.42f), hr * 0.07f, StrokeCap.Round)
                    }
                    drawCircle(Color(0x33F43F5E), hr * 0.14f, Offset(hx + hr * 0.3f, hy + hr * 0.25f))
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

    fun drawBow(d: DrawScope, r: Ragdoll, b: Bow, nock: Boolean, clock: Float, nockKind: Int = 0) {
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
        // After the shot the string twangs back and forth for a moment.
        val twang = if (!drawing && r.recoil > 0.04f) sin(r.time * 90f) * r.recoil * 0.07f * k else 0f
        val spx = if (drawing) sx(r.x[Ragdoll.L_HAND]) else (t1x + t2x) / 2f + sdx * twang
        val spy = if (drawing) sy(r.y[Ragdoll.L_HAND]) else (t1y + t2y) / 2f + sdy * twang
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
            drawArrowScreen(d, ax, ay, ang, true, b, 1f, nockKind, clock)
        }
        // Limbs
        when (b.style) {
            BowStyle.NEON, BowStyle.FIRE, BowStyle.LASER -> {
                val pulse = if (b.style == BowStyle.FIRE) 0.3f + 0.2f * sin(clock * 11f) else 0.35f
                d.drawPath(path, acc.copy(alpha = pulse), style = glowStroke)
            }
            else -> Unit
        }
        d.drawPath(path, OUTLINE_C, style = bowOutline)
        d.drawPath(path, wood, style = bowStroke)
        d.drawPath(path, Palette.lighter(wood, 0.35f).copy(alpha = 0.55f), style = thinStroke)
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

    private fun drawArrow(d: DrawScope, x: Float, y: Float, angle: Float, byPlayer: Boolean, b: Bow, alpha: Float, kind: Int = 0, clock: Float = 0f) {
        drawArrowScreen(d, sx(x), sy(y), angle, byPlayer, b, alpha, kind, clock)
    }

    /** Arrow centred at screen (x, y), world angle [angle] (up positive). */
    fun drawArrowScreen(d: DrawScope, x: Float, y: Float, angle: Float, byPlayer: Boolean, b: Bow, alpha: Float, kind: Int = 0, clock: Float = 0f) {
        val dx = cos(angle); val dy = -sin(angle)
        val px = -dy; val py = dx
        val half = 0.36f * k
        val tipX = x + dx * half; val tipY = y + dy * half
        val tailX = x - dx * half; val tailY = y - dy * half
        if (max(tipX, tailX) < -50f || min(tipX, tailX) > d.size.width + 50f) return
        val sw = max(1.5f, 0.042f * k)
        d.drawLine(OUTLINE_C.copy(alpha = alpha), Offset(tailX, tailY), Offset(tipX, tipY), sw + max(1.6f, 0.03f * k), StrokeCap.Round)
        d.drawLine(Color(0xFF9A6332).copy(alpha = alpha), Offset(tailX, tailY), Offset(tipX, tipY), sw, StrokeCap.Round)
        d.drawLine(Color(0xFFD9A066).copy(alpha = alpha * 0.8f), Offset(tailX - px * sw * 0.2f, tailY - py * sw * 0.2f), Offset(tipX - px * sw * 0.2f, tipY - py * sw * 0.2f), sw * 0.35f)
        if (kind == ArrowKind.BOMB) {
            // A little round bomb instead of a head, with a sparking fuse.
            val bx = tipX + dx * 0.02f * k
            val by = tipY + dy * 0.02f * k
            d.drawCircle(OUTLINE_C.copy(alpha = alpha), 0.11f * k + 1.5f, Offset(bx, by))
            d.drawCircle(Color(0xFF27272A).copy(alpha = alpha), 0.11f * k, Offset(bx, by))
            d.drawCircle(Color.White.copy(alpha = alpha * 0.5f), 0.035f * k, Offset(bx - 0.04f * k, by - 0.04f * k))
            val fx = bx - px * 0.11f * k
            val fy = by - py * 0.11f * k - 0.04f * k
            d.drawLine(Color(0xFFD6D3D1).copy(alpha = alpha), Offset(bx - px * 0.08f * k, by - py * 0.08f * k), Offset(fx, fy), max(1f, 0.02f * k))
            d.drawCircle(Color(0xFFFDE047).copy(alpha = alpha * (0.6f + 0.4f * sin(clock * 40f))), 0.04f * k, Offset(fx, fy))
        } else {
        // Head (steel, with a bright edge; cyan crystal for ice, glowing for fire)
        if (kind == ArrowKind.FIRE) {
            val f = 0.85f + 0.15f * sin(clock * 30f)
            d.drawCircle(Color(0xFFF97316).copy(alpha = alpha * 0.45f), 0.2f * k * f, Offset(tipX + dx * 0.05f * k, tipY + dy * 0.05f * k))
            d.drawCircle(Color(0xFFFDE047).copy(alpha = alpha * 0.6f), 0.11f * k * f, Offset(tipX + dx * 0.06f * k, tipY + dy * 0.06f * k))
        }
        if (kind == ArrowKind.ICE) d.drawCircle(Color(0xFFA5F3FC).copy(alpha = alpha * 0.45f), 0.17f * k, Offset(tipX + dx * 0.05f * k, tipY + dy * 0.05f * k))
        path.reset()
        path.moveTo(tipX + dx * 0.13f * k, tipY + dy * 0.13f * k)
        path.lineTo(tipX + px * 0.06f * k - dx * 0.01f * k, tipY + py * 0.06f * k - dy * 0.01f * k)
        path.lineTo(tipX + dx * 0.02f * k, tipY + dy * 0.02f * k)
        path.lineTo(tipX - px * 0.06f * k - dx * 0.01f * k, tipY - py * 0.06f * k - dy * 0.01f * k)
        path.close()
        d.drawPath(path, OUTLINE_C.copy(alpha = alpha), style = thinStroke)
        d.drawPath(path, (when (kind) { ArrowKind.ICE -> Color(0xFF67E8F9); ArrowKind.FIRE -> Color(0xFFF97316); else -> Color(0xFF94A3B8) }).copy(alpha = alpha))
        d.drawLine(Color.White.copy(alpha = alpha * 0.8f), Offset(tipX + dx * 0.12f * k, tipY + dy * 0.12f * k), Offset(tipX + px * 0.045f * k, tipY + py * 0.045f * k), max(1f, 0.012f * k))
        }
        // Fletching
        val fc = when {
            !byPlayer -> Color(0xFFEF4444)
            kind == ArrowKind.FIRE -> Color(0xFFF97316)
            kind == ArrowKind.ICE -> Color(0xFF67E8F9)
            kind == ArrowKind.BOMB -> Color(0xFF52525B)
            kind == ArrowKind.TRIPLE -> Color(0xFFFACC15)
            else -> rgb(b.trail)
        }
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
            FIRE_TRAIL -> Color(0xFFFF7A1A)
            else -> rgb(color)
        }
        for (i in 1 until trailN) {
            val f = i / trailN.toFloat()
            val c = if (style == Trails.RAINBOW) Color.hsv(((i * 24f + clock * 200f) % 360f + 360f) % 360f, 0.8f, 1f) else base
            val a = when (style) { Trails.GHOST -> 0.22f; Trails.NEON -> 0.75f; else -> 0.5f } * f
            val wpx = (when (style) { Trails.GHOST, Trails.NEON -> 0.14f; Trails.BLUE_FIRE, FIRE_TRAIL -> 0.13f; else -> 0.08f }) * k * (0.35f + 0.65f * f)
            d.drawLine(c.copy(alpha = a), Offset(sx(trailX[i - 1]), sy(trailY[i - 1])), Offset(sx(trailX[i]), sy(trailY[i])), wpx, StrokeCap.Round)
            if ((style == Trails.STARS || style == Trails.GOLD) && i % 3 == 0) {
                val j = sin(i * 12.9898f + clock * 3f) * 0.12f
                d.drawCircle(if (style == Trails.GOLD) Color(0xFFFFE680) else Color.White, 0.035f * k, Offset(sx(trailX[i] + j), sy(trailY[i] - j)), alpha = f)
            }
        }
    }

    // ------------------------------------------------------------------ HUD in the world

    /** Health bar over an archer; the chunk just lost ([ghost]) drains behind. Returns its top (or null). */
    private fun hpBar(d: DrawScope, r: Ragdoll, hp: Int, maxHp: Int, ghost: Float, color: Color, myTurn: Boolean, clock: Float, shields: Int): Float? {
        if (!r.alive && hp <= 0 && ghost <= 0.5f) return null
        val bw = max(1.05f * k, d.size.width * 0.13f)
        val bh = max(0.13f * k, d.size.width * 0.018f)
        val x = sx(r.rootX) - bw / 2f
        val y = sy(r.rootY + 2.3f) - max(0f, d.size.width * 0.03f - 0.3f * k)
        if (x > d.size.width || x + bw < 0f) return null
        val cr = androidx.compose.ui.geometry.CornerRadius(bh / 2f)
        d.drawRoundRect(OUTLINE_C, Offset(x - 2.5f, y - 2.5f), Size(bw + 5f, bh + 5f), androidx.compose.ui.geometry.CornerRadius(bh))
        d.drawRoundRect(Color(0xFF2A2236), Offset(x, y), Size(bw, bh), cr)
        val f = (hp / maxHp.toFloat()).coerceIn(0f, 1f)
        val gf = (ghost / maxHp).coerceIn(0f, 1f)
        if (gf > f) d.drawRoundRect(Color(0xFFFFF3B0), Offset(x, y), Size(max(bh, bw * gf), bh), cr)
        if (f > 0f) {
            // Low health blinks
            val c = if (f < 0.3f && sin(clock * 10f) > 0f) Palette.lighter(color, 0.35f) else color
            d.drawRoundRect(c, Offset(x, y), Size(max(bh, bw * f), bh), cr)
            d.drawRoundRect(Color.White.copy(alpha = 0.32f), Offset(x + bh * 0.3f, y + bh * 0.15f), Size(max(0f, bw * f - bh * 0.6f), bh * 0.25f), cr)
        }
        // Ticks every 25 HP
        var t = 25
        while (t < maxHp) {
            val tx = x + bw * t / maxHp
            d.drawLine(Color(0x55000000), Offset(tx, y + 1f), Offset(tx, y + bh - 1f), 1.5f)
            t += 25
        }
        if (shields > 0) {
            d.drawCircle(OUTLINE_C, bh * 1.1f + 2f, Offset(x - bh * 1.6f, y + bh / 2f))
            d.drawCircle(Color(0xFF38BDF8), bh * 1.1f, Offset(x - bh * 1.6f, y + bh / 2f))
            d.drawCircle(Color.White.copy(alpha = 0.6f), bh * 0.5f, Offset(x - bh * 1.6f, y + bh / 2f))
        }
        if (myTurn) {
            val bob = sin(clock * 6f) * bh * 0.5f
            path.reset()
            val tx = x + bw / 2f
            val ty = y - bh * 0.8f + bob
            path.moveTo(tx - bh * 0.8f, ty - bh * 1.1f)
            path.lineTo(tx + bh * 0.8f, ty - bh * 1.1f)
            path.lineTo(tx, ty)
            path.close()
            d.drawPath(path, OUTLINE_C, style = thinStroke)
            d.drawPath(path, Color(0xFFFACC15))
            return ty - bh * 1.2f
        }
        return y - 3f
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
        const val OUTLINE = 0.026f
        const val FIRE_TRAIL = 100
        val OUTLINE_C = Color(0xFF1A1226)
        val HAIR_C = Color(0xFF3B2416)
        val CAR_COLORS = arrayOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFFFACC15), Color(0xFF10B981), Color(0xFFF8FAFC))
        val BALLOONS = arrayOf(Color(0xFFF43F5E), Color(0xFFFACC15), Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF34D399), Color(0xFFFB923C), Color(0xFFF472B6), Color(0xFFE11D48), Color(0xFF22D3EE), Color(0xFF84CC16))
        val QUIVER_FLETCH = arrayOf(Color(0xFFF8FAFC), Color(0xFFEF4444), Color(0xFFFACC15))
        const val TRAIL = 22
        const val HEADSHOT = "HEADSHOT!"
        private fun frac(v: Float) = v - floor(v)
        private const val PI_F = 3.1415927f
    }
}
