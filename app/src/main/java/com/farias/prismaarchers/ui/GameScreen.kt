package com.farias.prismaarchers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farias.prismaarchers.R
import com.farias.prismaarchers.ads.AdsManager
import com.farias.prismaarchers.audio.Sfx
import com.farias.prismaarchers.audio.SoundManager
import com.farias.prismaarchers.data.Progress
import com.farias.prismaarchers.game.GameHost
import com.farias.prismaarchers.game.GameHost.Phase
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Drag length (fraction of the screen's short side) that gives full power. */
private const val FULL_POWER_DRAG = 0.36f

@Composable
fun GameScreen(host: GameHost, ads: AdsManager, sound: SoundManager, onGreatMoment: () -> Unit, onHome: () -> Unit) {
    BackHandler {
        when {
            host.phase == Phase.PLAYING -> host.pauseGame(!host.paused)
            host.phase == Phase.DEAD -> { host.giveUp(); onHome() }
            else -> onHome()
        }
    }

    // Drag state, read only by the drawing of the pull indicator.
    val startX = remember { mutableFloatStateOf(0f) }
    val startY = remember { mutableFloatStateOf(0f) }
    val nowX = remember { mutableFloatStateOf(0f) }
    val nowY = remember { mutableFloatStateOf(0f) }
    val dragging = remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = min(constraints.maxWidth, constraints.maxHeight).toFloat().coerceAtLeast(1f)
        // Drag back anywhere to aim: the arrow flies the opposite way, power by the pull length.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (host.phase != Phase.PLAYING || host.paused || !host.myTurn) return@awaitEachGesture
                        startX.floatValue = down.position.x
                        startY.floatValue = down.position.y
                        nowX.floatValue = down.position.x
                        nowY.floatValue = down.position.y
                        dragging.value = true
                        var angle = 0.3f
                        var power = 0f
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            if (c.positionChange() != Offset.Zero) c.consume()
                            nowX.floatValue = c.position.x
                            nowY.floatValue = c.position.y
                            val dx = c.position.x - startX.floatValue
                            val dy = c.position.y - startY.floatValue
                            val len = sqrt(dx * dx + dy * dy)
                            power = (len / (short * FULL_POWER_DRAG)).coerceIn(0f, 1f)
                            // Pull left/down → shoot right/up.
                            if (len > 4f) angle = atan2(dy, -dx).coerceIn(-1.2f, 1.45f)
                            host.aim(angle, power)
                        }
                        dragging.value = false
                        if (power >= 0.08f) host.release(angle, power) else host.cancelAim()
                    }
                },
        )
        // Pull indicator
        Canvas(Modifier.fillMaxSize()) {
            if (!dragging.value) return@Canvas
            val s = Offset(startX.floatValue, startY.floatValue)
            val n = Offset(nowX.floatValue, nowY.floatValue)
            val dx = n.x - s.x; val dy = n.y - s.y
            val p = (sqrt(dx * dx + dy * dy) / (short * FULL_POWER_DRAG)).coerceIn(0f, 1f)
            val col = Color(1f, 1f - 0.75f * p, 0.25f + 0.2f * (1f - p), 0.85f)
            drawCircle(Color.White.copy(alpha = 0.18f), 30.dp.toPx(), s)
            drawCircle(Color.White.copy(alpha = 0.6f), 6.dp.toPx(), s)
            drawLine(col, s, n, 5.dp.toPx(), StrokeCap.Round)
            drawCircle(col, 13.dp.toPx(), n)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(onClick = { host.pauseGame(true) }) { PauseIcon(Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { TitlePill(host) }
                Spacer(Modifier.width(12.dp))
                PanoramaButton(host.overview) { host.toggleOverview() }
            }
            Spacer(Modifier.height(8.dp))
            ScoreText(host)
            if (host.windKmh != 0) {
                Spacer(Modifier.height(4.dp))
                WindPill(host.windKmh)
            }
        }

        if (host.boss && !host.touched && host.phase == Phase.PLAYING) BossBanner(host.bossHintRes, Modifier.align(Alignment.Center).padding(bottom = 220.dp))

        if (host.notice != 0 && host.phase == Phase.PLAYING) {
            NoticeCard(host.notice, Modifier.align(Alignment.Center).padding(bottom = 60.dp, start = 24.dp, end = 24.dp)) { host.clearNotice() }
        }

        if (!host.touched && host.phase == Phase.PLAYING && host.shields == 0 && ads.rewardedReady &&
            (host.boss || host.endless || host.failsHere >= 1)
        ) {
            ShieldOffer(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 175.dp)) {
                ads.showRewarded { host.addShield() }
            }
        }

        if (host.phase == Phase.PLAYING && !host.paused) {
            if (!host.touched && (Progress.levelsWon < 3 || host.level <= 2) && !host.endless) {
                DragHint(Modifier.align(Alignment.Center).padding(top = 40.dp))
            } else {
                TurnPill(host.myTurn, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 70.dp))
            }
        }

        if (host.phase == Phase.PLAYING) DuelIntro(host, Modifier.align(Alignment.Center).padding(bottom = 120.dp))

        if (host.phase == Phase.PLAYING && !host.paused) {
            SpecialArrowBar(host, ads, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 108.dp))
        }

        TitleBanner(host.title, Modifier.align(Alignment.TopCenter).padding(top = 150.dp))

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { BannerAd(ads) }

        when {
            host.paused -> PauseOverlay(host, ads, onHome)
            host.phase == Phase.DEAD -> DeadOverlay(host, ads, sound, onHome)
            host.phase == Phase.WON -> WinOverlay(host, ads, sound, onGreatMoment, onHome)
        }
    }
}

