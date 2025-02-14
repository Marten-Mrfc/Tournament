package dev.marten_mrfcyt.tournament

import com.earth2me.essentials.Essentials
import dev.marten_mrfcyt.tournament.menus.GuiHandler
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentEndChecker
import dev.marten_mrfcyt.tournament.tournaments.TournamentHandler
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import lirand.api.architecture.KotlinPlugin
import org.bukkit.Bukkit
import org.bukkit.event.Listener

class Tournament : KotlinPlugin() {
    companion object {
        lateinit var instance: Tournament
        lateinit var essentials: Essentials
    }

    private lateinit var tournamentEndChecker: TournamentEndChecker

    override fun onEnable() {
        logger.info("-------------------------------")
        logger.info("--- Tournaments is starting ---")
        instance = this
        config.options().copyDefaults()
        saveDefaultConfig()
        essentials = (server.pluginManager.getPlugin("Essentials") as Essentials?)!!
        logger.info("Registering commands")
        tournamentCommands()
        logger.info("Commands registered successfully!")
        logger.info("Registering events")
        registerEvents(
            TournamentHandler(TournamentManager()),
            GuiHandler(TournamentManager())
        )
        logger.info("Loaded ${TournamentManager().getTournaments().size} tournaments")

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, {
            TournamentHandler.locations.clear()
        }, 100, 100)

        // Start the TournamentEndChecker
        logger.info("Starting TournamentEndChecker")
        tournamentEndChecker = TournamentEndChecker(TournamentManager())
        tournamentEndChecker.startChecking()
        logger.info("TournamentEndChecker started successfully!")
        logger.info("--- Tournaments has started ---")
        logger.info("-------------------------------")
    }

    override fun onDisable() {
        PlayerProgress().saveProgressToFile()
        logger.info("Tournaments has stopped")

        // Stop the TournamentEndChecker
        tournamentEndChecker.stopChecking()
    }

    private fun registerEvents(vararg listeners: Listener) {
        val pluginManager = Bukkit.getPluginManager()
        listeners.forEach { listener ->
            pluginManager.registerEvents(listener, this)
        }
        logger.info("${listeners.size} events registered successfully!")
    }
}