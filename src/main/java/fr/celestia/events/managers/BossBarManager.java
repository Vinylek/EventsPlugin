package fr.celestia.events.managers;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {
    
    private final JavaPlugin plugin;
    private final Map<String, BossBar> activeBossBars;
    private final Map<String, BukkitTask> bossBarTasks;
    
    public BossBarManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeBossBars = new HashMap<>();
        this.bossBarTasks = new HashMap<>();
    }
    
    public void createGameBossBar(String gameId, String gameType, String mapName, int duration) {
        // Supprimer une éventuelle bossbar existante
        removeBossBar(gameId);
        
        String title = "§6§l" + gameType + " §7- §e" + mapName + " §7| §aRejoignez maintenant!";
        BossBar bossBar = Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SEGMENTED_10, BarFlag.CREATE_FOG);
        
        // Afficher à tous les joueurs
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        
        bossBar.setVisible(true);
        activeBossBars.put(gameId, bossBar);
        
        // Task pour mettre à jour la bossbar
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            double progress = 1.0;
            final double decrement = 1.0 / (duration * 20); // Mise à jour 20 fois par seconde
            
            @Override
            public void run() {
                progress -= decrement;
                
                if (progress <= 0) {
                    progress = 0;
                    removeBossBar(gameId);
                    return;
                }
                
                bossBar.setProgress(progress);
                
                // Changer la couleur selon le temps restant
                if (progress < 0.3) {
                    bossBar.setColor(BarColor.RED);
                    bossBar.setTitle("§c§l" + gameType + " §7- §e" + mapName + " §7| §cFermeture imminente!");
                } else if (progress < 0.6) {
                    bossBar.setColor(BarColor.YELLOW);
                    bossBar.setTitle("§e§l" + gameType + " §7- §e" + mapName + " §7| §ePlus que quelques secondes!");
                }
            }
        }, 0L, 1L); // Mise à jour chaque tick
        
        bossBarTasks.put(gameId, task);
    }
    
    public void updateBossBarPlayers(String gameId) {
        BossBar bossBar = activeBossBars.get(gameId);
        if (bossBar != null) {
            // Mettre à jour les joueurs (enlever ceux qui sont dans le jeu)
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }
    }
    
    public void removeBossBar(String gameId) {
        BossBar bossBar = activeBossBars.remove(gameId);
        if (bossBar != null) {
            bossBar.setVisible(false);
            bossBar.removeAll();
        }
        
        BukkitTask task = bossBarTasks.remove(gameId);
        if (task != null) {
            task.cancel();
        }
    }
    
    public void addPlayerToBossBar(String gameId, Player player) {
        BossBar bossBar = activeBossBars.get(gameId);
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }
    
    public void removePlayerFromBossBar(String gameId, Player player) {
        BossBar bossBar = activeBossBars.get(gameId);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }
    
    public void cleanup() {
        // Nettoyer toutes les bossbars
        for (String gameId : activeBossBars.keySet()) {
            removeBossBar(gameId);
        }
        activeBossBars.clear();
        bossBarTasks.clear();
    }
    
    public boolean hasActiveBossBar(String gameId) {
        return activeBossBars.containsKey(gameId);
    }

    public void updateBossBarForPlayer(String gameId, Player player) {
        BossBar bossBar = activeBossBars.get(gameId);
        if (bossBar != null && !bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
            player.sendMessage("§6⚡ Un événement est en cours! §eRejoignez avec §6/game join");
        }
    }

    public void sendBossBarNotification(String gameId, Player player, String gameType, String mapName) {
        BossBar bossBar = activeBossBars.get(gameId);
        if (bossBar != null) {
            // Ajouter le joueur à la bossbar existante
            bossBar.addPlayer(player);
            
            // Message de notification
            player.sendMessage("§m-----------------------------------------------------");
            player.sendMessage("§6⚡ ÉVÉNEMENT EN COURS!");
            player.sendMessage("§eJeu: §6" + gameType);
            player.sendMessage("§eMap: §6" + mapName);
            player.sendMessage("§eRejoindre: §6/game join");
            player.sendMessage("§m-----------------------------------------------------");
        }
    }
}