// ------------------------------------------------------------------ HUD

@Composable
private fun TitlePill(host: GameHost) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Color(0x8C0B0720))
            .border(1.dp, if (host.boss) Palette.Gold else Palette.GlassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            host.endless -> {
                Text("∞", style = Styles.glow(20, Palette.Cyan, FontWeight.Black))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.kills_x, host.kills), style = Styles.plain(16, Color.White, FontWeight.Black))
            }
            host.daily -> {
                Text("★", style = Styles.glow(20, Palette.Gold, FontWeight.Black))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.daily_short), style = Styles.plain(16, Color.White, FontWeight.Black))
            }
            else -> {
                if (host.boss) {
                    CrownIcon(Modifier.size(20.dp, 15.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.level_x, host.level), style = Styles.plain(16, Color.White, FontWeight.Black))
            }
        }
    }
}

@Composable
private fun WindPill(kmh: Int) {
    val shape = RoundedCornerShape(50)
    val strong = abs(kmh) >= 20
    Row(
        Modifier
            .clip(shape)
            .background(Color(0x660B0720))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.wind), style = Styles.plain(13, Palette.TextSecondary, FontWeight.Bold))
        Spacer(Modifier.width(6.dp))
        val arrow = if (kmh > 0) "→" else "←"
        Text("$arrow ${abs(kmh)} km/h", style = Styles.plain(14, if (strong) Color(0xFFFB923C) else Color.White, FontWeight.Black))
    }
}

@Composable
private fun TurnPill(mine: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    // Slides and pops when the turn changes.
    androidx.compose.animation.AnimatedContent(
        targetState = mine,
        transitionSpec = {
            (androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.6f, stiffness = 400f)) { it } + fadeIn(tween(180))) togetherWith
                (androidx.compose.animation.slideOutVertically(tween(160)) { -it } + fadeOut(tween(160)))
        },
        modifier = modifier,
        label = "turn",
    ) { m ->
        Row(
            Modifier
                .clip(shape)
                .background(Color(0xB30B0720))
                .border(1.dp, (if (m) Color(0xFF34D399) else Color(0xFFF43F5E)).copy(alpha = 0.6f), shape)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (m) Color(0xFF34D399) else Color(0xFFF43F5E)))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (m) R.string.your_turn else R.string.enemy_turn),
                style = Styles.plain(14, if (m) Color(0xFFA7F3D0) else Color(0xFFFCA5A5), FontWeight.Black),
            )
        }
    }
}

