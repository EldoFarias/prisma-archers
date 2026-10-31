package com.farias.prismaarchers.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/**
 * Everything the player keeps between sessions: level, coins, themes, records, missions, chest and
 * settings. Backed by SharedPreferences and exposed as Compose state so screens update by themselves.
 */
object Progress {

    private const val PREFS = "prisma_archers"
    private lateinit var prefs: SharedPreferences

    /** Next level to play (1-based). */
    var level by mutableIntStateOf(1)
        private set
    var coins by mutableIntStateOf(0)
        private set
    var soundOn by mutableStateOf(true)
        private set
    var musicOn by mutableStateOf(true)
        private set
    var vibrationOn by mutableStateOf(true)
        private set
    /** Scenery, clouds and air particles. Turned off by itself on slow phones. */
    var detailedScenery by mutableStateOf(true)
        private set
    var notificationsOn by mutableStateOf(true)
        private set
    var notifAsked by mutableStateOf(false)
        private set
    var outfitId by mutableStateOf("classic")
        private set
    var bowId by mutableStateOf("wood")
        private set
    var ownedOutfits by mutableStateOf(setOf("classic"))
        private set
    var ownedBows by mutableStateOf(setOf("wood"))
        private set
    var tutorialDone by mutableStateOf(false)
        private set
    var lastReviewAsk by mutableStateOf(0L)
        private set

    // Statistics
    var levelsWon by mutableIntStateOf(0)
        private set
    var levelsLost by mutableIntStateOf(0)
        private set
    var arrowsHit by mutableIntStateOf(0)
        private set
    var headshots by mutableIntStateOf(0)
        private set
    var bestScore by mutableIntStateOf(0)
        private set
    var bestKillStreak by mutableIntStateOf(0)
        private set
    /** Most consecutive wins without losing. */
    var winStreak by mutableIntStateOf(0)
        private set
    var bestWinStreak by mutableIntStateOf(0)
        private set

    // Daily missions
    private var missionDay = 0
    var missionProgress by mutableStateOf(List(Missions.COUNT) { 0 })
        private set
    var missionClaimed by mutableStateOf(List(Missions.COUNT) { false })
        private set

    // Daily chest (7-day calendar)
    var chestLastDay by mutableIntStateOf(0)
        private set
    var chestRun by mutableIntStateOf(0)
        private set

    val isReady: Boolean get() = ::prefs.isInitialized

    // Counters for achievements (all persisted as ints under their own key)
    var bossesBeaten by mutableIntStateOf(0)
        private set
    var perfectLevels by mutableIntStateOf(0)
        private set
    var coinsCollected by mutableIntStateOf(0)
        private set
    var longShots by mutableIntStateOf(0)
        private set
    var comebacks by mutableIntStateOf(0)
        private set
    var bestEndless by mutableIntStateOf(0)
        private set
    var claimedAchievements by mutableStateOf(setOf<String>())
        private set

    // Trails (third shop tab)
    var trailId by mutableStateOf("classic")
        private set
    var ownedTrails by mutableStateOf(setOf("classic"))
        private set

    // Try a skin for a few games (rewarded video)
    var trialOutfit by mutableStateOf("")
        private set
    var trialBow by mutableStateOf("")
        private set
    var trialGames by mutableIntStateOf(0)
        private set

    // Daily roulette
    var rouletteDay by mutableIntStateOf(0)
        private set
    var rouletteVideoSpins by mutableIntStateOf(0)
        private set

    /** Talent levels: extra health, longer aim guide, starting shield, more coins (see [Talents]). */
    var talents by mutableStateOf(List(4) { 0 })
        private set
    /** Victory celebration in use and the ones owned. */
    var danceId by mutableIntStateOf(0)
        private set
    var ownedDances by mutableStateOf(setOf(0))
        private set
    /** Best target practice score. */
    var targetBest by mutableIntStateOf(0)
        private set

