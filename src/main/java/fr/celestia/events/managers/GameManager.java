package fr.celestia.events.managers;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.game.Game;
import fr.celestia.events.game.GameState;
import fr.celestia.events.game.PlayerData;
import fr.celestia.events.game.types.BlockPartyGame;
import fr.celestia.events.game.types.FFAGame;
import fr.celestia.events.game.types.GameType;
import fr.celestia.events.game.types.SpleefGame;
import fr.celestia.events.game.types.TntRunGame;
import fr.celestia.events.game.types.TntTagGame;

import org.bukkit.entity.Player;

import java.util.*;


public class GameManager {
    
    private final EventsPlugin plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager;
    private Game activeGame; // Un seul jeu actif à la fois
    private final Map<UUID, PlayerData> playerData;
    
    public GameManager(EventsPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.bossBarManager = plugin.getBossBarManager();
        this.playerData = new HashMap<>();
    }

    public void onPlayerJoin(Player player) {
        // Quand un joueur rejoint le serveur, vérifier s'il y a un événement en attente
        Optional<Game> activeGameOpt = getActiveGame();
        if (activeGameOpt.isPresent()) {
            Game game = activeGameOpt.get();
            
            // Si l'événement est en attente et peut encore être rejoint
            if (game.getState() == fr.celestia.events.game.GameState.WAITING && game.isJoinable()) {
                bossBarManager.sendBossBarNotification(
                    game.getGameId(), 
                    player, 
                    game.getGameType().getDisplayName(), 
                    game.getArenaName()
                );
            }
        }
    }
    
    // Dans GameManager, modifiez la création de jeu :
    public Optional<Game> createGame(Player host, String gameTypeName, String arenaName) {
        // Vérifier si un jeu est déjà en cours
        if (activeGame != null) {
            host.sendMessage("§cUn événement est déjà en cours! Attendez qu'il se termine.");
            return Optional.empty();
        }
        
        // Créer l'instance GameType appropriée
        GameType gameType = createGameType(gameTypeName);
        if (gameType == null) {
            host.sendMessage("§cType de jeu inconnu: " + gameTypeName);
            return Optional.empty();
        }
    
        String gameId = UUID.randomUUID().toString().substring(0, 8);
        Game game = new Game(gameId, gameType, arenaName, this);
        activeGame = game;
        
        // Ajouter le host au jeu
        joinGame(host, gameId);
        
        // Créer la bossbar
        int waitingTime = gameType.getWaitingTime();
        bossBarManager.createGameBossBar(gameId, gameType.getDisplayName(), arenaName, waitingTime);
        
        plugin.getLogger().info("Nouveau jeu créé: " + gameType.getDisplayName() + " sur " + arenaName);
        
        return Optional.of(game);
    }

    private GameType createGameType(String gameTypeName) {
        switch (gameTypeName.toUpperCase()) {
            case "TNT RUN":
            case "TNTRUN":
                return new TntRunGame();
            case "TNT TAG":
            case "TNTTAG":
                return new TntTagGame();
            case "SPLEEF":
                return new SpleefGame();
            case "FFA":
                return new FFAGame();
            case "BLOCKPARTY":
            case "BLOCK PARTY":
                return new BlockPartyGame();
            default:
                return null;
        }
    }
    
    public boolean joinGame(Player player, String gameId) {
        // Vérifier s'il y a un jeu actif
        if (activeGame == null) {
            player.sendMessage("§cAucun événement en cours! Créez-en un avec §e/events");
            return false;
        }
        
        // Vérifier que c'est bien le jeu actif
        if (!activeGame.getGameId().equals(gameId)) {
            player.sendMessage("§cCet événement n'existe plus! L'événement actuel est: §e" + 
                             activeGame.getGameType() + " §c(ID: §e" + activeGame.getGameId() + "§c)");
            return false;
        }
        
        if (activeGame.getState() != GameState.WAITING) {
            player.sendMessage("§cImpossible de rejoindre: l'événement a déjà commencé!");
            return false;
        }
        
        if (activeGame.isFull()) {
            player.sendMessage("§cImpossible de rejoindre: l'événement est complet!");
            return false;
        }
        
        if (!activeGame.isJoinable()) {
            player.sendMessage("§cImpossible de rejoindre: les inscriptions sont fermées!");
            return false;
        }
        
        // Vérifier si le joueur est déjà dans un jeu
        if (getPlayerGame(player).isPresent()) {
            player.sendMessage("§cVous êtes déjà dans un événement!");
            return false;
        }
        
        
        // Sauvegarder les données du joueur
        savePlayerData(player);
        
        // Rejoindre le jeu
        activeGame.addPlayer(player);
        
        
        return true;
    }
    