/** Camera button: pulls back to show both archers (lit while active). */
@Composable
private fun PanoramaButton(active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (active) Brush.verticalGradient(listOf(Color(0xFFFACC15), Color(0xFFF97316))) else Brush.verticalGradient(listOf(Palette.Glass, Palette.Glass)))
            .border(1.dp, if (active) Color.White.copy(alpha = 0.6f) else Palette.GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val c = if (active) Color(0xFF3B1A00) else Color.White
            val w = size.width
            val sw = w * 0.1f
            val l = w * 0.32f
            // Four corner brackets
            for (cx in 0..1) for (cy in 0..1) {
                val x = if (cx == 0) sw / 2 else w - sw / 2
                val y = if (cy == 0) sw / 2 else w - sw / 2
                val dx = if (cx == 0) l else -l
                val dy = if (cy == 0) l else -l
                drawLine(c, Offset(x, y), Offset(x + dx, y), sw, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x, y + dy), sw, StrokeCap.Round)
            }
            // Two little archers facing each other
            drawCircle(c, w * 0.08f, Offset(w * 0.3f, w * 0.45f))
            drawLine(c, Offset(w * 0.3f, w * 0.52f), Offset(w * 0.3f, w * 0.7f), sw * 0.8f, StrokeCap.Round)
            drawCircle(c, w * 0.08f, Offset(w * 0.7f, w * 0.45f))
            drawLine(c, Offset(w * 0.7f, w * 0.52f), Offset(w * 0.7f, w * 0.7f), sw * 0.8f, StrokeCap.Round)
        }
    }
}

/** Opening banner of each duel: level (or survival count) and the world, punching in then fading. */
@Composable
private fun DuelIntro(host: GameHost, modifier: Modifier) {
    val anim = remember(host.duelId) { Animatable(0f) }
    LaunchedEffect(host.duelId) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(1700, easing = androidx.compose.animation.core.LinearEasing))
    }
    val done by remember(host.duelId) { androidx.compose.runtime.derivedStateOf { anim.value >= 1f } }
    if (done) return
    val title = when {
        host.endless -> stringResource(R.string.kills_x, host.kills)
        host.daily -> stringResource(R.string.daily_short)
        else -> stringResource(R.string.level_x, host.level)
    }
    Column(
        modifier.graphicsLayer {
            val t = anim.value
            val inT = (t / 0.18f).coerceIn(0f, 1f)
            val sc = if (t < 0.18f) 1.6f - 0.6f * easeOutBack(inT) else 1f + (t - 0.18f) * 0.06f
            scaleX = sc
            scaleY = sc
            alpha = if (t < 0.12f) t / 0.12f else (1f - (t - 0.75f) / 0.25f).coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (host.boss) CrownIcon(Modifier.size(46.dp, 34.dp))
        Text(title.uppercase(), style = Styles.gradient(44), textAlign = TextAlign.Center)
        Text(stringResource(host.world.nameRes), style = Styles.glow(18, Color.White, FontWeight.ExtraBold))
        Spacer(Modifier.height(6.dp))
        Text("⚔", style = Styles.glow(30, Palette.Gold, FontWeight.Black))
    }
}

