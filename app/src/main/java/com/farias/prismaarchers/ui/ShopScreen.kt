package com.farias.prismaarchers.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farias.prismaarchers.R
import com.farias.prismaarchers.ads.AdsManager
import com.farias.prismaarchers.audio.Sfx
import com.farias.prismaarchers.audio.SoundManager
import com.farias.prismaarchers.data.Bow
import com.farias.prismaarchers.data.Looks
import com.farias.prismaarchers.data.Outfit
import com.farias.prismaarchers.data.Progress
import com.farias.prismaarchers.data.Rarity
import com.farias.prismaarchers.data.Trails
import com.farias.prismaarchers.data.rarityOf
import com.farias.prismaarchers.game.GameHost
import com.farias.prismaarchers.game.Ragdoll
import kotlin.math.atan2

/**
 * Outfits, bows and arrow trails. A live preview at the top shows the archer with the item being
 * looked at, drawing and shooting, so the player sees exactly what they buy.
 */
@Composable
fun ShopScreen(host: GameHost, ads: AdsManager, sound: SoundManager, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var outfitSel by remember { mutableStateOf(Progress.outfitId) }
    var bowSel by remember { mutableStateOf(Progress.bowId) }
    var trailSel by remember { mutableStateOf(Progress.trailId) }
    var reveal by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
    val panelBg = Brush.verticalGradient(listOf(Color(0xFF1B1240), Color(0xFF0B0720)))
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0720))) {
        val screenH = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = slide.value * size.height },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF130C30))
                    .safeTop()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.shop), style = Styles.gradient(30), modifier = Modifier.weight(1f))
                CoinPill(Progress.coins, onPlus = {
                    if (ads.adsReady && ads.rewardedReady) {
                        ads.showRewarded { Progress.addCoins(VIDEO_COINS); sound.play(Sfx.COIN) }
                    } else Toast.makeText(context, R.string.no_video, Toast.LENGTH_SHORT).show()
                })
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Palette.TextPrimary)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(screenH * 0.34f),
            ) {
                ShopPreview(Looks.outfit(outfitSel), Looks.bow(bowSel), Trails.byId(trailSel).style, Modifier.fillMaxSize())
                Text(
                    stringResource(R.string.preview),
                    style = Styles.plain(12, Color.White, FontWeight.ExtraBold),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(panelBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabChip(stringResource(R.string.outfits), tab == 0, Modifier.weight(1f)) { tab = 0 }
                    TabChip(stringResource(R.string.bows), tab == 1, Modifier.weight(1f)) { tab = 1 }
                    TabChip(stringResource(R.string.trails), tab == 2, Modifier.weight(1f)) { tab = 2 }
                }
                val locked = if (tab == 0) Looks.outfits.filter { !Progress.ownsOutfit(it.id) }.map { it.id }
                else Looks.bows.filter { !Progress.ownsBow(it.id) }.map { it.id }
                if (locked.isNotEmpty() && tab != 2) {
                    MysteryButton(MYSTERY_PRICE, Progress.coins >= MYSTERY_PRICE) {
                        if (!Progress.spend(MYSTERY_PRICE)) { sound.play(Sfx.INVALID); return@MysteryButton }
                        val id = locked.random()
                        if (tab == 0) {
                            Progress.giveOutfit(id); Progress.selectOutfit(id); outfitSel = id
                        } else {
                            Progress.giveBow(id); Progress.selectBow(id); bowSel = id
                        }
                        host.applyLooks()
                        sound.play(Sfx.RECORD)
                        reveal = (tab == 0) to id
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (tab) {
                        0 -> Looks.outfits.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { o ->
                                    ItemCard(
                                        name = stringResource(o.nameRes), price = o.price, rarity = rarityOf(o.price),
                                        owned = Progress.ownsOutfit(o.id), inUse = Progress.outfitId == o.id,
                                        selected = outfitSel == o.id, modifier = Modifier.weight(1f),
                                        onClick = { outfitSel = o.id; sound.play(Sfx.PICK, 0.6f) },
                                    ) { OutfitThumb(o) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        1 -> Looks.bows.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { b ->
                                    ItemCard(
                                        name = stringResource(b.nameRes), price = b.price, rarity = rarityOf(b.price),
                                        owned = Progress.ownsBow(b.id), inUse = Progress.bowId == b.id,
                                        selected = bowSel == b.id, modifier = Modifier.weight(1f),
                                        onClick = { bowSel = b.id; sound.play(Sfx.PICK, 0.6f) },
                                    ) { BowThumb(b) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        else -> Trails.all.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { tr ->
                                    ItemCard(
                                        name = stringResource(tr.nameRes), price = tr.price, rarity = rarityOf(tr.price),
                                        owned = Progress.ownsTrail(tr.id), inUse = Progress.trailId == tr.id,
                                        selected = trailSel == tr.id, modifier = Modifier.weight(1f),
                                        onClick = { trailSel = tr.id; sound.play(Sfx.PICK, 0.6f) },
                                    ) { TrailThumb(tr.style, Looks.bow(bowSel)) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> {
                                val o = Looks.outfit(outfitSel)
                                ActionButton(Progress.ownsOutfit(o.id), Progress.outfitId == o.id, o.price,
                                    onBuy = {
                                        if (Progress.buyOutfit(o.id, o.price)) { Progress.selectOutfit(o.id); host.applyLooks(); sound.play(Sfx.COIN) } else sound.play(Sfx.INVALID)
                                    },
                                    onUse = { Progress.selectOutfit(o.id); host.applyLooks(); sound.play(Sfx.PLACE) })
                            }
                            1 -> {
                                val b = Looks.bow(bowSel)
                                ActionButton(Progress.ownsBow(b.id), Progress.bowId == b.id, b.price,
                                    onBuy = {
                                        if (Progress.buyBow(b.id, b.price)) { Progress.selectBow(b.id); host.applyLooks(); sound.play(Sfx.COIN) } else sound.play(Sfx.INVALID)
                                    },
                                    onUse = { Progress.selectBow(b.id); host.applyLooks(); sound.play(Sfx.PLACE) })
                            }
                            else -> {
                                val tr = Trails.byId(trailSel)
                                ActionButton(Progress.ownsTrail(tr.id), Progress.trailId == tr.id, tr.price,
                                    onBuy = {
                                        if (Progress.buyTrail(tr.id, tr.price)) { Progress.selectTrail(tr.id); host.applyLooks(); sound.play(Sfx.COIN) } else sound.play(Sfx.INVALID)
                                    },
                                    onUse = { Progress.selectTrail(tr.id); host.applyLooks(); sound.play(Sfx.PLACE) })
                            }
                        }
                    }
                    val lockedSel = (tab == 0 && !Progress.ownsOutfit(outfitSel)) || (tab == 1 && !Progress.ownsBow(bowSel))
                    if (lockedSel && ads.rewardedReady) {
                        Spacer(Modifier.width(10.dp))
                        TryButton {
                            ads.showRewarded {
                                if (tab == 0) Progress.startTrial(outfitSel, "", TRIAL_GAMES) else Progress.startTrial("", bowSel, TRIAL_GAMES)
                                host.applyLooks()
                                sound.play(Sfx.RECORD)
                                Toast.makeText(context, context.getString(R.string.trial_started, TRIAL_GAMES), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        reveal?.let { (isOutfit, id) -> RevealDialog(isOutfit, id) { reveal = null } }
    }
}

/** The archer with the chosen looks, drawing and shooting in a loop over a little meadow. */
@Composable
private fun ShopPreview(outfit: Outfit, bow: Bow, trail: Int, modifier: Modifier) {
    val painter = remember { ArcheryPainter() }
    val archer = remember { Ragdoll(0f, 0f, 1f) }
    val tick = remember { mutableLongStateOf(0L) }
    val clock = remember { floatArrayOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                clock[0] += dt
                val c = clock[0] % CYCLE
                archer.aim = 0.35f
                archer.draw = if (c < 1.0f) (c / 0.9f).coerceAtMost(1f) else 0f
                archer.step(dt, emptyList())
                tick.longValue = now
            }
        }
    }
    val sky = remember { Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFFBAE6FD), Color(0xFFE0F2FE))) }
    Canvas(modifier) {
        tick.longValue
        val w = size.width
        val h = size.height
        if (w < 2f || h < 2f) return@Canvas
        drawRect(sky)
        drawCircle(Color(0x88FFF7D6), w * 0.14f, Offset(w * 0.82f, h * 0.22f))
        drawCircle(Color(0xFFFFF7D6), w * 0.06f, Offset(w * 0.82f, h * 0.22f))
        drawOval(Color(0xFF86EFAC), Offset(-w * 0.2f, h * 0.72f), Size(w * 1.4f, h * 0.7f))
        drawOval(Color(0xFF4ADE80), Offset(-w * 0.1f, h * 0.8f), Size(w * 0.9f, h * 0.5f))
        val k = h / 2.6f
        val groundY = h * 0.86f
        painter.setTransform(0f, 0f, k, w * 0.22f, groundY)
        val c = clock[0] % CYCLE
        painter.drawArcher(this, archer, outfit, bow, false, c < 1.0f, clock[0])
        if (c >= 1.0f) {
            // The arrow flies off to the right with its trail.
            val ft = c - 1.0f
            val vx = 9f; val vy = 3.2f
            val x = archer.x[Ragdoll.R_HAND] + vx * ft
            val y = archer.y[Ragdoll.R_HAND] + vy * ft - 4.9f * ft * ft
            painter.drawFlyingArrow(this, x, y, atan2(vy - 9.8f * ft, vx), bow, trail, clock[0], ft < 0.02f)
        }
    }
}

private const val CYCLE = 2.3f

/** The archer standing with the outfit. */
@Composable
fun OutfitThumb(o: Outfit) {
    val painter = remember { ArcheryPainter() }
    val archer = remember { Ragdoll(0f, 0f, 1f) }
    val bow = remember { Looks.bows[0] }
    Canvas(Modifier.fillMaxSize()) {
        if (size.width < 2f || size.height < 2f) return@Canvas
        val k = size.height / 2.35f
        painter.setTransform(0.15f, 0.95f, k, size.width / 2f, size.height / 2f)
        painter.drawArcher(this, archer, o, bow, false, false, 0f)
    }
}

/** The bow with an arrow on the string. */
@Composable
fun BowThumb(b: Bow) {
    val painter = remember { ArcheryPainter() }
    val archer = remember { Ragdoll(0f, 0f, 1f).also { it.aim = 0f } }
    Canvas(Modifier.fillMaxSize()) {
        if (size.width < 2f || size.height < 2f) return@Canvas
        val k = size.height / 1.55f
        painter.setTransform(archer.x[Ragdoll.R_HAND] - 0.05f, archer.y[Ragdoll.R_HAND], k, size.width / 2f, size.height / 2f)
        painter.drawBow(this, archer, b, true, 1f)
    }
}

/** A short arrow flight in the trail's style. */
@Composable
fun TrailThumb(style: Int, bow: Bow) {
    val painter = remember { ArcheryPainter() }
    Canvas(Modifier.fillMaxSize(0.92f)) {
        val w = size.width
        val h = size.height
        if (w < 2f || h < 2f) return@Canvas
        val k = w / 2.4f
        painter.setTransform(0f, 0f, k, w * 0.08f, h * 0.85f)
        var x = 0f
        var y = 0f
        var vx = 2.2f
        var vy = 2.6f
        for (i in 0 until 40) {
            val dt = 0.02f
            vy -= 4f * dt
            x += vx * dt
            y += vy * dt
            painter.trailPoint(x, y, i == 0)
        }
        painter.drawFlyingArrow(this, x, y, atan2(vy, vx), bow, style, 0f, false)
    }
}

/** "You got…": the new item spins in over light rays. */
@Composable
private fun RevealDialog(isOutfit: Boolean, id: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 120f)) }
    val t = rememberInfiniteTransition(label = "rays")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = androidx.compose.animation.core.LinearEasing)), label = "rot")
    val price = if (isOutfit) Looks.outfit(id).price else Looks.bow(id).price
    val rarity = rarityOf(price)
    val rc = rarityColor(rarity)
    DimOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(stringResource(R.string.new_item), style = Styles.gradient(34))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = rot }) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    for (k in 0 until 12) {
                        val a0 = Math.toRadians(k * 30.0).toFloat()
                        val a1 = a0 + 0.22f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + r * kotlin.math.cos(a0), c.y + r * kotlin.math.sin(a0))
                            lineTo(c.x + r * kotlin.math.cos(a1), c.y + r * kotlin.math.sin(a1))
                            close()
                        }
                        drawPath(path, rc.copy(alpha = 0.22f))
                    }
                    drawCircle(Brush.radialGradient(listOf(rc.copy(alpha = 0.55f), Color.Transparent), c, r), r, c)
                }
                Box(
                    Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            val v = anim.value
                            scaleX = 0.2f + 0.8f * v; scaleY = 0.2f + 0.8f * v
                            rotationY = (1f - v) * 540f
                            alpha = v.coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x55000000))
                        .border(3.dp, rc, RoundedCornerShape(24.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isOutfit) OutfitThumb(Looks.outfit(id)) else BowThumb(Looks.bow(id))
                }
            }
            Text(
                stringResource(if (isOutfit) Looks.outfit(id).nameRes else Looks.bow(id).nameRes),
                style = Styles.glow(28, Color.White, FontWeight.Black),
            )
            Text(stringResource(rarityName(rarity)), style = Styles.glow(17, rc, FontWeight.ExtraBold))
            Spacer(Modifier.height(16.dp))
            Box(Modifier.widthIn(max = 320.dp)) { PrimaryButton(stringResource(R.string.got_it), onClick = onClose) }
        }
    }
}

