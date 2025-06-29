package dev.marten_mrfcyt.tournament.tournaments.models

enum class ObjectiveType {
    HAK_BLOK,
    DOOD_MOB;

    companion object {
        fun fromString(value: String): ObjectiveType {
            return when (value.lowercase()) {
                "hak_block", "hak blok" -> HAK_BLOK
                "dood_mob", "dood mob" -> DOOD_MOB
                else -> throw IllegalArgumentException("Unknown objective type: $value")
            }
        }

        fun toString(type: ObjectiveType): String {
            return when (type) {
                HAK_BLOK -> "Hak Blok"
                DOOD_MOB -> "Dood Mob"
            }
        }
    }
}