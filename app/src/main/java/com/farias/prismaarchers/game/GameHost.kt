package com.farias.prismaarchers.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farias.prismaarchers.R
import com.farias.prismaarchers.audio.Haptic
import com.farias.prismaarchers.audio.Haptics
import com.farias.prismaarchers.audio.MusicPlayer
import com.farias.prismaarchers.audio.Sfx
import com.farias.prismaarchers.audio.SoundManager
import com.farias.prismaarchers.data.Bow
import com.farias.prismaarchers.data.Looks
import com.farias.prismaarchers.data.MissionType
import com.farias.prismaarchers.data.Outfit
import com.farias.prismaarchers.data.Progress
import com.farias.prismaarchers.data.Trails
import com.farias.prismaarchers.data.World
import java.util.Calendar
import java.util.TimeZone

/**
 * Owns the [ArcheryGame] and mirrors what the UI needs as Compose state. Everything runs on the main
 * thread: the game view calls [frame] once per display frame.
 */
class GameHost(context: Context) {

    enum class Phase { MENU, PLAYING, DEAD, WON }

    private val app = context.applicationContext
    private val sound = SoundManager.get(app)
    val game = ArcheryGame(::onDuelEvent)

    var phase by mutableStateOf(Phase.MENU)
        private set
    var level by mutableIntStateOf(1)
        private set
    var daily by mutableStateOf(false)
        private set
    /** Survival run: one opponent after another until the player falls. */
    var endless by mutableStateOf(false)
        private set
    var boss by mutableStateOf(false)
        private set
    /** Score of the whole run (kept while the player keeps winning). */
    var score by mutableIntStateOf(0)
        private set
    private var runBase = 0
    var touched by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var revived by mutableStateOf(false)
        private set
    var newBest by mutableStateOf(false)
        private set
    var lastReward by mutableIntStateOf(0)
        private set
    /** Opponents beaten in this survival run. */
    var kills by mutableIntStateOf(0)
        private set
    var shields by mutableIntStateOf(0)
        private set
    /** Wind of the current duel (km/h, signed: + blows towards the enemy). */
    var windKmh by mutableIntStateOf(0)
        private set
    /** True while it's the player's turn to shoot (for the hint and the HUD). */
    var myTurn by mutableStateOf(true)
        private set
    /** String resource of a mechanic shown for the first time (0 = none). */
    var notice by mutableIntStateOf(0)
        private set
    var failsHere by mutableIntStateOf(0)
        private set
    /** Headshots in this duel (for the result panel). */
    var headsThisDuel by mutableIntStateOf(0)
        private set
    private var failsKey = -1

    // Looks (read by the painter)
    var world: World = Looks.world(1)
        private set
    var playerOutfit: Outfit = Looks.outfits[0]
        private set
    var playerBow: Bow = Looks.bows[0]
        private set
    var enemyOutfit: Outfit = Looks.outfits[1]
        private set
    var enemyBow: Bow = Looks.bows[0]
        private set
    var trailStyle = Trails.CLASSIC
        private set
    /** Changes whenever the scenery must be rebuilt. */
    var lookVersion = 0
        private set

    /** The game view stops drawing while a screen covers it completely. */
    var renderPaused = false

    private var hits = 0
    private var heads = 0
    private var tookDamage = false
    private var longHits = 0
    private var bestKillStreak = 0
    private var drawSoundPlayed = false
    private var nextOpponentAt = -1f
    private var endlessPaid = 0
    private var endlessSeed = 0L
    private var shownDuel: Duel? = null

    // ------------------------------------------------------------------ looks

    fun applyLooks(
        outfitId: String = if (Progress.trialGames > 0 && Progress.trialOutfit.isNotEmpty()) Progress.trialOutfit else Progress.outfitId,
        bowId: String = if (Progress.trialGames > 0 && Progress.trialBow.isNotEmpty()) Progress.trialBow else Progress.bowId,
        trailId: String = Progress.trailId,
    ) {
        playerOutfit = Looks.outfit(outfitId)
        playerBow = Looks.bow(bowId)
        trailStyle = Trails.byId(trailId).style
        lookVersion++
    }

    private fun setOpponent(spec: LevelSpec, worldLevel: Int) {
        world = Looks.world(worldLevel)
        enemyOutfit = Looks.enemyOutfit(spec.number, spec.boss)
        enemyBow = Looks.enemyBow(spec.number)
        windKmh = game.duel.windKmh()
        lookVersion++
    }

    // ------------------------------------------------------------------ commands

