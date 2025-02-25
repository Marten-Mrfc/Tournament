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
            try {
                val endTime = LocalDateTime.parse(tournament.endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                if (now.isAfter(endTime)) {
                    onTournamentEnd(tournament)
                }
            } catch (e: Exception) {
                println("Error processing tournament ${tournament.name}: ${e.message}")
            }
        }
    }

    private fun onTournamentEnd(tournament: Tournament) {
        val instance = dev.marten_mrfcyt.tournament.Tournament.instance
        Bukkit.getScheduler().runTask(instance, Runnable {
            instance.logger.info(createTournamentEndDisplay(tournament))
            val formattedName = tournament.name.replace(" ", "_").lowercase()
            PlayerProgress.getInstance().giveRewards(formattedName)
            tournamentManager.removeTournament(formattedName)
            PlayerProgress.getInstance().saveProgressToFile()
        })
    }

    fun stopChecking() {
        scheduler.shutdown()
    }

    fun createTournamentEndDisplay(tournament: Tournament): String {
        val topPlayers = TournamentManager().getTopPlayers(tournament.name)
        val winners = topPlayers.keys.take(5)
            .mapIndexed { index, player -> "${index + 1}. ${player.name}" }
            .joinToString("\n")

        return """
            
    ----------------------
    Tournament End
    ----------------------
    Name: ${tournament.name}
    Description: ${tournament.description}

    Winners:
    $winners

    Objective: ${tournament.objective}
    Target: ${tournament.target}
    ----------------------
    """.trimIndent()
    }
}