    /** Special arrows in stock, by ArrowKind (index 0 = normal, unused). */
    var arrowStock by mutableStateOf(List(5) { 0 })
        private set

    // Mechanics already introduced ("new!" notice)
    var seenMechanics by mutableStateOf(setOf<String>())
        private set

    fun init(context: Context) {
        if (isReady) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        level = prefs.getInt("level", 1)
        coins = prefs.getInt("coins", 100) // a small welcome gift
        soundOn = prefs.getBoolean("sound", true)
        musicOn = prefs.getBoolean("music", true)
        vibrationOn = prefs.getBoolean("vibration", true)
        detailedScenery = prefs.getBoolean("detailed_scenery", true)
        notificationsOn = prefs.getBoolean("notifications", true)
        notifAsked = prefs.getBoolean("notif_asked", false)
        outfitId = prefs.getString("outfit", "classic") ?: "classic"
        bowId = prefs.getString("bow", "wood") ?: "wood"
        ownedOutfits = prefs.getStringSet("owned_outfits", setOf("classic"))?.toSet() ?: setOf("classic")
        ownedBows = prefs.getStringSet("owned_bows", setOf("wood"))?.toSet() ?: setOf("wood")
        tutorialDone = prefs.getBoolean("tutorial_done", false)
        lastReviewAsk = prefs.getLong("last_review_ask", 0L)
        levelsWon = prefs.getInt("levels_won", 0)
        levelsLost = prefs.getInt("levels_lost", 0)
        arrowsHit = prefs.getInt("arrows_hit", 0)
        headshots = prefs.getInt("headshots", 0)
        bestScore = prefs.getInt("best_score", 0)
        bestKillStreak = prefs.getInt("best_kill_streak", 0)
        winStreak = prefs.getInt("win_streak", 0)
        bestWinStreak = prefs.getInt("best_win_streak", 0)
        missionDay = prefs.getInt("mission_day", 0)
        missionProgress = (prefs.getString("mission_progress", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
            .let { if (it.size == Missions.COUNT) it else List(Missions.COUNT) { 0 } }
        missionClaimed = (prefs.getString("mission_claimed", "") ?: "").split(",").map { it == "1" }
            .let { if (it.size == Missions.COUNT) it else List(Missions.COUNT) { false } }
        bossesBeaten = prefs.getInt("bosses", 0)
        perfectLevels = prefs.getInt("perfect_levels", 0)
        coinsCollected = prefs.getInt("coins_collected", 0)
        longShots = prefs.getInt("long_shots", 0)
        comebacks = prefs.getInt("comebacks", 0)
        bestEndless = prefs.getInt("best_endless", 0)
        claimedAchievements = prefs.getStringSet("achievements", emptySet())?.toSet() ?: emptySet()
        trailId = prefs.getString("trail", "classic") ?: "classic"
        ownedTrails = prefs.getStringSet("owned_trails", setOf("classic"))?.toSet() ?: setOf("classic")
        trialOutfit = prefs.getString("trial_outfit", "") ?: ""
        trialBow = prefs.getString("trial_bow", "") ?: ""
        trialGames = prefs.getInt("trial_games", 0)
        rouletteDay = prefs.getInt("roulette_day", 0)
        rouletteVideoSpins = prefs.getInt("roulette_video_spins", 0)
        seenMechanics = prefs.getStringSet("seen_mechanics", emptySet())?.toSet() ?: emptySet()
        chestLastDay = prefs.getInt("chest_last_day", 0)
        chestRun = prefs.getInt("chest_run", 0)
        dailyLastDay = prefs.getInt("daily_last_day", 0)
        dailyRun = prefs.getInt("daily_run", 0)
        talents = (prefs.getString("talents", "0,0,0,0") ?: "0,0,0,0").split(",").mapNotNull { it.toIntOrNull() }
            .let { if (it.size == 4) it else List(4) { 0 } }
        danceId = prefs.getInt("dance", 0)
        ownedDances = (prefs.getStringSet("owned_dances", setOf("0")) ?: setOf("0")).mapNotNull { it.toIntOrNull() }.toSet() + 0
        targetBest = prefs.getInt("target_best", 0)
        // New players start with two of each special arrow.
        arrowStock = (prefs.getString("arrow_stock", "0,2,2,2,2") ?: "0,2,2,2,2").split(",").mapNotNull { it.toIntOrNull() }
            .let { if (it.size == 5) it else listOf(0, 2, 2, 2, 2) }
    }

    // ------------------------------------------------------------------ talents and celebrations

    fun talent(kind: Int): Int = talents.getOrElse(kind) { 0 }

    fun buyTalent(kind: Int): Boolean {
        val lv = talent(kind)
        if (lv >= Talents.MAX[kind]) return false
        if (!spend(Talents.cost(kind, lv))) return false
        talents = talents.toMutableList().also { it[kind] = lv + 1 }
        prefs.edit().putString("talents", talents.joinToString(",")).apply()
        return true
    }

    fun buyDance(id: Int, price: Int): Boolean {
        if (id in ownedDances) return true
        if (!spend(price)) return false
        ownedDances = ownedDances + id
        prefs.edit().putStringSet("owned_dances", ownedDances.map { it.toString() }.toSet()).apply()
        return true
    }

    fun selectDance(id: Int) {
        if (id !in ownedDances) return
        danceId = id
        prefs.edit().putInt("dance", id).apply()
    }

    /** True when [score] is a new target practice record. */
    fun onTargets(score: Int): Boolean {
        if (score <= targetBest) return false
        targetBest = score
        prefs.edit().putInt("target_best", score).apply()
        return true
    }

    // ------------------------------------------------------------------ special arrows

    fun arrowCount(kind: Int): Int = arrowStock.getOrElse(kind) { 0 }

    fun addArrows(kind: Int, n: Int) {
        if (kind !in 1..4 || n <= 0) return
        arrowStock = arrowStock.toMutableList().also { it[kind] += n }
        prefs.edit().putString("arrow_stock", arrowStock.joinToString(",")).apply()
    }

    fun useArrow(kind: Int): Boolean {
        if (kind !in 1..4 || arrowCount(kind) <= 0) return false
        arrowStock = arrowStock.toMutableList().also { it[kind] -= 1 }
        prefs.edit().putString("arrow_stock", arrowStock.joinToString(",")).apply()
        return true
    }

    fun buyArrows(kind: Int, n: Int, price: Int): Boolean {
        if (!spend(price)) return false
        addArrows(kind, n)
        return true
    }

    // ------------------------------------------------------------------ coins

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        coins += amount
        prefs.edit().putInt("coins", coins).apply()
    }

    fun spend(amount: Int): Boolean {
        if (coins < amount) return false
        coins -= amount
        prefs.edit().putInt("coins", coins).apply()
        return true
    }

    // ------------------------------------------------------------------ levels and records

    /** Records a won level: advances to the next one and updates records. */
    fun onLevelWon(number: Int, daily: Boolean) {
        if (!daily && number >= level) level = number + 1
        levelsWon += 1
        winStreak += 1
        bestWinStreak = maxOf(bestWinStreak, winStreak)
        prefs.edit()
            .putInt("level", level)
            .putInt("levels_won", levelsWon)
            .putInt("win_streak", winStreak)
            .putInt("best_win_streak", bestWinStreak)
            .apply()
    }

    /** Coins for finishing level [n]. */
    fun levelReward(n: Int): Int = (10 + n / 3).coerceAtMost(60)

    /** Returns true when [score] is a new best. */
    fun onScore(score: Int): Boolean {
        if (score <= bestScore) return false
        bestScore = score
        prefs.edit().putInt("best_score", score).apply()
        return true
    }

    fun onLevelLost() {
        levelsLost += 1
        winStreak = 0
        prefs.edit().putInt("levels_lost", levelsLost).putInt("win_streak", 0).apply()
    }

    fun onShots(hits: Int, heads: Int, killStreak: Int) {
        arrowsHit += hits
        headshots += heads
        bestKillStreak = maxOf(bestKillStreak, killStreak)
        prefs.edit().putInt("arrows_hit", arrowsHit).putInt("headshots", headshots)
            .putInt("best_kill_streak", bestKillStreak).apply()
    }

    // ------------------------------------------------------------------ settings

    fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putBoolean("sound", soundOn).apply()
    }

