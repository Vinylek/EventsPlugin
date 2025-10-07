package fr.celestia.events;

import fr.celestia.events.commands.CommandManager;
import fr.celestia.events.managers.*;
import fr.celestia.events.events.ChatListener;
import fr.celestia.events.events.GameListener;
import fr.celestia.events.events.MenuListener;
import fr.celestia.events.events.PlayerConnectionListener;

import org.bukkit.plugin.java.JavaPlugin;

public class EventsPlugin extends JavaPlugin {
    
    private static EventsPlugin instance;
    private ConfigManager configManager;
    private GameManager gameManager;
    private TokenManager tokenManager;
    private BossBarManager bossBarManager;
    private InventoryManager inventoryManager;
    private CommandManager commandManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialisation des managers
        this.configManager = new ConfigManager(this);
        this.tokenManager = new TokenManager(this);
        this.bossBarManager = new BossBarManager(this);
        this.inventoryManager = new InventoryManager(configManager);
        this.gameManager = new GameManager(this);
        this.commandManager = new CommandManager(this);
        
        // Chargement des configurations
        configManager.loadConfig();
        tokenManager.loadTokens();
        
        // Enregistrement des événements
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this); 
        
        // Enregistrement des commandes
        commandManager.registerCommands();
        
        getLogger().info("Plugin Events activé avec succès!");
    }
    
    @Override
    public void onDisable() {
        if (bossBarManager != null) {
            bossBarManager.cleanup();
        }
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("Plugin Events désactivé!");
    }
    
    public static EventsPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public GameManager getGameManager() {
        return gameManager;
    }
    
    public TokenManager getTokenManager() {
        return tokenManager;
    }
    
    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }
    
    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
}