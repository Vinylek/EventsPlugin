package fr.celestia.events.events;

import fr.celestia.events.game.types.BuildBattleGame;
import fr.celestia.events.game.types.FFAGame;
import fr.celestia.events.game.types.SpleefGame;
import fr.celestia.events.game.types.TntTagGame;
import fr.celestia.events.EventsPlugin;
import fr.celestia.events.game.Game;
import fr.celestia.events.managers.GameManager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Optional;

public class GameListener implements Listener {
    
    private final EventsPlugin plugin;
    private final GameManager gameManager;
    
    public GameListener(EventsPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);
        Location to = event.getTo();
        Location from = event.getFrom();
        
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            game.handlePlayerMove(event);

            
        }
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            Optional<Game> gameOpt = gameManager.getPlayerGame(player);
            
            if (gameOpt.isPresent()) {
                Game game = gameOpt.get();
                game.handlePlayerDamage(event);
                
                

            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player victim = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        
        Optional<Game> victimGameOpt = gameManager.getPlayerGame(victim);
        Optional<Game> damagerGameOpt = gameManager.getPlayerGame(damager);
        
        // Si les deux joueurs sont dans le même événement
        if (victimGameOpt.isPresent() && damagerGameOpt.isPresent() && 
            victimGameOpt.get().equals(damagerGameOpt.get())) {
            
            Game game = victimGameOpt.get();
            
            // Empêcher le PVP pendant l'attente et le démarrage
            if (game.getState() == fr.celestia.events.game.GameState.WAITING || 
                game.getState() == fr.celestia.events.game.GameState.STARTING) {
                
                event.setCancelled(true);
                damager.sendMessage("§cLe PVP est désactivé pendant la phase d'attente!");
                return;
            }
            
            // Si c'est un jeu TNT Tag, gérer le tag
            if (game.getGameType() instanceof TntTagGame) {
                TntTagGame tntTagGame = (TntTagGame) game.getGameType();
                tntTagGame.handlePlayerAttack(game, event);
            }
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);
        
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            game.handleBlockBreak(event);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);
        
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            game.handleBlockPlace(event);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);
        
        if (gameOpt.isPresent() && gameOpt.get().getGameType() instanceof BuildBattleGame) {
            Game game = gameOpt.get();
            BuildBattleGame buildBattle = (BuildBattleGame) game.getGameType();
            ItemStack item = event.getItem();
            
            if (item != null && buildBattle.isTimeToVote() && item.getType().toString().contains("WOOL")) {
                buildBattle.handleVote(player, item);
                event.setCancelled(true);
            }
        }
    }

    // FFA
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);

        if (gameOpt.isPresent() && gameOpt.get().getGameType() instanceof fr.celestia.events.game.types.FFAGame) {
            Game game = gameOpt.get();
            FFAGame ffa = (FFAGame) game.getGameType();

            event.setDeathMessage(null); // Désactiver le message par défaut
            event.getDrops().clear();    // Pas de loot d'inventaire
            event.setCancelled(true);
            player.setHealth(player.getMaxHealth());
            
            ffa.handlePlayerDeath(game, player, player.getKiller());
            
        }
    }




    // SPLEEF

    @EventHandler
    public void onEntityCollision(org.bukkit.event.entity.EntityDamageEvent event) {
        // Vérifier si c'est un ArmorStand qui subit des dégâts
        if (!(event.getEntity() instanceof org.bukkit.entity.ArmorStand)) {
            return;
        }

    }





    @EventHandler
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        
        Snowball snowball = (Snowball) event.getEntity();
        
        // Vérifier si le lanceur est un joueur
        if (!(snowball.getShooter() instanceof Player)) return;
        
        Player player = (Player) snowball.getShooter();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);
        
        if (gameOpt.isPresent() && gameOpt.get().getGameType() instanceof SpleefGame) {
            SpleefGame spleefGame = (SpleefGame) gameOpt.get().getGameType();
            spleefGame.handleSnowballLaunch(gameOpt.get(), player, snowball);
        }
    }

    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        
        Snowball snowball = (Snowball) event.getEntity();
        
        // Vérifier si le lanceur est un joueur
        if (!(snowball.getShooter() instanceof Player)) return;
        
        Player player = (Player) snowball.getShooter();
        Optional<Game> gameOpt = gameManager.getPlayerGame(player);
        
        if (gameOpt.isPresent() && gameOpt.get().getGameType() instanceof SpleefGame) {
            SpleefGame spleefGame = (SpleefGame) gameOpt.get().getGameType();
            
            // Gérer l'impact
            org.bukkit.entity.Entity hitEntity = event.getHitEntity();
            if (hitEntity != null) {
                spleefGame.handleSnowballImpact(gameOpt.get(), snowball, hitEntity);
            } else if (event.getHitBlock() != null) {
                // Impact sur un bloc
                spleefGame.handleSnowballImpact(gameOpt.get(), snowball, null);
            }
        }
    }
    
}