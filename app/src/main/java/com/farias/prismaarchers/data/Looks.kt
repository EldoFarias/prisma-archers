package com.farias.prismaarchers.data

import androidx.annotation.StringRes
import com.farias.prismaarchers.R

/** Head accessories, drawn by the painter. */
object Hat {
    const val NONE = 0
    const val CAP = 1
    const val HOOD = 2
    const val HELMET = 3
    const val CROWN = 4
    const val VIKING = 5
    const val BAND = 6
    const val PIRATE = 7
    const val WIZARD = 8
    const val COWBOY = 9
    const val SAMURAI = 10
    const val VISOR = 11
    const val SKULL = 12
    const val HALO = 13
}

/** An archer's outfit: body and limb colours, head accessory, glow. */
class Outfit(
    val id: String,
    @StringRes val nameRes: Int,
    val price: Int,
    val body: Long,
    val limbs: Long,
    val head: Long,
    val hat: Int,
    val hatColor: Long,
    val glow: Long,
)

/** Bow styles, drawn by the painter. */
object BowStyle {
    const val SIMPLE = 0
    const val RECURVE = 1
    const val COMPOUND = 2
    const val NEON = 3
    const val CRYSTAL = 4
    const val FIRE = 5
    const val DRAGON = 6
    const val LASER = 7
}

/** A bow: look and the colour of its arrows' trail. */
class Bow(
    val id: String,
    @StringRes val nameRes: Int,
    val price: Int,
    val wood: Long,
    val accent: Long,
    val style: Int,
    val trail: Long,
)

/** A world (every 10 levels): sky, scenery and the platforms' stone. */
class World(
    @StringRes val nameRes: Int,
    val skyTop: Long,
    val skyBottom: Long,
    val sun: Long,
    val far: Long,
    val near: Long,
    val rock: Long,
    val rockTop: Long,
    /** 0 none, 1 snow, 2 embers, 3 petals, 4 stars, 5 leaves. */
    val air: Int,
    /** 0 hills, 1 peaks, 2 dunes, 3 volcano, 4 castle, 5 forest. */
    val scenery: Int,
)

enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }

fun rarityOf(price: Int): Rarity = when {
    price < 600 -> Rarity.COMMON
    price < 1500 -> Rarity.RARE
    price < 3000 -> Rarity.EPIC
    else -> Rarity.LEGENDARY
}

object Looks {
    val outfits = listOf(
        Outfit("classic", R.string.outfit_classic, 0, 0x3B82F6, 0x1E3A8A, 0xFCD9B6, Hat.CAP, 0x2563EB, 0x60A5FA),
        Outfit("ranger", R.string.outfit_ranger, 300, 0x15803D, 0x14532D, 0xFCD9B6, Hat.HOOD, 0x166534, 0x86EFAC),
        Outfit("pirate", R.string.outfit_pirate, 500, 0xB91C1C, 0x1F2937, 0xF5C9A0, Hat.PIRATE, 0x111827, 0xFCA5A5),
        Outfit("cowboy", R.string.outfit_cowboy, 700, 0xB45309, 0x78350F, 0xF1C27D, Hat.COWBOY, 0x92400E, 0xFCD34D),
        Outfit("viking", R.string.outfit_viking, 900, 0x64748B, 0x334155, 0xF5D0A9, Hat.VIKING, 0x94A3B8, 0xE2E8F0),
        Outfit("ninja", R.string.outfit_ninja, 1200, 0x111827, 0x030712, 0xE5B98F, Hat.BAND, 0xDC2626, 0xF87171),
        Outfit("knight", R.string.outfit_knight, 1600, 0x9CA3AF, 0x4B5563, 0xD1D5DB, Hat.HELMET, 0xD1D5DB, 0xE5E7EB),
        Outfit("samurai", R.string.outfit_samurai, 2000, 0x991B1B, 0x450A0A, 0xF5C9A0, Hat.SAMURAI, 0x1F2937, 0xFCA5A5),
        Outfit("wizard", R.string.outfit_wizard, 2500, 0x6D28D9, 0x3B0764, 0xF5D0A9, Hat.WIZARD, 0x5B21B6, 0xC4B5FD),
        Outfit("robot", R.string.outfit_robot, 3200, 0x0EA5E9, 0x334155, 0xCBD5E1, Hat.VISOR, 0x22D3EE, 0x67E8F9),
        Outfit("skeleton", R.string.outfit_skeleton, 3800, 0xE5E7EB, 0xD1D5DB, 0xF9FAFB, Hat.SKULL, 0x111827, 0xF9FAFB),
        Outfit("king", R.string.outfit_king, 4500, 0x7C3AED, 0xFBBF24, 0xF5D0A9, Hat.CROWN, 0xFACC15, 0xFDE68A),
        Outfit("angel", R.string.outfit_angel, 5500, 0xF8FAFC, 0xE2E8F0, 0xFDE7D3, Hat.HALO, 0xFDE047, 0xFFFFFF),
    ).sortedBy { it.price }

