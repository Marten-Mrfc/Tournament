package dev.marten_mrfcyt.tournament.tournaments

import org.bukkit.Bukkit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TournamentEndChecker(private val tournamentManager: TournamentManager) {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun startChecking() {
        scheduler.scheduleAtFixedRate({
            checkTournaments()
        }, 0, 1, TimeUnit.MINUTES)
    }

    private fun checkTournaments() {
        val now = LocalDateTime.now()
        val tournaments = tournamentManager.getTournaments()
        tournaments.forEach { tournament ->
            val endTime = LocalDateTime.parse(tournament.endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            if (now.isAfter(endTime)) {
                onTournamentEnd(tournament)
            }
        }
    }

    private fun onTournamentEnd(tournament: Tournament) {
        Bukkit.getScheduler().runTask(dev.marten_mrfcyt.tournament.Tournament.instance, Runnable {
            PlayerProgress().giveRewards(tournament.name)
            tournamentManager.removeTournament(tournament.name)
        })
    }

    fun stopChecking() {
        scheduler.shutdown()
    }
}