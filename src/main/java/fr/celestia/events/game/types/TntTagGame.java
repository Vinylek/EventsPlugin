package fr.celestia.events.game.types;

import fr.celestia.events.game.Game;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TntTagGame implements GameType {
    
    private Player taggedPlayer = null;
    private BukkitTask explosionTask = null;
    private BukkitTask countdownTask = null;
    private int gameTime = 0;
    private int globalExplosionTime = 15;
    private long lastTagTime = 0;
    private final long TAG_COOLDOWN = 10; // 10 millisecondes de délai entre les passages
    private BukkitTask gameTimerTask = null;
    
    @Override
    public void onStart(Game game) {
        game.broadcastMessage("§6§lTNT Tag §ecommence !");
        game.broadcastMessage("§6Évitez d'être le joueur avec la TNT !");
        game.broadcastMessage("§6Si vous avez la TNT, passez-la à quelqu'un d'autre !");
        game.broadcastMessage("§e⚠ La TNT explose après 15 secondes !");
        
        // Démarrer le timer global du jeu
        startGameTimer(game);
        
        // Démarrer le compte à rebours initial pour choisir le premier taggé
        startInitialCountdown(game);
    }
    
    @Override
    public void onEnd(Game game) {
        // Arrêter toutes les tâches
        stopAllTasks();
        
        // Réinitialiser les variables
        taggedPlayer = null;
        gameTime = 0;
        globalExplosionTime = 15;
        lastTagTime = 0;
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        // Instructions
        player.sendMessage("§c§l=== TNT TAG  ===");
        player.sendMessage("§e• Évitez d'être le joueur avec la TNT !");
        player.sendMessage("§e• Si vous avez la TNT, passez-la à quelqu'un d'autre !");
        player.sendMessage("§e• La TNT explose après 15 secondes !");
        player.sendMessage("§aBonne chance !");
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        // Si le joueur qui quitte est le taggé, choisir un nouveau taggé
        if (player.equals(taggedPlayer)) {
            taggedPlayer = null;
            if (game.getActivePlayers().size() >= 2) {
                chooseRandomTaggedPlayer(game);
            }
        }
    }
    
    @Override
    public void onPlayerMove(Game game, PlayerMoveEvent event) {
        // Rien de spécial pour le mouvement dans le TNT Tag
    }
    
    @Override
    public void onPlayerDamage(Game game, EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // Annuler tous les dégâts environnementaux
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && 
                event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                event.setCancelled(true);
            }
        }
    }
    
    @Override
    public void onBlockBreak(Game game, BlockBreakEvent event) {
        // Empêcher la casse de blocs
        event.setCancelled(true);
    }

    @Override
    public void onBlockPlace(Game game, BlockPlaceEvent event) {
        event.setCancelled(true);
    }
    
    @Override
    public boolean checkWinConditions(Game game) {
        List<Player> activePlayers = game.getActivePlayers();
        
        // Victoire si un seul joueur actif reste
        if (activePlayers.size() == 1) {
            return true;
        }
        
        // Match nul si aucun joueur actif
        if (activePlayers.isEmpty()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public void preparePlayer(Game game, Player player) {
        // Utiliser la préparation par défaut
        GameType.super.preparePlayer(game, player);
        
        // Donner des instructions au joueur
        player.sendMessage("§6§lInstructions TNT Tag:");
        player.sendMessage("§e• §7Évitez d'être le joueur avec la TNT");
        player.sendMessage("§e• §cLa TNT explose après 15 secondes");
        player.sendMessage("§e• §6Tapez un joueur pour lui passer la TNT");
        player.sendMessage("§e• §aLe dernier survivant gagne !");
    }
    
    @Override
    public void resetPlayer(Game game, Player player) {
        // Utiliser le reset par défaut
        GameType.super.resetPlayer(game, player);
        
        // Retirer le casque TNT si le joueur l'avait
        player.getInventory().setHelmet(null);
        player.removePotionEffect(PotionEffectType.SPEED);
    }
    
    @Override
    public String getDisplayName() {
        return "TNT Tag";
    }
    
    @Override
    public String getDescription() {
        return "Évitez d'être le joueur avec la TNT ! Passez-la à quelqu'un d'autre avant qu'elle n'explose !";
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public int getWaitingTime() {
        return 120;
    }
    
    // Méthodes spécifiques au TNT Tag
    
    /**
     * Gère les dégâts entre joueurs (pour le tag)
     */
    public void handlePlayerAttack(Game game, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player victim = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        
        // Vérifier que les deux joueurs sont dans le jeu
        if (!game.getPlayers().contains(victim.getUniqueId()) || 
            !game.getPlayers().contains(damager.getUniqueId())) {
            return;
        }
        
        // Vérifier le délai entre les passages
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTagTime < TAG_COOLDOWN) {
            event.setCancelled(true);
            return;
        }
        
        // Si le damager est le joueur taggé, passer la TNT
        if (damager.equals(taggedPlayer)) {
            // Mettre à jour le temps du dernier passage
            lastTagTime = currentTime;
            
            // Passer la TNT
            setTaggedPlayer(game, victim);

            // Enlever la tnt dans la main de l'ancien joueur
            
            // Message à tous les joueurs
            game.broadcastMessage("§c" + damager.getName() + " §6a passé la TNT à §c" + victim.getName() + "§6!");
            
            
        }
        
        victim.setHealth(victim.getMaxHealth()); // Soigner le joueur
    }
    
    private void startGameTimer(Game game) {
        gameTimerTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
                    if (gameTimerTask != null) {
                        gameTimerTask.cancel();
                    }
                    return;
                }
                
                gameTime++;
                
                // Mettre à jour l'action bar pour les joueurs
                updateActionBars(game);
            }, 0L, 20L);
    }
    
    private void startInitialCountdown(Game game) {
        final int[] countdown = {5}; // 5 secondes avant de choisir le premier taggé
        
        globalExplosionTime = 15;
        lastTagTime = 0;
        
        game.broadcastMessage("§6Choix du premier joueur taggé dans §e5 §6secondes...");
        
        countdownTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (countdown[0] > 0) {
                    if (countdown[0] <= 3) {
                        game.broadcastMessage("§6Nouveau taggé dans §e" + countdown[0] + "§6...");
                    }
                    countdown[0]--;
                } else {
                    chooseRandomTaggedPlayer(game);
                    if (countdownTask != null) {
                        countdownTask.cancel();
                    }
                }
            }, 0L, 20L);
    }
    
    private void startNewTagCountdown(Game game) {
        final int[] countdown = {5}; // 5 secondes avant de choisir le nouveau taggé
        
        // Annuler l'ancien countdown s'il existe
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        
        game.broadcastMessage("§6Choix d'un nouveau joueur taggé dans §e5 §6secondes...");
        
        countdownTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (countdown[0] > 0) {
                    if (countdown[0] <= 3) {
                        game.broadcastMessage("§6Nouveau taggé dans §e" + countdown[0] + "§6...");
                    }
                    countdown[0]--;
                } else {
                    chooseRandomTaggedPlayer(game);
                    if (countdownTask != null) {
                        countdownTask.cancel();
                    }
                }
            }, 0L, 20L);
    }
    
    private void startExplosionCountdown(Game game) {
        // Annuler l'ancien compte à rebours s'il existe
        if (explosionTask != null) {
            explosionTask.cancel();
        }
        
        globalExplosionTime = 15;
        
        explosionTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (taggedPlayer == null || !taggedPlayer.isOnline() || 
                    !game.getPlayers().contains(taggedPlayer.getUniqueId())) {
                    if (explosionTask != null) {
                        explosionTask.cancel();
                    }
                    return;
                }
                
                if (globalExplosionTime <= 0) {
                    // Joueur explose
                    handlePlayerExplosion(game, taggedPlayer);
                    if (explosionTask != null) {
                        explosionTask.cancel();
                    }
                } else {
                    // Mettre à jour l'action bar
                    if (globalExplosionTime <= 5) {
                        taggedPlayer.playSound(taggedPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        taggedPlayer.spawnParticle(Particle.LAVA, taggedPlayer.getLocation(), 5);
                    }
                    globalExplosionTime--;
                }
            }, 0L, 20L);
    }
    
    private void chooseRandomTaggedPlayer(Game game) {
        List<Player> activePlayers = game.getActivePlayers();
        if (activePlayers.isEmpty()) return;
        
        // Choisir un joueur aléatoire parmi les actifs
        int randomIndex = new Random().nextInt(activePlayers.size());
        Player newTagged = activePlayers.get(randomIndex);
        setTaggedPlayer(game, newTagged);
        
        // Avertir tous les joueurs
        game.broadcastMessage("§c" + newTagged.getName() + " §6a la TNT sur la tête !");
        newTagged.sendTitle("§cTu as la TNT !", "§6Donne-la vite à quelqu'un !");
        
        // Son pour tous les joueurs
        for (Player player : game.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
        
        // Relancer le timer d'explosion
        startExplosionCountdown(game);
    }
    
    private void setTaggedPlayer(Game game, Player player) {
        // Retirer la TNT de l'ancien joueur taggé
        if (taggedPlayer != null && taggedPlayer.isOnline()) {
            taggedPlayer.getInventory().setHelmet(null);
            taggedPlayer.removePotionEffect(PotionEffectType.SPEED);
            taggedPlayer.getInventory().clear();
        }
        
        taggedPlayer = player;
        
        // Donner la TNT au nouveau joueur taggé
        ItemStack tntHelmet = new ItemStack(Material.TNT);
        ItemMeta meta = tntHelmet.getItemMeta();
        meta.setDisplayName("§cTNT");
        tntHelmet.setItemMeta(meta);
        player.getInventory().setHelmet(tntHelmet);
        
        // Effet de vitesse
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, false, false));
        player.give(new ItemStack(Material.TNT));

        // Son quand on devient taggé
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }
    
    private void handlePlayerExplosion(Game game, Player player) {
        if (!game.getPlayers().contains(player.getUniqueId())) return;
        
        // Éliminer le joueur (le mettre en spectateur)
        game.eliminatePlayer(player, "a explosé", false);
        
        // Effets d'explosion
        player.sendTitle("§cÉliminé!", "§7Tu as explosé!", 10, 40, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
        
        // Message à tous les joueurs
        game.broadcastMessage("§6" + player.getName() + " §ca explosé! " + 
                             game.getPlayerCount() + " joueur(s) restant(s).");
        
        // Choisir un nouveau taggé s'il reste assez de joueurs
        if (game.getActivePlayers().size() >= 2) {
            startNewTagCountdown(game);
        }
    }
    
    private void updateActionBars(Game game) {
        for (Player player : game.getOnlinePlayers()) {
            if (taggedPlayer != null && globalExplosionTime > 0) {
                player.sendActionBar("§cExplosion dans " + globalExplosionTime + "s!");
            } else {
                player.sendActionBar("§6TNT Tag - " + game.getActivePlayers().size() + " joueurs");
            }
        }
    }
    
    private void stopAllTasks() {
        if (explosionTask != null) {
            explosionTask.cancel();
            explosionTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
    }
    
    // Getters pour les informations du jeu
    public Player getTaggedPlayer() {
        return taggedPlayer;
    }
    
    public int getGameTime() {
        return gameTime;
    }
    
    public int getExplosionTime() {
        return globalExplosionTime;
    }
}