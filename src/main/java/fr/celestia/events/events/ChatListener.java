package fr.celestia.events.events;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.game.Game;
import fr.celestia.events.managers.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Optional;

public class ChatListener implements Listener {
    
    private final EventsPlugin plugin;
    private final GameManager gameManager;
    
    public ChatListener(EventsPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        Optional<Game> playerGameOpt = gameManager.getPlayerGame(player);
        
        if (playerGameOpt.isPresent()) {
            // Le joueur est dans un événement
            Game game = playerGameOpt.get();
            event.setCancelled(true); // Annuler le message global
            
            // Formatter le message pour l'événement
            String formattedMessage = formatEventMessage(player, message, game);
            
            // Envoyer le message aux joueurs de l'événement seulement
            sendMessageToEventPlayers(formattedMessage, game);
            
        } else {
            // Le joueur n'est pas dans un événement
            // Filtrer les destinataires pour exclure les joueurs en événement
            event.getRecipients().removeIf(recipient -> 
                gameManager.getPlayerGame(recipient).isPresent()
            );
            
            // Formatter le message global
            event.setFormat("§8[§7Global§8] §f%s§8: §7%s");
        }
    }
    
    private String formatEventMessage(Player player, String message, Game game) {
        String prefix = getEventChatPrefix(game);
        return String.format("%s §f%s§8: §e%s", prefix, player.getName(), message);
    }
    
    private String getEventChatPrefix(Game game) {
        switch (game.getState()) {
            case WAITING:
                return "§8[§6⏳§8]";
            case STARTING:
                return "§8[§e⚡§8]";
            case RUNNING:
                return "§8[§c⚔§8]";
            case ENDING:
                return "§8[§d🏆§8]";
            default:
                return "§8[§6Event§8]";
        }
    }
    
    private void sendMessageToEventPlayers(String message, Game game) {
        // Envoyer aux joueurs actifs
        for (Player player : game.getActivePlayers()) {
            player.sendMessage(message);
        }
        
        // Envoyer aux spectateurs
        for (Player spectator : game.getOnlineSpectators()) {
            spectator.sendMessage(message);
        }
    }
}