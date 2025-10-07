package fr.celestia.events.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ConfigManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    private File locationsFile;
    private FileConfiguration locationsConfig;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        // Configuration principale
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        
        // Fichier des locations
        locationsFile = new File(plugin.getDataFolder(), "locations.yml");
        if (!locationsFile.exists()) {
            plugin.saveResource("locations.yml", false);
        }
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);
    }
    
    public boolean saveLocations() {
        try {
            locationsConfig.save(locationsFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des locations: " + e.getMessage());
            return false;
        }
    }
    
    // Getters pour la config
    public int getWaitingTimer() {
        return config.getInt("waiting-timer", 60);
    }
    
    public int getMinPlayers() {
        return config.getInt("min-players", 2);
    }
    
    public int getMaxPlayers() {
        return config.getInt("max-players", 16);
    }
    
    public int getCeremonyTimer() {
        return config.getInt("ceremony-timer", 10);
    }
    
    public String getMessage(String path) {
        return config.getString("messages." + path, "Message non configuré: " + path);
    }
    
    public List<String> getAvailableGames() {
        return config.getStringList("available-games");
    }
    
    public String getWinCommand() {
        return config.getString("win-command", "eco give %player% 1000");
    }
    
    // Getters pour les locations
    public FileConfiguration getLocationsConfig() {
        return locationsConfig;
    }

    
    public void setLocation(String path, org.bukkit.Location location) {
        locationsConfig.set(path + ".world", location.getWorld().getName());
        locationsConfig.set(path + ".x", location.getX());
        locationsConfig.set(path + ".y", location.getY());
        locationsConfig.set(path + ".z", location.getZ());
        locationsConfig.set(path + ".yaw", location.getYaw());
        locationsConfig.set(path + ".pitch", location.getPitch());
        saveLocations();
    }
    
    public org.bukkit.Location getLocation(String path) {
        if (!locationsConfig.contains(path + ".world")) {
            return null;
        }
        
        String worldName = locationsConfig.getString(path + ".world");
        double x = locationsConfig.getDouble(path + ".x");
        double y = locationsConfig.getDouble(path + ".y");
        double z = locationsConfig.getDouble(path + ".z");
        float yaw = (float) locationsConfig.getDouble(path + ".yaw");
        float pitch = (float) locationsConfig.getDouble(path + ".pitch");
        
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        
        return new org.bukkit.Location(world, x, y, z, yaw, pitch);
    }

    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Récupère la liste des maps disponibles pour un type d'event spécifique
     * depuis la structure arenas dans locations.yml
     */
    public List<String> getAvailableMaps(String gameType) {
        // Chemin dans locations.yml: arenas.ffa, arenas.blockparty, etc.
        String configPath = "arenas." + gameType.toLowerCase();
        
        if (!locationsConfig.contains(configPath)) {
            plugin.getLogger().warning("Aucune map configurée pour l'event: " + gameType);
            plugin.getLogger().warning("Chemin recherché: " + configPath);
            return new ArrayList<>();
        }
        
        // Récupérer toutes les clés sous arenas.gametype (desert, color, etc.)
        List<String> maps = new ArrayList<>();
        for (String mapName : locationsConfig.getConfigurationSection(configPath).getKeys(false)) {
            maps.add(mapName);
        }
        
        plugin.getLogger().info("Maps trouvées pour " + gameType + ": " + maps);
        return maps;
    }
    
    /**
     * Récupère toutes les maps de tous les events
     */
    public Map<String, List<String>> getAllMaps() {
        Map<String, List<String>> allMaps = new HashMap<>();
        
        if (!locationsConfig.contains("arenas")) {
            plugin.getLogger().warning("Aucune arène configurée dans la section 'arenas'");
            return allMaps;
        }
        
        // Parcourir tous les types d'events sous "arenas"
        for (String gameType : locationsConfig.getConfigurationSection("arenas").getKeys(false)) {
            List<String> maps = new ArrayList<>();
            String gameTypePath = "arenas." + gameType;
            
            // Récupérer toutes les maps pour ce type d'event
            for (String mapName : locationsConfig.getConfigurationSection(gameTypePath).getKeys(false)) {
                maps.add(mapName);
            }
            
            allMaps.put(gameType, maps);
        }
        
        return allMaps;
    }
    
    /**
     * Vérifie si une map existe pour un event donné
     */
    public boolean isMapValid(String gameType, String mapName) {
        String configPath = "arenas." + gameType.toLowerCase() + "." + mapName;
        return locationsConfig.contains(configPath + ".world");
    }
    
    /**
     * Récupère une map aléatoire pour un event donné
     */
    public String getRandomMap(String gameType) {
        List<String> maps = getAvailableMaps(gameType);
        if (maps.isEmpty()) {
            return null;
        }
        
        Random random = new Random();
        return maps.get(random.nextInt(maps.size()));
    }
    
    /**
     * Récupère tous les types d'events qui ont des maps configurées
     */
    public List<String> getAvailableGameTypes() {
        List<String> gameTypes = new ArrayList<>();
        
        if (!locationsConfig.contains("arenas")) {
            return gameTypes;
        }
        
        for (String gameType : locationsConfig.getConfigurationSection("arenas").getKeys(false)) {
            List<String> maps = getAvailableMaps(gameType);
            if (!maps.isEmpty()) {
                gameTypes.add(gameType);
            }
        }
        
        return gameTypes;
    }
    
    /**
     * Récupère la location d'une map spécifique
     */
    public org.bukkit.Location getArenaLocation(String gameType, String mapName) {
        String path = "arenas." + gameType.toLowerCase() + "." + mapName;
        return getLocation(path);
    }
}