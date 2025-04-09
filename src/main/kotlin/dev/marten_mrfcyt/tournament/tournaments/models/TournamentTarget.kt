package dev.marten_mrfcyt.tournament.tournaments.models

enum class TournamentTarget {
    PLAYER,
    PROVINCE;

    companion object {
        fun fromString(value: String): TournamentTarget {
            return when (value.lowercase()) {
                "player" -> PLAYER
                "provincie" -> PROVINCE
                else -> throw IllegalArgumentException("Unknown tournament target: $value")
            }
        }

        fun toString(target: TournamentTarget): String {
            return when (target) {
                PLAYER -> "player"
                PROVINCE -> "provincie"
            }
        }
    }
}