    public boolean joinCurrentGame(Player player) {
        // Méthode simplifiée pour rejoindre l'événement actuel
        if (activeGame == null) {
            player.sendMessage("§cAucun événement en cours! Créez-en un avec §e/events");
            return false;
        }
        
        return joinGame(player, activeGame.getGameId());
    }
    
    public boolean spectateCurrentGame(Player player) {
        if (activeGame == null) {
            player.sendMessage("§cAucun événement en cours!");
            return false;
        }
        
        if (activeGame.canSpectate()) {
            savePlayerData(player);
            activeGame.addSpectator(player);
            return true;
        } else {
            player.sendMessage("§cImpossible de spectater cet événement!");
            return false;
        }
    }
    
    public void leaveGame(Player player) {
        if (activeGame != null && activeGame.getPlayers().contains(player.getUniqueId())) {
            activeGame.removePlayer(player);
            player.sendMessage("§aVous avez quitté l'événement.");
        } else {
            player.sendMessage("§cVous n'êtes pas dans un événement!");
        }
    }
    
    public Optional<Game> getPlayerGame(Player player) {
        if (activeGame != null && 
            (activeGame.getPlayers().contains(player.getUniqueId()) || 
             activeGame.getSpectators().contains(player.getUniqueId()))) {
            return Optional.of(activeGame);
        }
        return Optional.empty();
    }
    
    public Optional<Game> getActiveGame() {
        return Optional.ofNullable(activeGame);
    }
    
    public List<Game> getWaitingGames() {
        List<Game> games = new ArrayList<>();
        if (activeGame != null && activeGame.getState() == GameState.WAITING && activeGame.isJoinable()) {
            games.add(activeGame);
        }
        return games;
    }
    
    public Collection<Game> getActiveGames() {
        List<Game> games = new ArrayList<>();
        if (activeGame != null) {
            games.add(activeGame);
        }
        return games;
    }
    
    public void removeGame(String gameId) {
        if (activeGame != null && activeGame.getGameId().equals(gameId)) {
            // Nettoyer la bossbar
            bossBarManager.removeBossBar(gameId);
            
            plugin.getLogger().info("Jeu supprimé: " + gameId);
            activeGame = null;
        }
    }
    
    public void onGameStateChange(Game game, GameState oldState, GameState newState) {
        if (newState == GameState.RUNNING) {
            // Fermer les inscriptions et supprimer la bossbar
            bossBarManager.removeBossBar(game.getGameId());
        }
    }
    
    private void savePlayerData(Player player) {
        PlayerData data = new PlayerData(player);
        playerData.put(player.getUniqueId(), data);
        
        if (configManager.getConfig().getBoolean("debug.player-data", false)) {
            plugin.getLogger().info("Sauvegarde des données de " + player.getName());
        }
    }
    
    public void restorePlayerData(Player player) {
        PlayerData data = playerData.remove(player.getUniqueId());
        if (data != null) {
            if (configManager.getConfig().getBoolean("debug.player-data", false)) {
                plugin.getLogger().info("Restauration des données de " + player.getName());
            }
            data.restore(player);
        } else {
            // Seulement si debug activé, sinon on spam pour rien
            if (configManager.getConfig().getBoolean("debug.player-data", false)) {
                plugin.getLogger().warning("Aucune donnée trouvée pour " + player.getName());
            }
            
            // Fallback une seule fois
            if (player.isOnline()) {
                player.teleport(player.getWorld().getSpawnLocation());
                player.sendMessage("§cRetour au spawn par défaut.");
            }
        }
    }

    
    public void shutdown() {
        if (activeGame != null) {
            activeGame.forceEnd();
        }
        activeGame = null;
        playerData.clear();
        bossBarManager.cleanup();
    }
    
    // Getters
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }
    
    public EventsPlugin getPlugin() {
        return plugin;
    }
}