    fun toggleMusic() {
        musicOn = !musicOn
        prefs.edit().putBoolean("music", musicOn).apply()
    }

    fun changeDetailedScenery(on: Boolean) {
        detailedScenery = on
        prefs.edit().putBoolean("detailed_scenery", on).apply()
    }

    fun toggleVibration() {
        vibrationOn = !vibrationOn
        prefs.edit().putBoolean("vibration", vibrationOn).apply()
    }

    fun setNotifications(on: Boolean) {
        notificationsOn = on
        prefs.edit().putBoolean("notifications", on).apply()
    }

    fun markNotifAsked() {
        notifAsked = true
        prefs.edit().putBoolean("notif_asked", true).apply()
    }

    fun markTutorial(done: Boolean) {
        tutorialDone = done
        prefs.edit().putBoolean("tutorial_done", done).apply()
    }

    fun markReviewAsked(now: Long) {
        lastReviewAsk = now
        prefs.edit().putLong("last_review_ask", now).apply()
    }

    // ------------------------------------------------------------------ achievements & counters

    fun addStats(bosses: Int = 0, perfect: Int = 0, coins: Int = 0, long: Int = 0, comeback: Int = 0) {
        bossesBeaten += bosses
        perfectLevels += perfect
        coinsCollected += coins
        longShots += long
        comebacks += comeback
        prefs.edit().putInt("bosses", bossesBeaten).putInt("perfect_levels", perfectLevels)
            .putInt("coins_collected", coinsCollected).putInt("long_shots", longShots)
            .putInt("comebacks", comebacks).apply()
    }