@Composable
private fun BossBanner(hintRes: Int, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "boss")
    val s by t.animateFloat(0.95f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Column(modifier.graphicsLayer { scaleX = s; scaleY = s }, horizontalAlignment = Alignment.CenterHorizontally) {
        CrownIcon(Modifier.size(54.dp, 40.dp))
        Text(stringResource(R.string.boss_duel), style = Styles.glow(30, Palette.Gold, FontWeight.Black))
        Text(stringResource(if (hintRes != 0) hintRes else R.string.boss_hint), style = Styles.plain(14, Color.White, FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoticeCard(textRes: Int, modifier: Modifier, onClose: () -> Unit) {
    LaunchedEffect(textRes) {
        delay(6000)
        onClose()
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .widthIn(max = 380.dp)
            .clip(shape)
            .background(Color(0xE6140B33))
            .border(1.5.dp, Palette.Gold.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClose)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.new_mechanic), style = Styles.glow(16, Palette.Gold, FontWeight.Black))
        Text(stringResource(textRes), style = Styles.plain(15, Color.White, FontWeight.SemiBold), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShieldOffer(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))))
            .border(1.dp, Color.White.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🛡", style = Styles.plain(20, Color.White, FontWeight.Black))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.shield_video), style = Styles.plain(15, Color.White, FontWeight.ExtraBold))
    }
}

@Composable
private fun ScoreText(host: GameHost) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(host.score) {
        if (host.score == 0) return@LaunchedEffect
        pulse.snapTo(1.18f)
        pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    Text(
        host.score.toString(),
        style = Styles.glow(40, Color.White, FontWeight.Black),
        modifier = Modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        },
    )
}

/** A finger pulling back from the archer, then letting go. */
@Composable
private fun DragHint(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "hint")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "p")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(180.dp, 110.dp)) {
            val sx = size.width * 0.7f
            val sy = size.height * 0.3f
            val pull = (p / 0.7f).coerceIn(0f, 1f)
            val fx = sx - pull * size.width * 0.45f
            val fy = sy + pull * size.height * 0.45f
            val a = if (p < 0.85f) 1f else (1f - (p - 0.85f) / 0.15f)
            drawCircle(Color.White.copy(alpha = 0.25f * a), 22.dp.toPx(), Offset(sx, sy))
            drawLine(Color.White.copy(alpha = 0.7f * a), Offset(sx, sy), Offset(fx, fy), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = a), 12.dp.toPx(), Offset(fx, fy))
            // Arrow showing where the shot goes
            if (p > 0.72f) {
                val path = Path().apply {
                    moveTo(sx + 10.dp.toPx(), sy - 6.dp.toPx())
                    lineTo(sx + 44.dp.toPx(), sy - 34.dp.toPx())
                }
                drawPath(path, Color(0xFFFACC15).copy(alpha = a), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Color(0xFFFACC15).copy(alpha = a), 6.dp.toPx(), Offset(sx + 46.dp.toPx(), sy - 36.dp.toPx()))
            }
        }
        Text(
            stringResource(R.string.drag_hint),
            style = Styles.plain(17, Color.White, FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x8C0B0720))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ------------------------------------------------------------------ overlays

@Composable
private fun PauseOverlay(host: GameHost, ads: AdsManager, onHome: () -> Unit) {
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.paused), style = Styles.gradient(34))
            Spacer(Modifier.height(4.dp))
            SettingsToggles()
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.resume)) { host.pauseGame(false) }
            SecondaryButton(stringResource(R.string.restart)) {
                host.pauseGame(false)
                ads.onNewGame { host.retry() }
            }
            SecondaryButton(stringResource(R.string.home)) {
                host.pauseGame(false)
                onHome()
            }
            if (ads.privacyOptionsRequired) LinkText(stringResource(R.string.privacy_options)) { ads.showPrivacyOptions() }
        }
    }
}

/** Waits a moment so the player sees what happened before the panel covers it. */
@Composable
private fun Delayed(ms: Long, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ms)
        show = true
    }
    AnimatedVisibility(show, enter = fadeIn(tween(250)), exit = fadeOut()) { content() }
}