    val bows = listOf(
        Bow("wood", R.string.bow_wood, 0, 0x92400E, 0xFDE68A, BowStyle.SIMPLE, 0xFDE68A),
        Bow("hunter", R.string.bow_hunter, 400, 0x78350F, 0x22C55E, BowStyle.RECURVE, 0x86EFAC),
        Bow("steel", R.string.bow_steel, 800, 0x6B7280, 0xE5E7EB, BowStyle.COMPOUND, 0xE5E7EB),
        Bow("neon", R.string.bow_neon, 1200, 0x0F172A, 0xE879F9, BowStyle.NEON, 0xF0ABFC),
        Bow("crystal", R.string.bow_crystal, 1800, 0x67E8F9, 0xFFFFFF, BowStyle.CRYSTAL, 0xA5F3FC),
        Bow("fire", R.string.bow_fire, 2600, 0x7C2D12, 0xFB923C, BowStyle.FIRE, 0xFB923C),
        Bow("gold", R.string.bow_gold, 3400, 0xCA8A04, 0xFEF08A, BowStyle.RECURVE, 0xFDE047),
        Bow("dragon", R.string.bow_dragon, 4500, 0x14532D, 0xF43F5E, BowStyle.DRAGON, 0xF43F5E),
        Bow("laser", R.string.bow_laser, 6000, 0x111827, 0x22D3EE, BowStyle.LASER, 0x22D3EE),
    ).sortedBy { it.price }

    val worlds = listOf(
        World(R.string.world_meadow, 0x7DD3FC, 0xE0F2FE, 0xFFF7D6, 0x93C5FD, 0x4ADE80, 0x78716C, 0x22C55E, 5, 0),
        World(R.string.world_desert, 0xFDBA74, 0xFEF3C7, 0xFFFBEB, 0xF59E0B, 0xD97706, 0xA16207, 0xFCD34D, 0, 2),
        World(R.string.world_snow, 0x93C5FD, 0xF1F5F9, 0xFFFFFF, 0xCBD5E1, 0xE2E8F0, 0x64748B, 0xF8FAFC, 1, 1),
        World(R.string.world_volcano, 0x450A0A, 0xF97316, 0xFDE047, 0x7F1D1D, 0x292524, 0x1C1917, 0xEA580C, 2, 3),
        World(R.string.world_night, 0x0B1023, 0x312E81, 0xF1F5F9, 0x1E1B4B, 0x0F172A, 0x334155, 0x6366F1, 4, 5),
        World(R.string.world_sakura, 0xF9A8D4, 0xFDF2F8, 0xFFF1F2, 0xF472B6, 0xBE185D, 0x6B5563, 0xF9A8D4, 3, 0),
        World(R.string.world_castle, 0xFB923C, 0xFDE68A, 0xFFF7ED, 0x9A3412, 0x7C2D12, 0x57534E, 0x84CC16, 0, 4),
        World(R.string.world_jungle, 0x14532D, 0xBBF7D0, 0xF0FDF4, 0x166534, 0x14532D, 0x44403C, 0x16A34A, 5, 5),
    )

    fun outfit(id: String) = outfits.firstOrNull { it.id == id } ?: outfits[0]
    fun bow(id: String) = bows.firstOrNull { it.id == id } ?: bows[0]
    fun world(level: Int) = worlds[((level - 1).coerceAtLeast(0) / 10) % worlds.size]

    /** The enemy's look for level [n] (bosses wear the knight/samurai/king armour). */
    fun enemyOutfit(n: Int, boss: Boolean): Outfit {
        if (boss) return listOf(outfit("knight"), outfit("samurai"), outfit("viking"), outfit("king"))[(n / 10) % 4]
        val pool = outfits.filter { it.id != "angel" && it.id != "king" }
        return pool[(n * 7) % pool.size]
    }

    fun enemyBow(n: Int): Bow = bows[(n * 5 / 3) % bows.size]
}
