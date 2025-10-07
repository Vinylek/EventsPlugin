package fr.celestia.events.game.types;

import fr.celestia.events.game.Game;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class TntRunGame implements GameType {
    
    private final Set<Location> ignitedTnt = new HashSet<>();
    private final Set<Location> removedTnt = new HashSet<>();
    private final Map<Player, Location> lastPlayerLocations = new HashMap<>();
    private final Map<Player, Integer> immobileTime = new HashMap<>();
    private BukkitTask fallCheckTask;
    private BukkitTask immobilityCheckTask;
    private final double blockOffset = 0.249;
    private final double fallDeathY = -55.0;
    
    @Override
    public void onStart(Game game) {
        game.broadcastMessage("§6§lTNT Run §ecommence !");
        game.broadcastMessage("§7Marchez sur les TNT pour les faire exploser !");
        game.broadcastMessage("§cNe tombez pas !");
        
        // Initialiser le tracking de position
        for (Player player : game.getActivePlayers()) {
            lastPlayerLocations.put(player, player.getLocation());
            immobileTime.put(player, 0);
        }
        
        // Démarrer les tâches spécifiques au TNT Run
        startFallCheckTask(game);
        startImmobilityCheckTask(game);
    }
    
    @Override
    public void onEnd(Game game) {
        // Arrêter les tâches
        if (fallCheckTask != null) {
            fallCheckTask.cancel();
            fallCheckTask = null;
        }
        if (immobilityCheckTask != null) {
            immobilityCheckTask.cancel();
            immobilityCheckTask = null;
        }
        
        // Nettoyer les données
        lastPlayerLocations.clear();
        immobileTime.clear();
        
        // Restaurer la map
        restoreMap(game);
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        player.sendMessage("§b§l=== TNT RUN ===");
        player.sendMessage("§e• La TNT disparait sous vos pieds !");
        player.sendMessage("§e• Ne tombez pas.");
        player.sendMessage("§e• Le dernier survivant gagne !");
        player.sendMessage("§aBonne chance !");
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        // Nettoyer les données du joueur
        lastPlayerLocations.remove(player);
        immobileTime.remove(player);
    }
    
    @Override
    public void onPlayerMove(Game game, PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Vérifier si le joueur est tombé
        if (player.getLocation().getY() < fallDeathY) {
            handlePlayerFall(game, player);
            return;
        }
        
        // Gérer l'activation des TNT
        if (player.getGameMode() == GameMode.ADVENTURE){
            handleTntActivation(game, player);
        }
    }
    
    @Override
    public void onPlayerDamage(Game game, EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // Annuler tous les dégâts sauf le void
            if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
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
    public boolean checkWinConditions(Game game) {
        List<Player> alivePlayers = game.getActivePlayers().stream()
            .filter(p -> game.getPlayers().contains(p.getUniqueId())) // Seulement les joueurs actifs
            .collect(java.util.stream.Collectors.toList());
        
        // Victoire si un seul joueur actif reste
        if (alivePlayers.size() == 1) {
            return true;
        }
        
        // Match nul si aucun joueur actif
        if (alivePlayers.isEmpty()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public void preparePlayer(Game game, Player player) {
        // Utiliser la préparation par défaut
        GameType.super.preparePlayer(game, player);
        
        // Ajouter des effets spécifiques au TNT Run si nécessaire
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.NIGHT_VISION, 
            Integer.MAX_VALUE, 0, false, false));
    }
    
    @Override
    public void resetPlayer(Game game, Player player) {
        // Utiliser le reset par défaut
        GameType.super.resetPlayer(game, player);
        
        // Retirer les effets spécifiques
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
    }
    
    @Override
    public String getDisplayName() {
        return "TNT Run";
    }
    
    @Override
    public String getDescription() {
        return "Marchez sur les TNT pour les faire exploser. Ne tombez pas !";
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public int getWaitingTime() {
        return 60;
    }
    
    // Méthodes spécifiques au TNT Run
    private void handleTntActivation(Game game, Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        
        double minX = playerLoc.getX() - blockOffset;
        double maxX = playerLoc.getX() + blockOffset;
        double minZ = playerLoc.getZ() - blockOffset;
        double maxZ = playerLoc.getZ() + blockOffset;
        
        for (double x = minX; x <= maxX; x += blockOffset) {
            for (double z = minZ; z <= maxZ; z += blockOffset) {
                Block block = world.getBlockAt(new Location(world, x, playerLoc.getY() - 1, z));
                Location loc = block.getLocation().add(0.5, 0, 0.5);
                
                if (block.getType() == Material.TNT && !ignitedTnt.contains(loc)) {
                    igniteTnt(game, block, loc);
                }
            }
        }
    }
    
    private void igniteTnt(Game game, Block block, Location loc) {
        ignitedTnt.add(loc);
        removedTnt.add(loc);
        
        block.setType(Material.BARRIER);
        
        TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
        tnt.setGravity(false);
        tnt.setVelocity(new Vector(0, 0, 0));
        tnt.setNoPhysics(true);
        tnt.setFuseTicks(40);
        tnt.setInvulnerable(true);
        tnt.setIsIncendiary(false);
        tnt.setYield(0);
        tnt.setSilent(true);
        
        game.getGameManager().getPlugin().getServer().getScheduler().runTaskLater(
            game.getGameManager().getPlugin(), () -> {
                Block actualBlock = loc.getBlock();
                if (actualBlock.getType() == Material.BARRIER) {
                    actualBlock.setType(Material.AIR);
                    ignitedTnt.remove(loc);
                    tnt.remove();
                }
            }, 14L);
    }
    
    private void handlePlayerFall(Game game, Player player) {
        if (!game.getPlayers().contains(player.getUniqueId())) return;
        
        player.sendTitle("§cÉliminé!", "§7Tu es tombé!", 10, 40, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Utiliser la nouvelle méthode d'élimination
        game.eliminatePlayer(player, "est tombé", true);
        
        // Effet supplémentaire
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.0f, 1.0f);
    }

    
    private void startFallCheckTask(Game game) {
        fallCheckTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                // Corrigeons cette ligne : vérifier l'état du jeu
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
                    if (fallCheckTask != null) {
                        fallCheckTask.cancel();
                    }
                    return;
                }
                
                for (Player player : game.getActivePlayers()) {
                    if (player.getLocation().getY() < fallDeathY) {
                        handlePlayerFall(game, player);
                    }
                }
            }, 0L, 20L);
    }

    private void startImmobilityCheckTask(Game game) {
        immobilityCheckTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                // Corrigeons cette ligne aussi
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
                    if (immobilityCheckTask != null) {
                        immobilityCheckTask.cancel();
                    }
                    return;
                }
                
                for (Player player : game.getActivePlayers()) {
                    Location currentLoc = player.getLocation();
                    Location lastLoc = lastPlayerLocations.get(player);
                    
                    if (lastLoc == null || hasMoved(currentLoc, lastLoc)) {
                        immobileTime.put(player, 0);
                        lastPlayerLocations.put(player, currentLoc);
                    } else {
                        int time = immobileTime.getOrDefault(player, 0) + 1;
                        immobileTime.put(player, time);
                        
                        if (time >= 80) { // 4 secondes
                            handleImmobilePlayer(game, player);
                            immobileTime.put(player, 0);
                        }
                    }
                }
            }, 0L, 1L);
    }
    
    private boolean hasMoved(Location loc1, Location loc2) {
        return loc1.getX() != loc2.getX() || 
               loc1.getY() != loc2.getY() || 
               loc1.getZ() != loc2.getZ() ||
               loc1.getYaw() != loc2.getYaw() || 
               loc1.getPitch() != loc2.getPitch();
    }
    
    private void handleImmobilePlayer(Game game, Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        
        List<Block> blocksBelow = getBlocksBelow(player);
        for (Block block : blocksBelow) {
            if (block.getType() == Material.TNT) {
                Location loc = block.getLocation().add(0.5, 0, 0.5);
                if (!ignitedTnt.contains(loc)) {
                    igniteTnt(game, block, loc);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    player.sendMessage("§c§lAttention ! §7Ne restez pas immobile trop longtemps !");
                }
            }
        }
    }
    
    private List<Block> getBlocksBelow(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        List<Block> blocks = new ArrayList<>();
        
        double minX = loc.getX() - blockOffset;
        double maxX = loc.getX() + blockOffset;
        double minZ = loc.getZ() - blockOffset;
        double maxZ = loc.getZ() + blockOffset;
        
        for (double x = minX; x <= maxX; x += blockOffset) {
            for (double z = minZ; z <= maxZ; z += blockOffset) {
                Block block = world.getBlockAt(new Location(world, x, loc.getY() - 0.1, z));
                if (block.getType() != Material.AIR) {
                    blocks.add(block);
                }
            }
        }
        
        return blocks.isEmpty() ? Collections.singletonList(
            world.getBlockAt(new Location(world, loc.getBlockX(), (int) Math.floor(loc.getY() - 0.1), loc.getBlockZ()))
        ) : blocks;
    }
    
    private void restoreMap(Game game) {
        game.getGameManager().getPlugin().getServer().getScheduler().runTaskLater(
            game.getGameManager().getPlugin(), () -> {
                for (Location loc : removedTnt) {
                    if (loc != null && loc.getWorld() != null) {
                        Block block = loc.getBlock();
                        if (block.getType() == Material.AIR || block.getType() == Material.BARRIER) {
                            block.setType(Material.TNT);
                        }
                    }
                }
                removedTnt.clear();
                ignitedTnt.clear();
            }, 20L);
    }
}