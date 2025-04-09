package dev.marten_mrfcyt.tournament

import com.earth2me.essentials.Essentials
import dev.marten_mrfcyt.tournament.rewards.RewardsManager
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentEndChecker
import dev.marten_mrfcyt.tournament.tournaments.TournamentHandler
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import mlib.api.architecture.KotlinPlugin
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
        super.onEnable()
        config.options().copyDefaults()
        saveDefaultConfig()
        essentials = (server.pluginManager.getPlugin("Essentials") as Essentials?)!!

        // Initialize the rewards manager
        logger.info("Loading rewards data...")
        RewardsManager.getInstance().loadRewards()

        logger.info("Registering commands")
        tournamentCommands()
        logger.info("Commands registered successfully!")
        logger.info("Registering events")
        registerEvents(
            TournamentHandler(TournamentManager()),
        )
        logger.info("Loaded ${TournamentManager().getTournaments().size} tournaments")

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, {
            TournamentHandler.locations.clear()
        }, 100, 500)

        logger.info("Starting TournamentEndChecker")
        tournamentEndChecker = TournamentEndChecker(TournamentManager())
        tournamentEndChecker.startChecking()
        logger.info("TournamentEndChecker started successfully!")
        logger.info("--- Tournaments has started ---")
        logger.info("-------------------------------")
    }

    override fun onDisable() {
        logger.info("Saving rewards data...")
        RewardsManager.getInstance().saveRewards()

        // Save player progress
        PlayerProgress.getInstance().saveProgressToFile()
        logger.info("Tournament data saved successfully")

        tournamentEndChecker.stopChecking()
        logger.info("Tournaments has stopped")
    }

    private fun registerEvents(vararg listeners: Listener) {
        val pluginManager = Bukkit.getPluginManager()
        listeners.forEach { listener ->
            pluginManager.registerEvents(listener, this)
        }
        logger.info("${listeners.size} events registered successfully!")
    }
}