    fun showMenu() {
        phase = Phase.MENU
        paused = false
        applyLooks()
        game.startDemo()
        shownDuel = game.duel
        setOpponent(game.duel.spec, game.duel.spec.number)
        MusicPlayer.select(R.raw.music_chill)
    }

    fun play(n: Int, keepScore: Boolean = false) {
        daily = false
        endless = false
        level = n
        start(Levels.level(n), keepScore, n)
    }

    fun playDaily() {
        daily = true
        endless = false
        level = dayIndex()
        start(Levels.daily(dayIndex()), false, 30 + dayIndex() % 40)
    }

    fun playEndless() {
        daily = false
        endless = true
        level = 0
        kills = 0
        endlessPaid = 0
        endlessSeed = System.nanoTime()
        start(survivalSpec(0), false, 1)
    }

    fun retry() {
        when {
            endless -> playEndless()
            daily -> playDaily()
            else -> play(level)
        }
    }

    fun next() = play(Progress.level, keepScore = true)

    private fun survivalSpec(k: Int): LevelSpec = Levels.level(4 + k * 3, salt = endlessSeed + k)

    private fun start(spec: LevelSpec, keepScore: Boolean, worldLevel: Int) {
        Progress.ensureMissionDay()
        runBase = if (keepScore) score else 0
        score = runBase
        touched = false
        paused = false
        revived = false
        newBest = false
        shields = 0
        hits = 0
        heads = 0
        headsThisDuel = 0
        longHits = 0
        tookDamage = false
        bestKillStreak = 0
        nextOpponentAt = -1f
        myTurn = true
        boss = spec.boss
        val key = if (endless) -1 else if (daily) -2 else level
        if (key != failsKey) { failsKey = key; failsHere = 0 }
        notice = when {
            spec.wind != 0f && "wind" !in Progress.seenMechanics -> { Progress.markMechanicSeen("wind"); R.string.mech_wind }
            spec.enemyHelmet && "helmet" !in Progress.seenMechanics -> { Progress.markMechanicSeen("helmet"); R.string.mech_helmet }
            spec.heightDiff > 2.5f && "height" !in Progress.seenMechanics -> { Progress.markMechanicSeen("height"); R.string.mech_height }
            else -> 0
        }
        phase = Phase.PLAYING
        applyLooks()
        Progress.useTrialGame()
        game.startPlay(spec)
        setOpponent(spec, worldLevel)
        MusicPlayer.select(MusicPlayer.trackFor(worldLevel, spec.boss || endless))
    }

    /** One extra shield for this duel (rewarded video): blocks the next enemy hit. */
    fun addShield() {
        shields += 1
        game.duel.shields += 1
        sound.play(Sfx.REVIVE, 0.8f)
    }

    fun clearNotice() {
        notice = 0
    }

    fun revive() {
        if (phase != Phase.DEAD) return
        revived = true
        phase = Phase.PLAYING
        myTurn = true
        sound.play(Sfx.REVIVE)
        game.revive(REVIVE_HP)
    }

    fun pauseGame(p: Boolean) {
        if (phase != Phase.PLAYING && p) return
        paused = p
    }

    /** Ends a lost attempt for good (the player gave up the "continue"). */
    fun giveUp() {
        if (phase == Phase.DEAD) Progress.onLevelLost()
    }

    // ------------------------------------------------------------------ input

    fun aim(angle: Float, power: Float) {
        if (phase != Phase.PLAYING || paused) return
        if (!touched) {
            touched = true
            notice = 0
        }
        game.aim(angle, power)
        if (!drawSoundPlayed && power > 0.15f && game.duel.turn == Duel.Turn.PLAYER) {
            drawSoundPlayed = true
            sound.play(Sfx.DRAW, 0.7f)
        }
    }

    fun release(angle: Float, power: Float) {
        drawSoundPlayed = false
        if (phase != Phase.PLAYING || paused) {
            game.cancelAim()
            return
        }
        game.release(angle, power)
    }

    fun cancelAim() {
        drawSoundPlayed = false
        game.cancelAim()
    }

    // ------------------------------------------------------------------ frame

    fun frame(dt: Float) {
        if (paused) return
        game.update(dt)
        if (phase == Phase.MENU && game.duel !== shownDuel) {
            shownDuel = game.duel
            setOpponent(game.duel.spec, game.duel.spec.number)
        }
        if (nextOpponentAt > 0f && game.clock >= nextOpponentAt) {
            nextOpponentAt = -1f
            nextSurvivalOpponent()
        }
    }

