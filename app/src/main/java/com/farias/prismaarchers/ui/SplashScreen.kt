package com.farias.prismaarchers.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.farias.prismaarchers.audio.Sfx
import com.farias.prismaarchers.audio.SoundManager
import com.farias.prismaarchers.data.Looks
import com.farias.prismaarchers.data.Progress
import com.farias.prismaarchers.game.Ragdoll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private val LOGO_COLORS = listOf(Color(0xFFFDE047), Color(0xFFFACC15), Color(0xFFFB923C), Color(0xFFF97316), Color(0xFFF43F5E), Color(0xFFE879F9))

// Timeline (seconds)
private const val DRAW_START = 0.35f
private const val RELEASE = 1.25f
private const val HIT = 1.95f
private const val LOGO = HIT + 0.15f
private const val END = 4.3f
private const val SLOW = 1.2f

private class SplashStar(val x: Float, val y: Float, val r: Float, val ph: Float)

private fun spring01(t: Float): Float = if (t <= 0f) 0f else 1f - exp(-7f * t) * cos(13f * t)

/**
 * Opening: at dusk an archer draws the bow, the arrow flies in an arc and hits the bullseye,
 * sparks burst and the logo pops in letter by letter. Tap to skip.
 */
@Composable
fun SplashScreen(sound: SoundManager, onFinish: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val stars = remember { Random(3).let { r -> List(60) { SplashStar(r.nextFloat(), r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 1.4f, r.nextFloat() * 6.28f) } } }
    val sparks = remember { Random(11).let { r -> List(48) { floatArrayOf(r.nextFloat() * 6.28f, 0.4f + r.nextFloat(), r.nextFloat()) } } }
    val painter = remember { ArcheryPainter() }
    val archer = remember { Ragdoll(0f, 0f, 1f) }
    val outfit = remember { Looks.outfit(Progress.outfitId) }
    val bow = remember { Looks.bow(Progress.bowId) }

    fun finish() {
        if (done) return
        done = true
        onFinish()
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (!done) {
            withFrameNanos { now ->
                t = (now - start) / 1e9f / SLOW
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                archer.aim = 0.42f
                archer.draw = when {
                    t < DRAW_START -> 0f
                    t < RELEASE - 0.1f -> ((t - DRAW_START) / (RELEASE - 0.1f - DRAW_START)).coerceIn(0f, 1f)
                    t < RELEASE -> 1f
                    else -> 0f
                }
                archer.step(dt, emptyList())
            }
            if (t > END) finish()
        }
    }
    LaunchedEffect(Unit) {
        launch { delay((DRAW_START * 1000 * SLOW).toLong()); sound.play(Sfx.DRAW, 0.8f) }
        launch { delay((RELEASE * 1000 * SLOW).toLong()); sound.play(Sfx.SHOOT) }
        launch { delay((HIT * 1000 * SLOW).toLong()); sound.play(Sfx.STICK); sound.play(Sfx.HEADSHOT, 0.6f) }
        launch { delay((LOGO * 1000 * SLOW).toLong() + 120); sound.play(Sfx.RECORD, 0.8f) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        val hDp = maxHeight
        val shining by remember { androidx.compose.runtime.derivedStateOf { t > LOGO + 0.8f && t < LOGO + 1.6f } }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (t / 0.5f).coerceIn(0f, 1f) }) { drawSplashSky(stars) }
        Canvas(Modifier.fillMaxSize()) { drawSplash(t, painter, archer, outfit, bow, sparks) }

        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = hDp * 0.16f)
                .then(if (shining) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
                .drawWithContent {
                    drawContent()
                    val s = ((t - (LOGO + 0.85f)) / 0.7f)
                    if (s in 0f..1f) {
                        val x = size.width * (-0.2f + 1.4f * s)
                        val band = size.width * 0.14f
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                                start = Offset(x - band, 0f),
                                end = Offset(x + band, size.height),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                "PRISMA".forEachIndexed { i, ch ->
                    Text(
                        ch.toString(),
                        style = Styles.glow(58, LOGO_COLORS[i], FontWeight.Black),
                        modifier = Modifier.graphicsLayer {
                            val lt = t - (LOGO + i * 0.065f)
                            val sc = spring01(lt)
                            scaleX = sc
                            scaleY = sc
                            alpha = (lt * 6f).coerceIn(0f, 1f)
                            translationY = (1f - sc.coerceAtMost(1f)) * -60f
                            rotationZ = (1f - sc) * (if (i % 2 == 0) -25f else 25f)
                        },
                    )
                }
            }
            Text(
                "ARCHERS",
                style = Styles.glow(26, Color.White, FontWeight.Black).copy(letterSpacing = 12.sp),
                modifier = Modifier.graphicsLayer {
                    val ta = ((t - (LOGO + 0.45f)) * 3f).coerceIn(0f, 1f)
                    alpha = ta
                    scaleX = 0.8f + 0.2f * ta
                    translationY = (1f - ta) * 30f
                },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val fade = ((t - (END - 0.35f)) / 0.35f).coerceIn(0f, 1f)
            if (fade > 0f) drawRect(Color(0xFF07051A), alpha = fade)
        }
    }
}