const val MYSTERY_PRICE = 750
const val TRIAL_GAMES = 3

@Composable
private fun TryButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .height(58.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("▶", style = Styles.plain(14, Color.White, FontWeight.Black))
        Text(stringResource(R.string.try_skin), style = Styles.plain(13, Color.White, FontWeight.ExtraBold))
    }
}

@Composable
private fun MysteryButton(price: Int, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val t = rememberInfiniteTransition(label = "mystery")
    val shine by t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1800)), label = "shine")
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFF59E0B))))
            .drawWithContent {
                drawContent()
                val x = size.width * shine
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), start = Offset(x - 80f, 0f), end = Offset(x + 80f, size.height)))
            }
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GiftIcon(Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.mystery_box), style = Styles.plain(16, Color.White, FontWeight.Black), modifier = Modifier.weight(1f))
        CoinIcon(Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(price.toString(), style = Styles.plain(16, Color.White, FontWeight.Black))
    }
}

@Composable
fun GiftIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFFFDE68A), Offset(w * 0.1f, h * 0.4f), Size(w * 0.8f, h * 0.55f), CornerRadius(w * 0.08f))
        drawRoundRect(Color(0xFFFBBF24), Offset(w * 0.04f, h * 0.28f), Size(w * 0.92f, h * 0.18f), CornerRadius(w * 0.06f))
        drawRect(Color(0xFFDC2626), Offset(w * 0.44f, h * 0.28f), Size(w * 0.12f, h * 0.67f))
        drawCircle(Color(0xFFDC2626), w * 0.13f, Offset(w * 0.36f, h * 0.2f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f))
        drawCircle(Color(0xFFDC2626), w * 0.13f, Offset(w * 0.64f, h * 0.2f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f))
    }
}

