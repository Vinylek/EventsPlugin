package fr.celestia.events.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class TokenManager {
    
    private final JavaPlugin plugin;
    private File tokensFile;
    private FileConfiguration tokensConfig;
    
    public TokenManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadTokens() {
        tokensFile = new File(plugin.getDataFolder(), "tokens.yml");
        if (!tokensFile.exists()) {
            try {
                tokensFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Erreur lors de la création du fichier tokens.yml: " + e.getMessage());
            }
        }
        tokensConfig = YamlConfiguration.loadConfiguration(tokensFile);
    }
    
    private void saveTokens() {
        try {
            tokensConfig.save(tokensFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des tokens: " + e.getMessage());
        }
    }
    
    public int getTokens(Player player) {
        return tokensConfig.getInt(player.getUniqueId().toString(), 0);
    }
    
    public int getTokens(UUID uuid) {
        return tokensConfig.getInt(uuid.toString(), 0);
    }
    
    public void setTokens(Player player, int amount) {
        tokensConfig.set(player.getUniqueId().toString(), amount);
        saveTokens();
    }
    
    public void setTokens(UUID uuid, int amount) {
        tokensConfig.set(uuid.toString(), amount);
        saveTokens();
    }
    
    public void addTokens(Player player, int amount) {
        int current = getTokens(player);
        setTokens(player, current + amount);
    }
    
    public void removeTokens(Player player, int amount) {
        int current = getTokens(player);
        setTokens(player, Math.max(0, current - amount));
    }
    
    public boolean hasTokens(Player player, int amount) {
        return getTokens(player) >= amount;
    }
}