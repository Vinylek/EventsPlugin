package fr.celestia.events.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.Arrays;

public class PlayerData {
    private final Location location;
    private final GameMode gameMode;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final ItemStack[] extraContents;
    private final PotionEffect[] potionEffects;
    
    public PlayerData(Player player) {
        this.location = player.getLocation().clone();
        this.gameMode = player.getGameMode();
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exp = player.getExp();
        this.level = player.getLevel();
        
        // Sauvegarde de l'inventaire
        PlayerInventory inv = player.getInventory();
        this.inventory = inv.getContents().clone();
        this.armor = inv.getArmorContents().clone();
        this.extraContents = inv.getExtraContents().clone();
        
        // Sauvegarde des effets de potion
        this.potionEffects = player.getActivePotionEffects().toArray(new PotionEffect[0]);
    }
    
    public void restore(Player player) {
        if (player == null || !player.isOnline()) return;
        
        try {
            // Téléportation d'abord
            if (location != null && location.getWorld() != null) {
                player.teleport(location);
            }
            
            // Restauration des stats
            player.setGameMode(gameMode);
            player.setHealth(Math.min(health, player.getMaxHealth()));
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExp(exp);
            player.setLevel(level);
            
            // Restauration de l'inventaire
            PlayerInventory inv = player.getInventory();
            inv.clear();
            
            if (inventory != null) {
                inv.setContents(inventory);
            }
            if (armor != null) {
                inv.setArmorContents(armor);
            }
            if (extraContents != null) {
                inv.setExtraContents(extraContents);
            }
            
            // Nettoyage des effets existants
            player.getActivePotionEffects().forEach(effect -> 
                player.removePotionEffect(effect.getType()));
            
            // Restauration des effets
            if (potionEffects != null) {
                for (PotionEffect effect : potionEffects) {
                    player.addPotionEffect(effect);
                }
            }
            
            // Réinitialisation diverses
            player.setFireTicks(0);
            player.setFallDistance(0);
            player.setNoDamageTicks(0);
            
        } catch (Exception e) {
            player.getServer().getLogger().severe("Erreur lors de la restauration des données de " + player.getName() + ": " + e.getMessage());
        }
    }
    
    @Override
    public String toString() {
        return "PlayerData{location=" + location + ", gameMode=" + gameMode + ", health=" + health + "}";
    }
}