fun rarityColor(r: Rarity): Color = when (r) {
    Rarity.COMMON -> Color(0xFF94A3B8)
    Rarity.RARE -> Color(0xFF38BDF8)
    Rarity.EPIC -> Color(0xFFC084FC)
    Rarity.LEGENDARY -> Color(0xFFFBBF24)
}

fun rarityName(r: Rarity): Int = when (r) {
    Rarity.COMMON -> R.string.rarity_common
    Rarity.RARE -> R.string.rarity_rare
    Rarity.EPIC -> R.string.rarity_epic
    Rarity.LEGENDARY -> R.string.rarity_legendary
}

@Composable
private fun ActionButton(owned: Boolean, inUse: Boolean, price: Int, onBuy: () -> Unit, onUse: () -> Unit) {
    when {
        inUse -> SecondaryButton(stringResource(R.string.in_use)) {}
        owned -> PrimaryButton(stringResource(R.string.use), onClick = onUse)
        else -> PrimaryButton(
            if (Progress.coins >= price) stringResource(R.string.buy_for, price) else stringResource(R.string.need_coins, price - Progress.coins),
            enabled = Progress.coins >= price,
            onClick = onBuy,
        )
    }
}

@Composable
private fun TabChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(
                if (active) Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6)))
                else Brush.horizontalGradient(listOf(Palette.Glass, Palette.Glass)),
            )
            .border(1.dp, if (active) Color.White.copy(alpha = 0.3f) else Palette.GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Styles.plain(16, Color.White, FontWeight.ExtraBold)) }
}

