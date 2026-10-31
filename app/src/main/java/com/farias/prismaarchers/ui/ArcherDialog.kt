package com.farias.prismaarchers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farias.prismaarchers.R
import com.farias.prismaarchers.audio.Sfx
import com.farias.prismaarchers.audio.SoundManager
import com.farias.prismaarchers.data.Dances
import com.farias.prismaarchers.data.Progress
import com.farias.prismaarchers.data.Talents

private val TALENT_NAMES = intArrayOf(R.string.talent_health, R.string.talent_guide, R.string.talent_shield, R.string.talent_coins)
private val TALENT_DESC = intArrayOf(R.string.talent_health_desc, R.string.talent_guide_desc, R.string.talent_shield_desc, R.string.talent_coins_desc)
private val TALENT_COLORS = longArrayOf(0xFF34D399, 0xFF22D3EE, 0xFF60A5FA, 0xFFFACC15)
private val DANCE_NAMES = intArrayOf(R.string.dance_jump, R.string.dance_dance, R.string.dance_flex, R.string.dance_bow)

/** Talents (permanent upgrades bought with coins) and the victory dance. */
@Composable
fun ArcherDialog(sound: SoundManager, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.archer), style = Styles.gradient(30))
            CoinPill(Progress.coins)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.talents), style = Styles.plain(16, Palette.TextSecondary, FontWeight.Black))
                for (kind in 0 until 4) {
                    val lv = Progress.talent(kind)
                    val max = Talents.MAX[kind]
                    val full = lv >= max
                    val cost = Talents.cost(kind, lv)
                    val can = !full && Progress.coins >= cost
                    val c = Color(TALENT_COLORS[kind])
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(Palette.Glass).border(1.dp, Palette.GlassBorder, shape).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TalentIcon(kind, c, Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(TALENT_NAMES[kind]), style = Styles.plain(15, Palette.TextPrimary, FontWeight.Black))
                            Text(stringResource(TALENT_DESC[kind]), style = Styles.plain(12, Palette.TextSecondary))
                            // Level pips
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (i in 0 until max) {
                                    Spacer(
                                        Modifier.size(14.dp, 6.dp).clip(RoundedCornerShape(3.dp))
                                            .background(if (i < lv) c else Color(0x33FFFFFF)),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        val bs = RoundedCornerShape(50)
                        Row(
                            Modifier.clip(bs)
                                .background(if (full) Color(0x22FFFFFF) else if (can) c.copy(alpha = 0.85f) else Color(0x33FFFFFF))
                                .clickable(enabled = can) {
                                    if (Progress.buyTalent(kind)) sound.play(Sfx.COIN)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (full) {
                                Text(stringResource(R.string.maxed), style = Styles.plain(13, Palette.Gold, FontWeight.Black))
                            } else {
                                CoinIcon(Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(cost.toString(), style = Styles.plain(13, if (can) Color(0xFF0B0720) else Palette.TextSecondary, FontWeight.Black))
                            }
                        }
                    }
                }
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.dances), style = Styles.plain(16, Palette.TextSecondary, FontWeight.Black))
                for (id in Dances.PRICES.indices) {
                    val owned = id in Progress.ownedDances
                    val using = Progress.danceId == id
                    val price = Dances.PRICES[id]
                    val can = owned || Progress.coins >= price
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape)
                            .background(if (using) Color(0x3334D399) else Palette.Glass)
                            .border(1.dp, if (using) Color(0xFF34D399) else Palette.GlassBorder, shape)
                            .clickable(enabled = can && !using) {
                                if (!owned) {
                                    if (Progress.buyDance(id, price)) { sound.play(Sfx.COIN); Progress.selectDance(id) }
                                } else {
                                    Progress.selectDance(id)
                                    sound.play(Sfx.WHOOSH, 0.6f)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(DANCE_NAMES[id]), style = Styles.plain(15, Palette.TextPrimary, FontWeight.Black), modifier = Modifier.weight(1f))
                        when {
                            using -> Text(stringResource(R.string.in_use), style = Styles.plain(13, Color(0xFF34D399), FontWeight.Black))
                            owned -> Text(stringResource(R.string.use), style = Styles.plain(13, Palette.Cyan, FontWeight.Black))
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CoinIcon(Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(price.toString(), style = Styles.plain(13, if (can) Palette.Gold else Palette.TextSecondary, FontWeight.Black))
                            }
                        }
                    }
                }
            }
            LinkText(stringResource(R.string.close), onClick = onClose)
        }
    }
}

@Composable
private fun TalentIcon(kind: Int, c: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val s = Stroke(w * 0.1f, cap = StrokeCap.Round)
        when (kind) {
            Talents.HEALTH -> {
                drawLine(c, Offset(w * 0.5f, w * 0.2f), Offset(w * 0.5f, w * 0.8f), w * 0.2f, StrokeCap.Round)
                drawLine(c, Offset(w * 0.2f, w * 0.5f), Offset(w * 0.8f, w * 0.5f), w * 0.2f, StrokeCap.Round)
            }
            Talents.GUIDE -> {
                drawCircle(c, w * 0.35f, style = s)
                drawCircle(c, w * 0.1f)
            }
            Talents.SHIELD -> {
                drawCircle(c.copy(alpha = 0.3f), w * 0.42f)
                drawCircle(c, w * 0.42f, style = s)
            }
            else -> {
                drawCircle(Color(0xFFB45309), w * 0.4f)
                drawCircle(c, w * 0.34f)
                drawCircle(Color.White.copy(alpha = 0.5f), w * 0.1f, Offset(w * 0.4f, w * 0.38f))
            }
        }
    }
}
