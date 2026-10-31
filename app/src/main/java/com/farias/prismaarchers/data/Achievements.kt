package com.farias.prismaarchers.data

import androidx.annotation.StringRes
import com.farias.prismaarchers.R

/** A long-term goal with a coin reward. [value] reads the current progress. */
class Achievement(val id: String, @StringRes val textRes: Int, val target: Int, val reward: Int, val value: () -> Int)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("hits50", R.string.ach_hits, 50, 50) { Progress.arrowsHit },
        Achievement("hits500", R.string.ach_hits, 500, 150) { Progress.arrowsHit },
        Achievement("hits2000", R.string.ach_hits, 2000, 400) { Progress.arrowsHit },
        Achievement("head10", R.string.ach_heads, 10, 80) { Progress.headshots },
        Achievement("head100", R.string.ach_heads, 100, 300) { Progress.headshots },
        Achievement("head500", R.string.ach_heads, 500, 700) { Progress.headshots },
        Achievement("boss1", R.string.ach_boss, 1, 100) { Progress.bossesBeaten },
        Achievement("boss5", R.string.ach_boss, 5, 250) { Progress.bossesBeaten },
        Achievement("boss20", R.string.ach_boss, 20, 600) { Progress.bossesBeaten },
        Achievement("perfect1", R.string.ach_perfect, 1, 80) { Progress.perfectLevels },
        Achievement("perfect20", R.string.ach_perfect, 20, 300) { Progress.perfectLevels },
        Achievement("long10", R.string.ach_long, 10, 120) { Progress.longShots },
        Achievement("comeback5", R.string.ach_comeback, 5, 150) { Progress.comebacks },
        Achievement("survival10", R.string.ach_survival, 10, 200) { Progress.bestEndless },
        Achievement("survival30", R.string.ach_survival, 30, 500) { Progress.bestEndless },
        Achievement("streak10", R.string.ach_streak, 10, 200) { Progress.bestWinStreak },
        Achievement("level50", R.string.ach_level, 50, 250) { Progress.level - 1 },
        Achievement("level100", R.string.ach_level, 100, 500) { Progress.level - 1 },
        Achievement("skins10", R.string.ach_skins, 10, 300) { Progress.ownedOutfits.size + Progress.ownedBows.size },
        Achievement("daily7", R.string.ach_daily, 7, 300) { Progress.dailyStreak() },
    )

    fun progress(a: Achievement): Int = a.value().coerceAtMost(a.target)
    fun done(a: Achievement): Boolean = a.value() >= a.target
    fun claimable(): Int = all.count { done(it) && it.id !in Progress.claimedAchievements }
}

/** Random locked bow for prizes (roulette, mystery). */
fun randomLockedBow(): String? = Looks.bows.filter { !Progress.ownsBow(it.id) }.randomOrNull()?.id