    /** Returns true when [kills] is a new survival record. */
    fun onEndless(kills: Int): Boolean {
        if (kills <= bestEndless) return false
        bestEndless = kills
        prefs.edit().putInt("best_endless", kills).apply()
        return true
    }

    fun claimAchievement(id: String, reward: Int): Boolean {
        if (id in claimedAchievements) return false
        claimedAchievements = claimedAchievements + id
        prefs.edit().putStringSet("achievements", claimedAchievements).apply()
        addCoins(reward)
        return true
    }

    // ------------------------------------------------------------------ trails

    fun ownsTrail(id: String) = id in ownedTrails

    fun buyTrail(id: String, price: Int): Boolean {
        if (ownsTrail(id)) return true
        if (!spend(price)) return false
        giveTrail(id)
        return true
    }

    fun giveTrail(id: String) {
        ownedTrails = ownedTrails + id
        prefs.edit().putStringSet("owned_trails", ownedTrails).apply()
    }

    fun selectTrail(id: String) {
        if (!ownsTrail(id)) return
        trailId = id
        prefs.edit().putString("trail", id).apply()
    }

    // ------------------------------------------------------------------ skin trial

    fun startTrial(outfit: String, bow: String, games: Int) {
        trialOutfit = outfit
        trialBow = bow
        trialGames = games
        prefs.edit().putString("trial_outfit", outfit).putString("trial_bow", bow).putInt("trial_games", games).apply()
    }

    /** Called when a level starts: uses up one trial game. */
    fun useTrialGame() {
        if (trialGames <= 0) return
        trialGames -= 1
        if (trialGames == 0) { trialOutfit = ""; trialBow = "" }
        prefs.edit().putString("trial_outfit", trialOutfit).putString("trial_bow", trialBow).putInt("trial_games", trialGames).apply()
    }

