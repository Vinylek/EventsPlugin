package fr.celestia.events.game.types;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.celestia.events.game.Game;


public interface GameType {
    
    /**
     * Appelé quand le jeu démarre
     */
    void onStart(Game game);
    
    /**
     * Appelé quand le jeu se termine
     */
    void onEnd(Game game);
    
    /**
     * Appelé quand un joueur rejoint le jeu
     */
    void onPlayerJoin(Game game, Player player);
    
    /**
     * Appelé quand un joueur quitte le jeu
     */
    void onPlayerLeave(Game game, Player player);
    
    /**
     * Appelé quand un joueur bouge
     */
    void onPlayerMove(Game game, PlayerMoveEvent event);
    
    /**
     * Appelé quand un joueur subit des dégâts
     */
    void onPlayerDamage(Game game, EntityDamageEvent event);
    
    /**
     * Appelé quand un bloc est cassé
     */
    void onBlockBreak(Game game, BlockBreakEvent event);

    /**
     * Appelé quand un bloc est posé
     */
    void onBlockPlace(Game game, BlockPlaceEvent event);
    
    /**
     * Vérifie les conditions de victoire
     * @return true si le jeu doit se terminer
     */
    boolean checkWinConditions(Game game);
    
    /**
     * Prépare un joueur pour le jeu (peut être overridé)
     */
    default void preparePlayer(Game game, Player player) {
        // Préparation par défaut
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(effect -> 
            player.removePotionEffect(effect.getType()));
    }
    
    /**
     * Réinitialise un joueur après le jeu (peut être overridé)
     */
    default void resetPlayer(Game game, Player player) {
        // Reset par défaut
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(effect -> 
            player.removePotionEffect(effect.getType()));
    }
    
    /**
     * Nom affiché du type de jeu
     */
    String getDisplayName();
    
    /**
     * Description du jeu
     */
    String getDescription();
    
    /**
     * Nombre minimum de joueurs requis
     */
    default int getMinPlayers() {
        return 2;
    }
    
    /**
     * Durée d'attente avant le démarrage (secondes)
     */
    default int getWaitingTime() {
        return 60;
    }
}