@Composable
private fun DeadOverlay(host: GameHost, ads: AdsManager, sound: SoundManager, onHome: () -> Unit) {
    Delayed(500) {
        DimOverlay {
            GlassPanel {
                Text(stringResource(R.string.defeated), style = Styles.gradient(34))
                if (host.endless) {
                    Text(stringResource(R.string.kills_x, host.kills), style = Styles.glow(36, Color.White, FontWeight.Black))
                    if (host.newBest) NewBestBadge() else Text(stringResource(R.string.best_x, Progress.bestEndless), style = Styles.plain(16, Palette.TextSecondary, FontWeight.Bold))
                    CoinsEarned(host.lastReward)
                } else {
                    val left = host.game.duel.enemyHp
                    Text(stringResource(R.string.enemy_hp_left, left), style = Styles.plain(17, Palette.TextSecondary, FontWeight.Bold))
                }
                if (!host.revived) {
                    if (ads.rewardedReady) {
                        PrimaryButton(stringResource(R.string.continue_video)) { ads.showRewarded { host.revive() } }
                    } else {
                        Text(stringResource(R.string.loading_video), style = Styles.plain(14, Palette.TextSecondary))
                    }
                }
                SecondaryButton(stringResource(R.string.try_again)) {
                    host.giveUp()
                    sound.play(Sfx.WHOOSH)
                    ads.onNewGame { host.retry() }
                }
                LinkText(stringResource(R.string.home)) {
                    host.giveUp()
                    onHome()
                }
            }
        }
    }
}

@Composable
private fun WinOverlay(host: GameHost, ads: AdsManager, sound: SoundManager, onGreatMoment: () -> Unit, onHome: () -> Unit) {
    LaunchedEffect(Unit) {
        if (!host.daily && host.level >= 6 && host.level % 4 == 2) onGreatMoment()
    }
    Delayed(400) {
        var bonus by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { sound.play(Sfx.COIN) }
        DimOverlay {
            GlassPanel {
                Text(
                    when {
                        host.daily -> stringResource(R.string.daily_complete)
                        host.boss -> stringResource(R.string.boss_beaten)
                        else -> stringResource(R.string.victory)
                    },
                    style = Styles.gradient(30),
                    textAlign = TextAlign.Center,
                )
                if (!host.daily) Text(stringResource(R.string.level_x, host.level), style = Styles.plain(17, Palette.TextSecondary, FontWeight.Bold))
                if (host.game.recorder.clip() != null) ReplayView(host)
                WinStars(host.stars, sound)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(host.score.toString(), style = Styles.glow(44, Color.White, FontWeight.Black))
                    if (host.newBest) {
                        Spacer(Modifier.width(10.dp))
                        NewBestBadge()
                    }
                }
                if (host.headsThisDuel > 0) {
                    Text(stringResource(R.string.headshots_x, host.headsThisDuel), style = Styles.glow(17, Color(0xFFFFD34D), FontWeight.Black))
                }
                CountUpCoins(host.lastReward + bonus)
                if (bonus == 0 && host.lastReward > 0 && ads.rewardedReady) {
                    SecondaryButton(stringResource(R.string.triple_video)) {
                        ads.showRewarded {
                            val extra = host.lastReward * 2
                            Progress.addCoins(extra)
                            bonus = extra
                            sound.play(Sfx.COIN)
                        }
                    }
                }
                if (host.daily) {
                    PrimaryButton(stringResource(R.string.home), onClick = onHome)
                } else {
                    PrimaryButton(stringResource(R.string.next_level_n, Progress.level)) {
                        sound.play(Sfx.WHOOSH)
                        ads.onNewGame { host.next() }
                    }
                    LinkText(stringResource(R.string.home), onClick = onHome)
                }
            }
        }
    }
}

@Composable
fun NewBestBadge() {
    val t = rememberInfiniteTransition(label = "best")
    val s by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s")
    Box(
        Modifier
            .graphicsLayer { scaleX = s; scaleY = s; rotationZ = 6f * sin(s * 6f) }
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFE680), Palette.Gold, Color(0xFFF59E0B))))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(stringResource(R.string.new_best), style = Styles.plain(13, Color(0xFF3B2300), FontWeight.Black))
    }
}

