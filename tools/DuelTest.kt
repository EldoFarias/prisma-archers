import com.farias.prismaarchers.game.*
import kotlin.math.abs

/** A player bot: solves the shot like the AI, with a human-like error. */
fun playDuel(spec: LevelSpec, playerErr: Float, seed: Int): Triple<Boolean, Int, Float> {
    val d = Duel(spec, seed = seed * 7919L + 1)
    val ev = ArrayList<DuelEvent>()
    val rnd = kotlin.random.Random(seed)
    var t = 0f
    var shots = 0
    while (!d.finished && t < 300f) {
        if (d.turn == Duel.Turn.PLAYER) {
            val (a, p) = Duel.solveShot(d.player.bowX(), d.player.bowY(), 1f, d.enemy.x[Ragdoll.CHEST], d.enemy.y[Ragdoll.CHEST], spec.wind)
            val learn = (1f - shots * 0.25f).coerceAtLeast(0.3f)
            pShots++
            d.releasePlayer(a + (rnd.nextFloat() * 2 - 1) * 0.12f * playerErr * learn, (p + (rnd.nextFloat() * 2 - 1) * 0.15f * playerErr * learn).coerceIn(0.1f, 1f), ev)
            shots++
        }
        ev.clear()
        d.step(1f / 120f, ev)
        for (e in ev) when (e) {
            is DuelEvent.Shot -> if (!e.byPlayer) eShots++
            is DuelEvent.Hit -> if (e.byPlayer) pHits++ else eHits++
            is DuelEvent.Miss -> if (e.byPlayer && e.ground) pMissGround++
            else -> Unit
        }
        t += 1f / 120f
    }
    return Triple(d.playerWon, shots, t)
}

var pHits = 0; var pShots = 0; var eHits = 0; var eShots = 0; var pMissGround = 0
fun table() {
    for (n in listOf(1, 3, 7, 13, 25, 37, 55, 77, 99, 20, 50)) {
        val spec = Levels.level(n)
        var wins = 0; var time = 0f
        pHits = 0; pShots = 0; eHits = 0; eShots = 0; pMissGround = 0
        val runs = 150
        for (s in 0 until runs) { val (w, _, tt) = playDuel(spec, 1f, s); if (w) wins++; time += tt }
        println("L$n${if (spec.boss) "B" else ""} dist=${"%.1f".format(spec.distance)} h=${"%.1f".format(spec.heightDiff)} wind=${"%.1f".format(spec.wind)} hp=${spec.enemyHp} skill=${"%.2f".format(spec.enemySkill)} -> wins ${wins * 100 / runs}%  player hit ${pHits * 100 / maxOf(1, pShots)}% (${pShots / runs} shots, ground ${pMissGround * 100 / maxOf(1,pShots)}%)  enemy hit ${eHits * 100 / maxOf(1, eShots)}%  ${"%.0f".format(time / runs)}s")
    }
}

fun main(args: Array<String>) {
    if (args.isNotEmpty()) { table(); return }
    // 1) A standing ragdoll stays put.
    val r = Ragdoll(0f, 0f, 1f)
    val plats = listOf(Platform(-3f, 3f, 0f))
    repeat(600) { r.step(1f / 120f, plats) }
    println("standing head y=${"%.2f".format(r.y[Ragdoll.HEAD])} feet y=${"%.2f".format(r.y[Ragdoll.L_FOOT])}")
    // 2) A killed ragdoll falls and settles on the platform.
    r.kill(6f, 2f, Ragdoll.HEAD)
    var t = 0f
    while (!r.settled(1f / 120f) && t < 10f) { r.step(1f / 120f, plats); t += 1f / 120f }
    val minY = (0 until Ragdoll.N).minOf { r.y[it] }
    val maxY = (0 until Ragdoll.N).maxOf { r.y[it] }
    println("dead settled after ${"%.2f".format(t)}s, y range ${"%.2f".format(minY)}..${"%.2f".format(maxY)} pelvis x=${"%.2f".format(r.x[Ragdoll.PELVIS])}")
    // 3) Solver accuracy: a perfect shot hits the chest.
    for (n in listOf(1, 10, 30, 60, 100)) {
        val spec = Levels.level(n)
        var wins = 0; var shots = 0; var time = 0f
        val runs = 40
        for (s in 0 until runs) { val (w, sh, tt) = playDuel(spec, 1f, s); if (w) wins++; shots += sh; time += tt }
        println("L$n dist=${"%.1f".format(spec.distance)} h=${"%.1f".format(spec.heightDiff)} wind=${"%.1f".format(spec.wind)} hp=${spec.enemyHp} skill=${"%.2f".format(spec.enemySkill)} -> player wins ${wins * 100 / runs}% avg shots ${shots / runs} avg ${"%.0f".format(time / runs)}s")
    }
}
