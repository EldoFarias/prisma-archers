package com.farias.prismaarchers.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.farias.prismaarchers.R
import com.farias.prismaarchers.data.Progress

enum class Sfx { PICK, PLACE, CLEAR, COMBO, INVALID, GAME_OVER, RECORD, REVIVE, COIN, WHOOSH, DRAW, SHOOT, HIT, HEADSHOT, STICK }

/** Short sound effects through SoundPool (low latency, game audio attributes). One per app. */
class SoundManager private constructor(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.PICK to pool.load(context, R.raw.sfx_pick, 1),
        Sfx.PLACE to pool.load(context, R.raw.sfx_place, 1),
        Sfx.CLEAR to pool.load(context, R.raw.sfx_clear, 1),
        Sfx.COMBO to pool.load(context, R.raw.sfx_combo, 1),
        Sfx.INVALID to pool.load(context, R.raw.sfx_invalid, 1),
        Sfx.GAME_OVER to pool.load(context, R.raw.sfx_gameover, 1),
        Sfx.RECORD to pool.load(context, R.raw.sfx_record, 1),
        Sfx.REVIVE to pool.load(context, R.raw.sfx_revive, 1),
        Sfx.COIN to pool.load(context, R.raw.sfx_coin, 1),
        Sfx.WHOOSH to pool.load(context, R.raw.sfx_whoosh, 1),
        Sfx.DRAW to pool.load(context, R.raw.sfx_draw, 1),
        Sfx.SHOOT to pool.load(context, R.raw.sfx_shoot, 1),
        Sfx.HIT to pool.load(context, R.raw.sfx_hit, 1),
        Sfx.HEADSHOT to pool.load(context, R.raw.sfx_headshot, 1),
        Sfx.STICK to pool.load(context, R.raw.sfx_stick, 1),
    )

    fun play(sfx: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!Progress.soundOn) return
        val id = ids[sfx] ?: return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    companion object {
        @Volatile private var instance: SoundManager? = null

        fun get(context: Context): SoundManager =
            instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
    }
}