@Composable
private fun ItemCard(
    name: String,
    price: Int,
    rarity: Rarity,
    owned: Boolean,
    inUse: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    thumb: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val rc = rarityColor(rarity)
    val legendary = rarity == Rarity.LEGENDARY
    // Only legendary cards pulse, and the pulse is drawn (not recomposed).
    val glow = if (legendary && !selected && !inUse) {
        rememberInfiniteTransition(label = "card").animateFloat(0.35f, 0.95f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    } else null
    val borderColor = when {
        selected -> Color.White
        inUse -> Color(0xFF34D399)
        else -> rc.copy(alpha = 0.55f)
    }
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(rc.copy(alpha = if (selected) 0.32f else 0.16f), Color(0x10FFFFFF))))
            .border(if (selected) 2.5.dp else 1.5.dp, borderColor, shape)
            .drawWithContent {
                drawContent()
                if (glow != null) {
                    val sw = 2.5.dp.toPx()
                    drawRoundRect(
                        rc.copy(alpha = glow.value),
                        topLeft = Offset(sw / 2, sw / 2),
                        size = Size(size.width - sw, size.height - sw),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            thumb()
            if (inUse) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF34D399)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            } else if (!owned) {
                Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        FitText(name, Styles.plain(13, Palette.TextPrimary, FontWeight.Bold), minSp = 9f)
        FitText(stringResource(rarityName(rarity)), Styles.plain(10, rc, FontWeight.ExtraBold), minSp = 7f)
        if (!owned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(price.toString(), style = Styles.plain(13, Palette.Gold, FontWeight.Black))
            }
        } else {
            Text(" ", style = Styles.plain(13))
        }
    }
}