/** Three stars that pop in one after the other (the earned ones fill with gold). */
@Composable
private fun WinStars(stars: Int, sound: SoundManager) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 3) {
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(250L + i * 260L)
                if (i < stars) sound.play(Sfx.COIN, 0.6f, 1f + i * 0.18f)
                a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
            }
            StarIcon(
                filled = i < stars,
                modifier = Modifier
                    .size(if (i == 1) 58.dp else 46.dp)
                    .graphicsLayer {
                        scaleX = a.value
                        scaleY = a.value
                        rotationZ = (1f - a.value) * (if (i == 0) -60f else 60f)
                        translationY = if (i == 1) 0f else 6.dp.toPx()
                    },
            )
        }
    }
}

/** Coins earned, counting up from zero. */
@Composable
private fun CountUpCoins(amount: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(amount) {
        delay(500)
        a.animateTo(amount.toFloat(), tween(900))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoinIcon(Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("+" + a.value.toInt(), style = Styles.glow(30, Palette.Gold, FontWeight.Black))
    }
}

// ------------------------------------------------------------------ special arrows, titles, replay

/** Four special arrows (fire, triple, bomb, ice) with how many are left; tap to arm one for the next shot. */
@Composable
private fun SpecialArrowBar(host: GameHost, ads: AdsManager, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (kind in 1..4) {
            val count = Progress.arrowCount(kind)
            val selected = host.selectedArrow == kind
            val enabled = host.myTurn
            val scale by androidx.compose.animation.core.animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "s")
            Box(
                Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (enabled) 1f else 0.5f
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))
                            else Brush.verticalGradient(listOf(Color(0xCC1E1538), Color(0xCC0B0720))),
                        )
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Palette.GlassBorder, RoundedCornerShape(50))
                        .clickable(enabled = enabled) {
                            if (!host.selectArrow(kind)) {
                                if (ads.rewardedReady) ads.showRewarded { host.giveArrow(kind) }
                                else android.widget.Toast.makeText(context, R.string.no_video, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { ArrowKindIcon(kind, Modifier.size(32.dp)) }
                // Count (or a "watch video" badge when empty)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (count > 0) Color(0xFF7C3AED) else Color(0xFF0EA5E9))
                        .border(1.5.dp, Color.White, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (count > 0) (if (count > 99) "99" else count.toString()) else "▶", style = Styles.plain(11, Color.White, FontWeight.Black))
                }
            }
        }
    }
}

/** Icon of a special arrow kind. */
@Composable
fun ArrowKindIcon(kind: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shaft = Color(0xFFD9A066)
        fun arrow(x0: Float, y0: Float, x1: Float, y1: Float, head: Color) {
            drawLine(Color(0xFF1A1226), Offset(x0, y0), Offset(x1, y1), w * 0.13f, StrokeCap.Round)
            drawLine(shaft, Offset(x0, y0), Offset(x1, y1), w * 0.07f, StrokeCap.Round)
            drawCircle(head, w * 0.1f, Offset(x1, y1))
        }
        when (kind) {
            1 -> {
                // Fire
                drawCircle(Color(0xFFF97316).copy(alpha = 0.35f), w * 0.36f, Offset(w * 0.66f, h * 0.36f))
                arrow(w * 0.15f, h * 0.85f, w * 0.68f, h * 0.32f, Color(0xFFF97316))
                drawOval(Color(0xFFF97316), Offset(w * 0.56f, h * 0.06f), Size(w * 0.26f, h * 0.36f))
                drawOval(Color(0xFFFDE047), Offset(w * 0.62f, h * 0.16f), Size(w * 0.14f, h * 0.22f))
            }
            2 -> {
                // Triple
                arrow(w * 0.12f, h * 0.8f, w * 0.8f, h * 0.18f, Color(0xFFCBD5E1))
                arrow(w * 0.1f, h * 0.62f, w * 0.72f, h * 0.06f, Color(0xFFCBD5E1))
                arrow(w * 0.3f, h * 0.92f, w * 0.92f, h * 0.36f, Color(0xFFCBD5E1))
            }
            3 -> {
                // Bomb
                drawCircle(Color(0xFF1A1226), w * 0.34f, Offset(w * 0.45f, h * 0.58f))
                drawCircle(Color(0xFF3F3F46), w * 0.29f, Offset(w * 0.45f, h * 0.58f))
                drawCircle(Color.White.copy(alpha = 0.5f), w * 0.08f, Offset(w * 0.35f, h * 0.48f))
                drawLine(Color(0xFFD6D3D1), Offset(w * 0.62f, h * 0.32f), Offset(w * 0.78f, h * 0.14f), w * 0.07f, StrokeCap.Round)
                drawCircle(Color(0xFFFDE047), w * 0.09f, Offset(w * 0.8f, h * 0.12f))
            }
            else -> {
                // Ice: a snowflake
                val c = Offset(w / 2f, h / 2f)
                for (i in 0 until 3) {
                    val a = Math.toRadians(i * 60.0).toFloat()
                    val dx = kotlin.math.cos(a) * w * 0.42f
                    val dy = kotlin.math.sin(a) * w * 0.42f
                    drawLine(Color(0xFF67E8F9), Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), w * 0.1f, StrokeCap.Round)
                }
                drawCircle(Color.White, w * 0.1f, c)
            }
        }
    }
}

