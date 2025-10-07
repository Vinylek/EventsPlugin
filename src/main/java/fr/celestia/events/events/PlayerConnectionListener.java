package fr.celestia.events.events;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.managers.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
    
    private final EventsPlugin plugin;
    private final GameManager gameManager;
    
    public PlayerConnectionListener(EventsPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Notifier le joueur des événements en cours
        gameManager.onPlayerJoin(player);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        gameManager.leaveGame(player);
    }
}