    // ------------------------------------------------------------------ roulette

    const val ROULETTE_VIDEO_SPINS = 3

    fun rouletteFreeAvailable(): Boolean = rouletteDay != today()

    fun rouletteVideoLeft(): Int = if (rouletteDay == today()) (ROULETTE_VIDEO_SPINS - rouletteVideoSpins).coerceAtLeast(0) else ROULETTE_VIDEO_SPINS

    /** Marks a spin as used ([video] = paid with a rewarded video). */
    fun useRouletteSpin(video: Boolean) {
        if (rouletteDay != today()) {
            rouletteDay = today()
            rouletteVideoSpins = 0
        }
        if (video) rouletteVideoSpins += 1
        prefs.edit().putInt("roulette_day", rouletteDay).putInt("roulette_video_spins", rouletteVideoSpins).apply()
    }

    // ------------------------------------------------------------------ mechanics

    fun markMechanicSeen(name: String) {
        seenMechanics = seenMechanics + name
        prefs.edit().putStringSet("seen_mechanics", seenMechanics).apply()
    }

    // ------------------------------------------------------------------ outfits and bows

    fun ownsOutfit(id: String) = id in ownedOutfits
    fun ownsBow(id: String) = id in ownedBows

    fun buyOutfit(id: String, price: Int): Boolean {
        if (ownsOutfit(id)) return true
        if (!spend(price)) return false
        ownedOutfits = ownedOutfits + id
        prefs.edit().putStringSet("owned_outfits", ownedOutfits).apply()
        return true
    }

    fun buyBow(id: String, price: Int): Boolean {
        if (ownsBow(id)) return true
        if (!spend(price)) return false
        ownedBows = ownedBows + id
        prefs.edit().putStringSet("owned_bows", ownedBows).apply()
        return true
    }

    /** Unlocks without charging (mystery prize). */
    fun giveOutfit(id: String) {
        ownedOutfits = ownedOutfits + id
        prefs.edit().putStringSet("owned_outfits", ownedOutfits).apply()
    }

    fun giveBow(id: String) {
        ownedBows = ownedBows + id
        prefs.edit().putStringSet("owned_bows", ownedBows).apply()
    }

    fun selectOutfit(id: String) {
        if (!ownsOutfit(id)) return
        outfitId = id
        prefs.edit().putString("outfit", id).apply()
    }

    fun selectBow(id: String) {
        if (!ownsBow(id)) return
        bowId = id
        prefs.edit().putString("bow", id).apply()
    }

    // ------------------------------------------------------------------ daily missions

    /** Read-only, safe to call while drawing the UI. */
    fun todaysMissions(): List<Mission> = Missions.forDay(today())

    fun missionProgressOf(index: Int): Int = if (missionDay == today()) missionProgress.getOrElse(index) { 0 } else 0

    fun missionClaimedOf(index: Int): Boolean = missionDay == today() && missionClaimed.getOrElse(index) { false }

    /** Starts a fresh set of missions when the day changes. Call from events, not from drawing. */
    fun ensureMissionDay() {
        val t = today()
        if (missionDay == t) return
        missionDay = t
        missionProgress = List(Missions.COUNT) { 0 }
        missionClaimed = List(Missions.COUNT) { false }
        saveMissions()
    }

    private fun saveMissions() {
        prefs.edit()
            .putInt("mission_day", missionDay)
            .putString("mission_progress", missionProgress.joinToString(","))
            .putString("mission_claimed", missionClaimed.joinToString(",") { if (it) "1" else "0" })
            .apply()
    }

