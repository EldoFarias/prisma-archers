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
                Box(Modifier.width(44.dp))
            }
            Spacer(Modifier.height(8.dp))
            ScoreText(host)
            if (host.windKmh != 0) {
                Spacer(Modifier.height(4.dp))
                WindPill(host.windKmh)
            }
        }

        if (host.boss && !host.touched && host.phase == Phase.PLAYING) BossBanner(Modifier.align(Alignment.Center).padding(bottom = 220.dp))

        if (host.notice != 0 && host.phase == Phase.PLAYING) {
            NoticeCard(host.notice, Modifier.align(Alignment.Center).padding(bottom = 60.dp, start = 24.dp, end = 24.dp)) { host.clearNotice() }
        }

        if (!host.touched && host.phase == Phase.PLAYING && host.shields == 0 && ads.rewardedReady &&
            (host.boss || host.endless || host.failsHere >= 1)
        ) {
            ShieldOffer(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp)) {
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
    Text(
        stringResource(if (mine) R.string.your_turn else R.string.enemy_turn),
        style = Styles.plain(14, if (mine) Color(0xFF34D399) else Color(0xFFFCA5A5), FontWeight.Black),
        modifier = modifier
            .clip(shape)
            .background(Color(0x8C0B0720))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun BossBanner(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "boss")
    val s by t.animateFloat(0.95f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Column(modifier.graphicsLayer { scaleX = s; scaleY = s }, horizontalAlignment = Alignment.CenterHorizontally) {
        CrownIcon(Modifier.size(54.dp, 40.dp))
        Text(stringResource(R.string.boss_duel), style = Styles.glow(30, Palette.Gold, FontWeight.Black))
        Text(stringResource(R.string.boss_hint), style = Styles.plain(14, Color.White, FontWeight.Bold), textAlign = TextAlign.Center)
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
                CoinsEarned(host.lastReward + bonus, big = true)
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
