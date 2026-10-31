package com.farias.prismaarchers.data

import androidx.annotation.StringRes
import com.farias.prismaarchers.R
import kotlin.random.Random

enum class MissionType(@StringRes val textRes: Int) {
    LEVELS(R.string.mission_levels),
    HITS(R.string.mission_hits),
    HEADSHOTS(R.string.mission_heads),
    SCORE(R.string.mission_score),
    STREAK(R.string.mission_streak),
}

data class Mission(val type: MissionType, val target: Int, val reward: Int)

/** Three daily missions, the same for every player on the same day. */
object Missions {
    const val COUNT = 3

    fun forDay(day: Int): List<Mission> {
        val rnd = Random(day * 31L + 11)
        return MissionType.entries.shuffled(rnd).take(COUNT).map { type ->
            val tier = rnd.nextInt(3)
            val target = when (type) {
                MissionType.LEVELS -> listOf(3, 5, 8)[tier]
                MissionType.HITS -> listOf(10, 20, 35)[tier]
                MissionType.HEADSHOTS -> listOf(2, 4, 8)[tier]
                MissionType.SCORE -> listOf(300, 600, 1000)[tier]
                MissionType.STREAK -> listOf(2, 3, 5)[tier]
            }
            Mission(type, target, listOf(30, 45, 70)[tier])
        }
    }
}