    /** Reports progress for missions of [type]. Returns the missions completed by this event. */
    fun missionEvent(type: MissionType, value: Int, asMax: Boolean = false): List<Mission> {
        if (value <= 0) return emptyList()
        ensureMissionDay()
        val missions = todaysMissions()
        val progress = missionProgress.toMutableList()
        val done = ArrayList<Mission>()
        var changed = false
        missions.forEachIndexed { i, m ->
            if (m.type != type || progress[i] >= m.target) return@forEachIndexed
            val next = (if (asMax) maxOf(progress[i], value) else progress[i] + value).coerceAtMost(m.target)
            if (next != progress[i]) {
                progress[i] = next
                changed = true
                if (next >= m.target) done.add(m)
            }
        }
        if (changed) {
            missionProgress = progress
            saveMissions()
        }
        return done
    }

    val claimableMissions: Int
        get() {
            val missions = todaysMissions()
            return missions.indices.count { missionProgressOf(it) >= missions[it].target && !missionClaimedOf(it) }
        }

    fun claimMission(index: Int): Int {
        ensureMissionDay()
        val missions = todaysMissions()
        val m = missions.getOrNull(index) ?: return 0
        if (missionProgress[index] < m.target || missionClaimed[index]) return 0
        missionClaimed = missionClaimed.toMutableList().also { it[index] = true }
        saveMissions()
        addCoins(m.reward)
        return m.reward
    }

    // ------------------------------------------------------------------ daily challenge

    const val DAILY_REWARD = 60
    var dailyLastDay by mutableIntStateOf(0)
        private set
    var dailyRun by mutableIntStateOf(0)
        private set

    fun dailyDoneToday(): Boolean = dailyLastDay == today()

    /** Days in a row with the challenge done (still counts until the end of today). */
    fun dailyStreak(): Int = if (dailyLastDay == today() || dailyLastDay == yesterday()) dailyRun else 0

    /** Coins for today's challenge: grows a little with the streak. */
    fun dailyReward(): Int = DAILY_REWARD + 10 * dailyStreak().coerceAtMost(7)

    fun onDailyWon(): Int {
        if (dailyDoneToday()) return 0
        val reward = dailyReward()
        dailyRun = if (dailyLastDay == yesterday()) dailyRun + 1 else 1
        dailyLastDay = today()
        prefs.edit().putInt("daily_last_day", dailyLastDay).putInt("daily_run", dailyRun).apply()
        addCoins(reward)
        return reward
    }

    // ------------------------------------------------------------------ daily chest

    val CHEST_REWARDS = listOf(20, 30, 40, 50, 70, 90, 150)

    fun chestAvailable(): Boolean = chestLastDay != today()

    fun chestIndex(): Int = if (chestLastDay == yesterday()) chestRun % CHEST_REWARDS.size else 0

    fun openChest(multiplier: Int): Int {
        if (!chestAvailable()) return 0
        val idx = chestIndex()
        val reward = CHEST_REWARDS[idx] * multiplier
        chestRun = if (chestLastDay == yesterday()) chestRun + 1 else 1
        chestLastDay = today()
        prefs.edit().putInt("chest_last_day", chestLastDay).putInt("chest_run", chestRun).apply()
        addCoins(reward)
        return reward
    }

    // ------------------------------------------------------------------ dates (yyyyMMdd, local time)

    fun today(): Int = dayOf(Calendar.getInstance())

    fun yesterday(): Int = dayOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })

    private fun dayOf(c: Calendar): Int =
        c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

/** Archer talents bought with coins. */
object Talents {
    const val HEALTH = 0
    const val GUIDE = 1
    const val SHIELD = 2
    const val COINS = 3
    val MAX = intArrayOf(5, 3, 2, 5)

    fun cost(kind: Int, level: Int): Int = when (kind) {
        HEALTH -> 300 + level * 350
        GUIDE -> 500 + level * 600
        SHIELD -> 900 + level * 1200
        else -> 250 + level * 300
    }
}

/** Victory celebrations (prices); 0 = the free jump. */
object Dances {
    val PRICES = intArrayOf(0, 600, 900, 1400)
}