    private fun nextSurvivalOpponent() {
        val hp = (game.duel.playerHp + SURVIVAL_HEAL).coerceAtMost(100)
        val spec = survivalSpec(kills)
        shields = 0
        myTurn = true
        game.nextOpponent(spec, hp, 0)
        setOpponent(spec, 1 + kills * 10)
        sound.play(Sfx.WHOOSH)
    }

    // ------------------------------------------------------------------ duel events

    private fun onDuelEvent(e: DuelEvent) {
        if (phase != Phase.PLAYING) return
        val duel = game.duel
        when (e) {
            is DuelEvent.Shot -> {
                sound.play(Sfx.SHOOT, 0.9f, if (e.byPlayer) 1f else 0.9f)
                if (e.byPlayer) haptic(Haptic.TICK)
                myTurn = false
            }
            is DuelEvent.Hit -> {
                val head = e.part == Part.HEAD
                sound.play(if (head) Sfx.HEADSHOT else Sfx.HIT, 1f, 0.92f + (Math.random() * 0.16).toFloat())
                if (e.byPlayer) {
                    hits++
                    if (head) { heads++; headsThisDuel++ }
                    if (duel.spec.distance >= LONG_SHOT) longHits++
                    score += e.damage + if (head) HEAD_BONUS else 0
                    haptic(if (e.kill) Haptic.COMBO else Haptic.PLACE)
                } else {
                    if (e.damage > 0) tookDamage = true
                    if (e.damage == 0 && shields > 0) shields--
                    haptic(Haptic.HEAVY)
                }
                if (e.kill) sound.play(Sfx.COMBO, 0.7f)
            }
            is DuelEvent.Miss -> if (e.ground) sound.play(Sfx.STICK, 0.7f)
            is DuelEvent.TurnStart -> {
                myTurn = e.player
                if (!e.player) sound.play(Sfx.DRAW, 0.45f, 0.9f)
            }
            is DuelEvent.Over -> onOver(e.playerWon)
        }
    }

    private fun onOver(won: Boolean) {
        val duel = game.duel
        if (won) {
            score += WIN_BONUS + duel.playerHp
            Progress.addStats(
                bosses = if (boss) 1 else 0,
                perfect = if (!tookDamage) 1 else 0,
                long = longHits,
                comeback = if (duel.playerHp <= 30) 1 else 0,
            )
            longHits = 0
            if (endless) {
                kills++
                bestKillStreak = maxOf(bestKillStreak, kills)
                sound.play(Sfx.RECORD, 0.8f)
                tookDamage = false
                nextOpponentAt = game.clock + 1.2f
                return
            }
            phase = Phase.WON
            sound.play(Sfx.RECORD)
            haptic(Haptic.COMBO)
            finishStats(true)
        } else {
            phase = Phase.DEAD
            sound.play(Sfx.GAME_OVER)
            haptic(Haptic.HEAVY)
            finishStats(false)
        }
    }

    private fun finishStats(won: Boolean) {
        if (!won) failsHere += 1
        Progress.onShots(hits, heads, if (endless) kills else bestKillStreak)
        Progress.missionEvent(MissionType.HITS, hits)
        Progress.missionEvent(MissionType.HEADSHOTS, heads)
        Progress.missionEvent(MissionType.SCORE, score, asMax = true)
        hits = 0
        heads = 0
        newBest = Progress.onScore(score)
        if (endless) {
            newBest = Progress.onEndless(kills) || newBest
            val total = kills * 25
            lastReward = (total - endlessPaid).coerceAtLeast(0)
            endlessPaid = total
            Progress.addCoins(lastReward)
            return
        }
        if (won) {
            Progress.onLevelWon(level, daily)
            Progress.missionEvent(MissionType.LEVELS, 1)
            Progress.missionEvent(MissionType.STREAK, Progress.winStreak, asMax = true)
            lastReward = if (daily) Progress.onDailyWon()
            else (Progress.levelReward(level) * (if (boss) 3 else 1) + headsThisDuel * 5).also { Progress.addCoins(it) }
        } else {
            lastReward = 0
        }
    }

    private fun haptic(kind: Haptic) {
        if (Progress.vibrationOn) Haptics.play(app, kind)
    }

    companion object {
        const val REVIVE_HP = 50
        const val SURVIVAL_HEAL = 25
        const val HEAD_BONUS = 50
        const val WIN_BONUS = 100
        const val LONG_SHOT = 24f

        /** Local calendar day number (same challenge for everyone on the same date). */
        fun dayIndex(): Int {
            val c = Calendar.getInstance()
            val offset = TimeZone.getDefault().getOffset(c.timeInMillis)
            return ((c.timeInMillis + offset) / 86_400_000L).toInt()
        }
    }
}