private fun DrawScope.drawSplashSky(stars: List<SplashStar>) {
    val w = size.width
    val h = size.height
    if (w < 2f || h < 2f) return
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0623), Color(0xFF2A1048), Color(0xFF7C2D52), Color(0xFFF97316)), endY = h * 0.85f))
    for (s in stars) {
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(s.ph * 3f))
        drawCircle(Color.White.copy(alpha = 0.75f * tw * (1f - s.y)), s.r * density * 0.8f, Offset(s.x * w, s.y * h))
    }
    // Setting sun and hills
    val sun = Offset(w * 0.5f, h * 0.8f)
    drawCircle(Brush.radialGradient(listOf(Color(0x99FDE68A), Color.Transparent), sun, w * 0.6f), w * 0.6f, sun)
    drawCircle(Color(0xFFFDBA74), w * 0.16f, sun)
    val far = Path().apply {
        moveTo(0f, h)
        for (i in 0..24) {
            val u = i / 24f
            lineTo(u * w, h * 0.78f - w * (0.06f + 0.04f * sin(u * 9f) + 0.02f * sin(u * 23f)))
        }
        lineTo(w, h); close()
    }
    drawPath(far, Color(0xFF3B1530))
    val near = Path().apply {
        moveTo(0f, h)
        for (i in 0..24) {
            val u = i / 24f
            lineTo(u * w, h * 0.86f - w * (0.03f + 0.025f * sin(u * 6f + 1f)))
        }
        lineTo(w, h); close()
    }
    drawPath(near, Color(0xFF1C0B1E))
}

private fun DrawScope.drawSplash(
    t: Float,
    painter: ArcheryPainter,
    archer: Ragdoll,
    outfit: com.farias.prismaarchers.data.Outfit,
    bow: com.farias.prismaarchers.data.Bow,
    sparks: List<FloatArray>,
) {
    val w = size.width
    val h = size.height
    if (w < 2f || h < 2f) return
    val k = w / 6.2f
    val groundY = h * 0.86f
    // Archer slides in from the left
    val enter = (1f - exp(-t * 6f))
    val ax = w * 0.17f - (1f - enter) * w * 0.4f
    painter.setTransform(0f, 0f, k, ax, groundY)
    painter.drawArcher(this, archer, outfit, bow, false, t < RELEASE, t)
    // Target on a stand at the right
    val tx = w * 0.84f
    val ty = h * 0.6f
    val tr = w * 0.11f
    val hitT = t - HIT
    val wobble = if (hitT > 0f) sin(hitT * 30f) * exp(-hitT * 5f) * 6f else 0f
    drawLine(Color(0xFF5B3A1E), Offset(tx - tr * 0.5f, ty), Offset(tx - tr * 0.9f, groundY + 4f), tr * 0.12f, StrokeCap.Round)
    drawLine(Color(0xFF5B3A1E), Offset(tx + tr * 0.5f, ty), Offset(tx + tr * 0.9f, groundY + 4f), tr * 0.12f, StrokeCap.Round)
    val rings = listOf(Color(0xFFF8FAFC), Color(0xFFEF4444), Color(0xFFF8FAFC), Color(0xFFEF4444), Color(0xFFFACC15))
    val c = Offset(tx + wobble, ty)
    drawCircle(Color(0xFF8B5A2B), tr * 1.08f, c)
    for ((i, col) in rings.withIndex()) drawCircle(col, tr * (1f - i * 0.19f), c)
    // Arrow flight (a parabola from the bow to the bullseye)
    val bx = painter.sx(archer.x[Ragdoll.R_HAND])
    val by = painter.sy(archer.y[Ragdoll.R_HAND])
    if (t >= RELEASE) {
        val u = ((t - RELEASE) / (HIT - RELEASE)).coerceIn(0f, 1f)
        val peak = h * 0.22f
        fun px(v: Float) = bx + (c.x - tr * 0.1f - bx) * v
        fun py(v: Float) = by + (ty - by) * v - peak * 4f * v * (1f - v)
        // Trail
        for (j in 1..14) {
            val v = (u - j * 0.02f)
            if (v < 0f) break
            drawCircle(Color(0xFFFDE68A).copy(alpha = 0.5f * (1f - j / 15f)), (k * 0.05f) * (1f - j / 16f), Offset(px(v), py(v)))
        }
        val x = px(u)
        val y = py(u)
        val dx = px((u + 0.01f).coerceAtMost(1.01f)) - px(u - 0.01f)
        val dy = py((u + 0.01f).coerceAtMost(1.01f)) - py(u - 0.01f)
        val ang = if (u < 1f) atan2(-dy, dx) else -0.5f
        painter.drawArrowScreen(this, x - kotlin.math.cos(ang) * k * 0.25f * (if (u >= 1f) 1f else 0f), y, ang, true, bow, 1f)
    }
    // Flash and sparks on the hit
    if (hitT > 0f) {
        val a = (1f - hitT / 0.35f).coerceIn(0f, 1f)
        if (a > 0f) drawRect(Color.White.copy(alpha = a * a * 0.6f))
        val rr = hitT * w * 0.9f
        if (hitT < 0.7f && rr > 1f) {
            drawCircle(Color(0xFFFFE680).copy(alpha = (1f - hitT / 0.7f) * 0.8f), rr, c, style = androidx.compose.ui.graphics.drawscope.Stroke(5f * density * (1f - hitT / 0.7f) + 1f))
        }
        for (s in sparks) {
            val st = hitT * s[1]
            if (st > 1.4f) continue
            val dist = w * 0.4f * (1f - exp(-st * 3.5f))
            val sx = c.x + cos(s[0]) * dist
            val sy = c.y + sin(s[0]) * dist * 0.7f + st * st * h * 0.08f
            val al = (1f - st / 1.4f).coerceIn(0f, 1f)
            drawCircle(lerp(Color(0xFFFFE680), Color(0xFFF97316), s[2]).copy(alpha = al), (1.5f + 2.5f * s[2]) * density, Offset(sx, sy))
        }
    }
}