/** Combo title ("DOUBLE HEADSHOT!") punching in with its bonus, then floating away. */
@Composable
private fun TitleBanner(title: com.farias.prismaarchers.game.TitleShow?, modifier: Modifier) {
    if (title == null) return
    val anim = remember(title.id) { Animatable(0f) }
    LaunchedEffect(title.id) { anim.animateTo(1f, tween(1700, easing = androidx.compose.animation.core.LinearEasing)) }
    val done by remember(title.id) { androidx.compose.runtime.derivedStateOf { anim.value >= 1f } }
    if (done) return
    Column(
        modifier.graphicsLayer {
            val t = anim.value
            val inT = (t / 0.15f).coerceIn(0f, 1f)
            val sc = if (t < 0.15f) 2f - easeOutBack(inT) else 1f
            scaleX = sc
            scaleY = sc
            translationY = -(t - 0.6f).coerceAtLeast(0f) * 120f
            alpha = if (t < 0.08f) t / 0.08f else (1f - (t - 0.75f) / 0.25f).coerceIn(0f, 1f)
            rotationZ = if (t < 0.15f) (1f - inT) * -8f else 0f
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title.textRes), style = Styles.gradient(34), textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (title.bonus > 0) Text("+${title.bonus}", style = Styles.glow(20, Color.White, FontWeight.Black))
            if (title.coins > 0) {
                Spacer(Modifier.width(10.dp))
                CoinIcon(Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("+${title.coins}", style = Styles.glow(20, Palette.Gold, FontWeight.Black))
            }
        }
    }
}

/** The killing blow again, looping, in a little screen inside the victory panel. */
@Composable
private fun ReplayView(host: GameHost) {
    val clip = remember { host.game.recorder.clip() } ?: return
    val painter = remember { ArcheryPainter() }
    val frame = remember { androidx.compose.runtime.mutableIntStateOf(clip[0]) }
    val clock = remember { floatArrayOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0f
        var hold = 0f
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                clock[0] += dt
                if (frame.intValue >= clip[1]) {
                    hold += dt
                    if (hold > 0.8f) { hold = 0f; frame.intValue = clip[0] }
                } else {
                    acc += dt
                    while (acc >= 1f / 30f && frame.intValue < clip[1]) { acc -= 1f / 30f; frame.intValue++ }
                }
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            painter.drawReplay(this, host, frame.intValue, clock[0])
        }
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val t = rememberInfiniteTransition(label = "rec")
            val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
            Box(Modifier.size(8.dp).graphicsLayer { alpha = a }.clip(RoundedCornerShape(50)).background(Color(0xFFF43F5E)))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.replay), style = Styles.plain(11, Color.White, FontWeight.Black))
        }
    }
}
