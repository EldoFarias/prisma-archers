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
import kotlin.math.exp
import kotlin.random.Random

/** Weather of a duel (only looks; it doesn't change the physics). */
object Weather {
    const val CLEAR = 0
    const val RAIN = 1
    const val FOG = 2
    const val STORM = 3
}

/** A big title on screen ("DOUBLE HEADSHOT!") with its bonus points (and coins for scenery bonuses). */
class TitleShow(val id: Int, val textRes: Int, val bonus: Int, val coins: Int)

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
    /** Camera pulled back to show both archers (the default view; the button zooms in). */
    var overview by mutableStateOf(true)
        private set
    /** Changes at every new duel (drives the opening banner). */
    var duelId by mutableIntStateOf(0)
        private set
    /** 1–3 stars for the last win (by health left). */
    var stars by mutableIntStateOf(0)
        private set

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
    private val rnd = Random(System.nanoTime())

    /** Special arrow chosen for the next shot (ArrowKind; NORMAL = none). */
    var selectedArrow by mutableIntStateOf(ArrowKind.NORMAL)
        private set
    /** Last combo title (the UI animates each new id). */
    var title by mutableStateOf<TitleShow?>(null)
        private set
    private var titleId = 0
    private var titleShot = -1
    private var headStreak = 0
    private var longShown = false
    private var windShown = false
    /** Speech bubble over an archer (read by the painter). */
    var bubbleText = ""
        private set
    var bubbleAt = -10f
        private set
    var bubbleEnemy = true
        private set
    /** "Name • Lv 12" shown over the enemy. */
    var enemyLabel = ""
        private set
    /** String resource describing the boss ability (0 = not a boss). */
    var bossHintRes by mutableIntStateOf(0)
        private set
    var weather = Weather.CLEAR
        private set
    /** Lightning flash 1 → 0 and where the bolt falls (screen fraction). */
    var lightning = 0f
        private set
    var boltX = 0.5f
        private set
    private var nextLightning = 0f

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
        game.liquidGround = com.farias.prismaarchers.data.GroundStyle.liquid(world.ground)
        game.lavaGround = world.ground == com.farias.prismaarchers.data.GroundStyle.LAVA
        enemyOutfit = Looks.enemyOutfit(spec.number, spec.boss)
        enemyBow = Looks.enemyBow(spec.number)
        windKmh = game.duel.windKmh()
        val name = when (spec.power) {
            BossPower.SHIELD -> "Sir Brann"
            BossPower.FOCUS -> "Kenji"
            BossPower.DOUBLE -> "Bjorn"
            BossPower.HEAL -> "Aldric"
            else -> NAMES[(spec.number * 7 + (spec.seed % 5).toInt().coerceAtLeast(0)) % NAMES.size]
        }
        enemyLabel = name + " • " + app.getString(R.string.lv_x, spec.number)
        bossHintRes = when (spec.power) {
            BossPower.SHIELD -> R.string.boss_shield
            BossPower.FOCUS -> R.string.boss_focus
            BossPower.DOUBLE -> R.string.boss_double
            BossPower.HEAL -> R.string.boss_heal
            else -> 0
        }
        weather = if (phase == Phase.MENU) Weather.CLEAR else weatherFor(spec.number, world)
        nextLightning = game.clock + 3f
        lightning = 0f
        bubbleAt = -10f
        lookVersion++
    }

    private fun weatherFor(n: Int, w: World): Int {
        val r = ((n * 37 + 11) % 10 + 10) % 10
        val dry = w.air == 1 || w.scenery == 3 || w.scenery == 2 // snow, volcano, desert: no rain
        return when {
            r <= 1 -> if (dry) Weather.FOG else Weather.RAIN
            r == 2 -> Weather.FOG
            r == 3 && n >= 12 -> if (dry) Weather.FOG else Weather.STORM
            else -> Weather.CLEAR
        }
    }

    private fun say(enemy: Boolean, options: IntArray, chance: Float, delay: Float = 0f) {
        if (rnd.nextFloat() > chance) return
        bubbleText = app.getString(options[rnd.nextInt(options.size)])
        bubbleEnemy = enemy
        bubbleAt = game.clock + delay
    }

    private fun showTitle(res: Int, bonus: Int, coins: Int = 0) {
        score += bonus
        if (coins > 0) Progress.addCoins(coins)
        title = TitleShow(++titleId, res, bonus, coins)
        sound.play(Sfx.COMBO, 0.8f, 1.1f)
    }

    // ------------------------------------------------------------------ special arrows

    /** Picks (or drops) a special arrow; false when there are none left of that kind. */
    fun selectArrow(kind: Int): Boolean {
        if (selectedArrow == kind) {
            selectedArrow = ArrowKind.NORMAL
            return true
        }
        if (Progress.arrowCount(kind) <= 0) return false
        selectedArrow = kind
        sound.play(Sfx.PICK, 0.8f)
        return true
    }

    /** One special arrow of [kind] from a rewarded video, ready to shoot. */
    fun giveArrow(kind: Int) {
        Progress.addArrows(kind, 1)
        selectedArrow = kind
        sound.play(Sfx.REVIVE, 0.7f)
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
        selectedArrow = ArrowKind.NORMAL
        title = null
        titleShot = -1
        headStreak = 0
        longShown = false
        windShown = false
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
        game.overview = overview
        duelId++
        setOpponent(spec, worldLevel)
        say(true, if (spec.boss) TAUNT_BOSS else TAUNT_HELLO, if (spec.boss) 1f else 0.4f, 0.9f)
        MusicPlayer.select(MusicPlayer.trackFor(worldLevel, spec.boss || endless))
    }

    /** One extra shield for this duel (rewarded video): blocks the next enemy hit. */
    fun addShield() {
        shields += 1
        game.duel.shields += 1
        sound.play(Sfx.REVIVE, 0.8f)
    }

    fun toggleOverview() {
        overview = !overview
        game.overview = overview
        sound.play(Sfx.WHOOSH, 0.5f, if (overview) 0.8f else 1.2f)
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
        val kind = if (selectedArrow != ArrowKind.NORMAL && Progress.arrowCount(selectedArrow) > 0) selectedArrow else ArrowKind.NORMAL
        val shot = game.release(angle, power, kind)
        if (shot && !Progress.tutorialDone) Progress.markTutorial(true)
        if (shot && kind != ArrowKind.NORMAL) {
            Progress.useArrow(kind)
            selectedArrow = ArrowKind.NORMAL
        }
    }

    fun cancelAim() {
        drawSoundPlayed = false
        game.cancelAim()
    }

    // ------------------------------------------------------------------ frame

    fun frame(dt: Float) {
        if (paused) return
        game.update(dt)
        if (weather == Weather.STORM) {
            if (game.clock > nextLightning) {
                lightning = 1f
                boltX = 0.15f + rnd.nextFloat() * 0.7f
                nextLightning = game.clock + 5f + rnd.nextFloat() * 7f
                sound.play(Sfx.BOMB, 0.35f, 0.5f)
            }
            lightning *= exp(-dt * 4f)
        }
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
        game.overview = overview
        duelId++
        setOpponent(spec, 1 + kills * 10)
        say(true, TAUNT_HELLO, 0.5f, 0.9f)
        sound.play(Sfx.WHOOSH)
    }

    // ------------------------------------------------------------------ duel events

    private fun onDuelEvent(e: DuelEvent) {
        if (phase != Phase.PLAYING) return
        val duel = game.duel
        when (e) {
            is DuelEvent.Shot -> {
                sound.play(Sfx.SHOOT, 0.9f, if (e.byPlayer) 1f else 0.9f)
                if (e.kind == ArrowKind.FIRE) sound.play(Sfx.WHOOSH, 0.6f, 0.7f)
                if (e.byPlayer) haptic(Haptic.TICK)
                myTurn = false
            }
            is DuelEvent.Hit -> {
                val head = e.part == Part.HEAD
                sound.play(if (head) Sfx.HEADSHOT else Sfx.HIT, 1f, 0.92f + (Math.random() * 0.16).toFloat())
                if (e.kind == ArrowKind.ICE) sound.play(Sfx.GLASS, 0.7f, 1.5f)
                if (e.byPlayer) {
                    hits++
                    if (head) { heads++; headsThisDuel++ }
                    if (duel.spec.distance >= LONG_SHOT) longHits++
                    score += e.damage + if (head) HEAD_BONUS else 0
                    haptic(when {
                        e.kill -> Haptic.HEAVY
                        head -> Haptic.COMBO
                        e.part == Part.TORSO -> Haptic.CLEAR
                        else -> Haptic.PLACE
                    })
                    playerHitTitle(e, head)
                    if (!e.kill && duel.enemy.alive) say(true, TAUNT_OUCH, 0.35f, 0.3f)
                } else {
                    if (e.damage > 0) tookDamage = true
                    if (e.damage == 0 && shields > 0) shields--
                    haptic(Haptic.HEAVY)
                    if (e.damage > 0 && !e.kill) say(true, TAUNT_HIT, 0.5f, 0.2f)
                }
                if (e.kill) sound.play(Sfx.COMBO, 0.7f)
            }
            is DuelEvent.Miss -> {
                if (e.ground) sound.play(Sfx.STICK, 0.7f)
                if (e.byPlayer) {
                    if (titleShot != duel.shotsFired) headStreak = 0
                    say(true, TAUNT_DODGE, 0.45f, 0.2f)
                } else {
                    say(true, TAUNT_MISS, 0.35f, 0.2f)
                }
            }
            is DuelEvent.TurnStart -> {
                myTurn = e.player
                if (!e.player) sound.play(Sfx.DRAW, 0.45f, 0.9f)
            }
            is DuelEvent.Explode -> {
                sound.play(Sfx.BOMB, 1f)
                haptic(Haptic.HEAVY)
                if (e.byPlayer && e.damage > 0) {
                    hits++
                    score += e.damage
                }
                if (e.byPlayer && e.kill && titleShot != duel.shotsFired) {
                    titleShot = duel.shotsFired
                    showTitle(R.string.title_boom, 75)
                }
            }
            is DuelEvent.HelmetOff -> {
                sound.play(Sfx.HEADSHOT, 0.9f, 1.4f)
                showTitle(R.string.title_helmet, 40)
            }
            is DuelEvent.ShieldHit -> {
                sound.play(Sfx.GLASS, 0.9f, if (e.left == 0) 0.8f else 1.2f)
                haptic(Haptic.CLEAR)
                if (e.left > 0) say(true, TAUNT_DODGE, 0.6f, 0.2f)
            }
            is DuelEvent.Frozen -> sound.play(Sfx.GLASS, 0.8f, 1.6f)
            is DuelEvent.Thaw -> sound.play(Sfx.GLASS, 0.6f, 0.9f)
            is DuelEvent.Heal -> sound.play(Sfx.REVIVE, 0.6f, 1.3f)
            is DuelEvent.Fell -> {
                sound.play(Sfx.WHOOSH, 1f, 0.7f)
                haptic(Haptic.HEAVY)
                if (e.enemy) showTitle(R.string.title_fall, 100)
            }
            is DuelEvent.Bonus -> {
                sound.play(Sfx.COIN, 1f, 1.2f)
                if (e.kind == Prop.BIRD) showTitle(R.string.title_bird, 25, BIRD_COINS)
                else showTitle(R.string.title_balloon, 25, BALLOON_COINS)
            }
            is DuelEvent.Over -> onOver(e.playerWon)
        }
    }

    /** One title per shot, the best one that applies. */
    private fun playerHitTitle(e: DuelEvent.Hit, head: Boolean) {
        val duel = game.duel
        val shot = duel.shotsFired
        if (head && e.damage >= 100) headStreak++ else if (titleShot != shot) headStreak = 0
        if (titleShot == shot) return
        when {
            e.kill && shot == 1 -> showTitle(R.string.title_one_shot, 150)
            head && headStreak >= 3 -> showTitle(R.string.title_triple_head, 200)
            head && headStreak == 2 -> showTitle(R.string.title_double_head, 100)
            e.pierce -> showTitle(R.string.title_pierce, 75)
            duel.spec.distance >= LONG_SHOT && !longShown -> { longShown = true; showTitle(R.string.title_long, 50) }
            duel.spec.wind <= -1.5f && !windShown -> { windShown = true; showTitle(R.string.title_wind, 50) }
            else -> return
        }
        titleShot = shot
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
            stars = when {
                duel.playerHp >= 70 -> 3
                duel.playerHp >= 35 -> 2
                else -> 1
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
        const val BIRD_COINS = 5
        const val BALLOON_COINS = 10
        private val NAMES = arrayOf("Rolf", "Kira", "Bruno", "Yuki", "Ragnar", "Zara", "Igor", "Luna", "Hugo", "Nina", "Taro", "Vex", "Dante", "Mila", "Orion", "Sasha", "Fenn", "Ivy")
        private val TAUNT_HELLO = intArrayOf(R.string.taunt_hello_1, R.string.taunt_hello_2, R.string.taunt_hello_3)
        private val TAUNT_BOSS = intArrayOf(R.string.taunt_boss_1, R.string.taunt_boss_2, R.string.taunt_boss_3)
        private val TAUNT_HIT = intArrayOf(R.string.taunt_hit_1, R.string.taunt_hit_2, R.string.taunt_hit_3)
        private val TAUNT_MISS = intArrayOf(R.string.taunt_miss_1, R.string.taunt_miss_2, R.string.taunt_miss_3)
        private val TAUNT_DODGE = intArrayOf(R.string.taunt_dodge_1, R.string.taunt_dodge_2, R.string.taunt_dodge_3)
        private val TAUNT_OUCH = intArrayOf(R.string.taunt_ouch_1, R.string.taunt_ouch_2, R.string.taunt_ouch_3)

        /** Local calendar day number (same challenge for everyone on the same date). */
        fun dayIndex(): Int {
            val c = Calendar.getInstance()
            val offset = TimeZone.getDefault().getOffset(c.timeInMillis)
            return ((c.timeInMillis + offset) / 86_400_000L).toInt()
        }
    }
}
