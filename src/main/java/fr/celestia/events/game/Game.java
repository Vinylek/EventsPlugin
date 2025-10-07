package fr.celestia.events.game;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.game.types.GameType;
import fr.celestia.events.managers.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Game {
    
    private final String gameId;
    private final GameType gameType;
    private final String arenaName;
    private final GameManager gameManager;
    private final EventsPlugin plugin;

    private GameState state;
    private final Set<UUID> players;
    private final Set<UUID> spectators;
    private final Map<UUID, PlayerData> playerData;
    private BukkitTask waitingTask;
    private BukkitTask gameTask;
    private Location arenaLocation;
    private Location waitingRoomLocation;
    private Location winningRoomLocation;
    private final long creationTime;
    private boolean joinable = true;
    
    public Game(String gameId, GameType gameType, String arenaName, GameManager gameManager) {
        this.gameId = gameId;
        this.gameType = gameType;
        this.arenaName = arenaName;
        this.gameManager = gameManager;
        this.plugin = gameManager.getPlugin();
        this.state = GameState.WAITING;
        this.players = new HashSet<>();
        this.spectators = new HashSet<>();
        this.playerData = new HashMap<>();
        this.creationTime = System.currentTimeMillis();
        
        loadLocations();
        startWaitingTimer();
    }
    
    private void loadLocations() {
        this.waitingRoomLocation = gameManager.getConfigManager().getLocation("waiting-room");
        this.winningRoomLocation = gameManager.getConfigManager().getLocation("winning-room");
        
        String arenaPath = "arenas." + gameType.getDisplayName().toLowerCase().replace(" ", "") + "." + arenaName.toLowerCase();
        this.arenaLocation = gameManager.getConfigManager().getLocation(arenaPath);
        
        if (arenaLocation == null) {
            this.arenaLocation = waitingRoomLocation;
            Bukkit.getLogger().warning("Arène non trouvée: " + arenaPath);
        }
    }
    
    private void startWaitingTimer() {
        int timer = gameType.getWaitingTime();
        final boolean[] hasChecked = {false}; // Éviter les checks multiples
        
        waitingTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int countdown = timer;
            
            @Override
            public void run() {
                if (hasChecked[0]) {
                    return; // Déjà vérifié, on ne fait rien
                }
                
                if (countdown <= 0) {
                    hasChecked[0] = true;
                    checkStart();
                    waitingTask.cancel();
                    return;
                }
                
                // Mettre à jour l'affichage
                if (countdown % 30 == 0 || countdown <= 5) {
                    broadcastMessage("§eDébut dans §6" + countdown + " §esecondes! (§a" + players.size() + "§e/§c" + 
                                gameManager.getConfigManager().getMaxPlayers() + "§e joueurs)");
                }
                
                countdown--;
            }
        }, 0L, 20L);
        
        // Timer pour fermer les inscriptions 10 secondes avant la fin
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joinable) {
                joinable = false;
                broadcastMessage("§c⚠ Les inscriptions sont maintenant fermées!");
            }
        }, (timer - 10) * 20L);
    }
    
    private void checkStart() {
        
        int minPlayers = gameType.getMinPlayers();
        
        if (players.size() >= minPlayers) {
            startGame();
        } else {
            broadcastMessage("§cPas assez de joueurs pour démarrer le jeu! (" + players.size() + "/" + minPlayers + ")");
            endGame();
        }
    }
    

    private void startGame() {
        state = GameState.STARTING; // Nouvel état
        
        if (waitingTask != null) {
            waitingTask.cancel();
        }
        
        broadcastMessage("§aTéléportation vers l'arène...");
        
        // Téléporter les joueurs dans l'arène
        teleportToArena();
        
        // Préparer les joueurs pour le jeu
        preparePlayersForGame();
        
        // Démarrer le compte à rebours de début
        startGameCountdown();
    }

    private void startGameCountdown() {
        final int countdownTime = 5;
        
        // Afficher les messages de compte à rebours
        for (int i = countdownTime; i > 0; i--) {
            final int secondsLeft = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                broadcastCountdown(secondsLeft);
            }, (countdownTime - secondsLeft) * 20L);
        }
        
        // Démarrer le jeu après le délai
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state = GameState.RUNNING;
            broadcastMessage("§6§lC'est parti !");
            
            // Démarrer le jeu spécifique
            gameType.onStart(this);
            
            // Démarrer la tâche de vérification des conditions de victoire
            startWinConditionCheck();
        }, countdownTime * 20L);
    }

    private void broadcastCountdown(int countdown) {
        String message;
        org.bukkit.Sound sound = null;
        org.bukkit.Particle particle = null;
        
        switch (countdown) {
            case 5:
                message = "§6⚡ Départ dans §e5 §6secondes !";
                sound = org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
                break;
            case 4:
                message = "§6⚡ Départ dans §e4 §6secondes !";
                sound = org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
                break;
            case 3:
                message = "§e⚡ Départ dans §63 §esecondes !";
                sound = org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
                particle = org.bukkit.Particle.FLAME;
                break;
            case 2:
                message = "§e⚡ Départ dans §62 §esecondes !";
                sound = org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
                particle = org.bukkit.Particle.FLAME;
                break;
            case 1:
                message = "§c⚡ Départ dans §61 §cseconde !";
                sound = org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
                particle = org.bukkit.Particle.FIREWORK;
                break;
            default:
                return;
        }
        
        broadcastMessage(message);
    
        // Effets sonores et visuels
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                }
                if (particle != null) {
                    player.spawnParticle(particle, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                }
            }
        }
    }
    
    private void teleportToArena() {
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && arenaLocation != null) {
                player.teleport(arenaLocation);
                player.sendMessage("§aTéléportation vers l'arène...");
            }
        }
    }
    
    private void preparePlayersForGame() {
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                gameType.preparePlayer(this, player);
            }
        }
    }
    
    private void startWinConditionCheck() {
        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.RUNNING) {
                gameTask.cancel();
                return;
            }
            
            // Vérifier les conditions de victoire via le GameType
            if (gameType.checkWinConditions(this)) {
                // Le GameType a décidé que le jeu doit se terminer
                List<Player> winners = determineWinners();
                endGame(winners);
            }
        }, 0L, 20L);
    }
    
    private List<Player> determineWinners() {
        List<Player> winners = new ArrayList<>();
        List<Player> onlinePlayers = getActivePlayers();
        
        // Par défaut, tous les joueurs encore en jeu sont gagnants
        // Les GameTypes spécifiques peuvent override checkWinConditions pour définir des gagnants spécifiques
        winners.addAll(onlinePlayers);
        
        return winners;
    }
    
    public void endGame(List<Player> winners) {
        state = GameState.ENDING;
        
        // Arrêter les tâches
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        
        // Appeler la fin du jeu spécifique
        gameType.onEnd(this);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§6=== §eFIN DE LA PARTIE §6===");
        }
        
        // Annoncer les gagnants
        if (winners.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§cAucun gagnant!");
            }
            startCeremonyTimer();
            return;
        } else if (winners.size() == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§6§l" + winners.get(0).getName() + " §aa remporté la partie!");
            }
        } else {
            StringBuilder winnersList = new StringBuilder("§6§lGagnants: ");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) winnersList.append("§e, ");
                winnersList.append("§6").append(winners.get(i).getName());
            }
            broadcastMessage(winnersList.toString());
        }
        
        // Téléporter vers la winning room
        teleportToWinningRoom();
        
        // Donner les récompenses
        giveRewards(winners);
        
        // Cérémonie de fin
        startCeremonyTimer();
    }
    
    private void teleportToWinningRoom() {
        if (winningRoomLocation == null) {
            Bukkit.getLogger().warning("Winning room non configurée!");
            return;
        }
        
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(winningRoomLocation);
                player.sendMessage("§aTéléportation vers la salle des vainqueurs...");
            }
        }
    }
    
    private void giveRewards(List<Player> winners) {
        String winCommand = gameManager.getConfigManager().getWinCommand();
        
        for (Player winner : winners) {
            // Exécuter la commande de récompense
            String command = winCommand.replace("%player%", winner.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            // Message personnalisé au gagnant
            winner.sendMessage("§6§lFélicitations! §eVous avez gagné la partie!");
            winner.sendMessage("§aRécompense attribuée!");
        }
        
        // Récompenses de participation pour tous les joueurs
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && !winners.contains(player)) {
                player.sendMessage("§eMerci d'avoir participé! §7Mieux chance la prochaine fois!");
            }
        }
    }
    
    private void startCeremonyTimer() {
        int ceremonyTime = gameManager.getConfigManager().getCeremonyTimer();
        
        // Stocker la référence de la tâche
        final org.bukkit.scheduler.BukkitTask[] ceremonyTask = new org.bukkit.scheduler.BukkitTask[1];
        
        ceremonyTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int countdown = ceremonyTime;
            
            @Override
            public void run() {
                if (countdown <= 0) {
                    // Réinitialiser tous les joueurs (actifs ET spectateurs)
                    for (UUID playerId : players) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            gameType.resetPlayer(Game.this, player);
                            gameManager.restorePlayerData(player);
                        }
                    }
                    for (UUID spectatorId : spectators) {
                        Player spectator = Bukkit.getPlayer(spectatorId);
                        if (spectator != null) {
                            gameType.resetPlayer(Game.this, spectator);
                            gameManager.restorePlayerData(spectator);
                        }
                    }
                    
                    // Supprimer le jeu
                    gameManager.removeGame(gameId);
                    
                    // Annuler cette tâche
                    if (ceremonyTask[0] != null) {
                        ceremonyTask[0].cancel();
                    }
                    return;
                }
                
                if (countdown <= 5) {
                    broadcastMessage("§eRetour dans §6" + countdown + " §esecondes...");
                }
                
                countdown--;
            }
        }, 0L, 20L);
    }
    
    public void endGame() {
        endGame(new ArrayList<>());
    }
    
    public void addPlayer(Player player) {
        // Sauvegarder la position actuelle du joueur
        playerData.put(player.getUniqueId(), new PlayerData(player));
        
        players.add(player.getUniqueId());
        broadcastMessage("§6" + player.getName() + " §aa rejoint la partie! (§e" + players.size() + "§a/§e" + 
                        gameManager.getConfigManager().getMaxPlayers() + "§a)");
        
        // Téléporter vers la waiting room
        if (waitingRoomLocation != null) {
            player.teleport(waitingRoomLocation);
            player.sendMessage("§aVous avez rejoint l'événement! Attente des autres joueurs...");
            player.setGameMode(GameMode.ADVENTURE);
        }
        
        // Appeler le hook du GameType
        gameType.onPlayerJoin(this, player);
        
        // Vérifier si on peut démarrer plus tôt
        if (players.size() >= gameManager.getConfigManager().getMaxPlayers()) {
            broadcastMessage("§aNombre maximum de joueurs atteint! Démarrage du jeu...");
            if (waitingTask != null) {
                waitingTask.cancel();
            }
            startGame();
        }
    }
    
    public void removePlayer(Player player) {
        // Si le joueur est dans la liste des joueurs actifs
        if (players.contains(player.getUniqueId())) {
            players.remove(player.getUniqueId());
            playerData.remove(player.getUniqueId());
            broadcastMessage("§6" + player.getName() + " §ca quitté la partie!");
            
            // Appeler le hook du GameType
            gameType.onPlayerLeave(this, player);
            
            // Restaurer le joueur immédiatement
            gameType.resetPlayer(this, player);
            gameManager.restorePlayerData(player);
        } 
        // Si le joueur est dans la liste des spectateurs
        else if (spectators.contains(player.getUniqueId())) {
            spectators.remove(player.getUniqueId());
            player.sendMessage("§aVous avez quitté l'événement.");
            
            // Restaurer le joueur
            gameType.resetPlayer(this, player);
            gameManager.restorePlayerData(player);
        }
        
        // Vérifier si le jeu doit se terminer faute de joueurs
        if (state == GameState.RUNNING && players.size() < gameType.getMinPlayers()) {
            broadcastMessage("§cTrop de joueurs ont quitté! Fin de la partie...");
            endGame();
        }
    }

    // Nouvelle méthode pour éliminer un joueur (devenir spectateur)
    public void eliminatePlayer(Player player, String reason, Boolean broadcasting) {
        if (players.contains(player.getUniqueId())) {
            players.remove(player.getUniqueId());
            spectators.add(player.getUniqueId());
            
            // Mettre en mode spectateur
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);

            if (broadcasting){
                broadcastMessage("§6" + player.getName() + " §ca été éliminé! §7(" + reason + ")");
            }
            player.sendMessage("§cVous avez été éliminé: " + reason);
            player.sendMessage("§7Vous êtes maintenant spectateur. Utilisez §e/game leave §7pour quitter.");
            
            // Vérifier les conditions de victoire
            if (players.size() <= 1) {
                gameType.checkWinConditions(this);
            }
        }
    }
    
    public void forceEnd() {
        if (waitingTask != null) waitingTask.cancel();
        if (gameTask != null) gameTask.cancel();
        
        // Appeler la fin du jeu spécifique
        gameType.onEnd(this);
        
        broadcastMessage("§cL'événement a été annulé!");
        
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                gameType.resetPlayer(this, player);
                gameManager.restorePlayerData(player);
                player.sendMessage("§cL'événement a été interrompu.");
            }
        }
        
        players.clear();
        playerData.clear();

        gameManager.removeGame(gameId);
    }
    
    // Méthodes pour gérer les événements
    public void handlePlayerMove(PlayerMoveEvent event) {
        if (state == GameState.RUNNING) {
            gameType.onPlayerMove(this, event);
        }
    }
    
    public void handlePlayerDamage(EntityDamageEvent event) {
        if (state == GameState.RUNNING) {
            gameType.onPlayerDamage(this, event);
        }
    }
    
    public void handleBlockBreak(BlockBreakEvent event) {
        if (state == GameState.WAITING || state == GameState.RUNNING) {
            gameType.onBlockBreak(this, event);
        }
    }
    
    public void broadcastMessage(String message) {
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("§6[Event] §r" + message);
            }
        }
        
        for (UUID spectatorId : spectators) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
    }
    
    public List<Player> getOnlinePlayers() {
        List<Player> onlinePlayers = new ArrayList<>();
        
        // Ajouter les joueurs actifs
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                onlinePlayers.add(player);
            }
        }
        
        // Ajouter les spectateurs (optionnel, selon l'usage)
        for (UUID spectatorId : spectators) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                onlinePlayers.add(spectator);
            }
        }
        
        return onlinePlayers;
    }

    // Nouvelle méthode pour obtenir seulement les joueurs actifs
    public List<Player> getActivePlayers() {
        List<Player> activePlayers = new ArrayList<>();
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                activePlayers.add(player);
            }
        }
        return activePlayers;
    }

    // Nouvelle méthode pour obtenir les spectateurs
    public List<Player> getOnlineSpectators() {
        List<Player> onlineSpectators = new ArrayList<>();
        for (UUID spectatorId : spectators) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                onlineSpectators.add(spectator);
            }
        }
        return onlineSpectators;
    }
    
    public void addSpectator(Player player) {
        spectators.add(player.getUniqueId());
        if (waitingRoomLocation != null) {
            player.teleport(waitingRoomLocation);
        }
        player.sendMessage("§7Vous observez maintenant l'événement.");
    }
    
    // Getters
    public String getGameId() { return gameId; }
    public GameType getGameType() { return gameType; }
    public String getArenaName() { return arenaName; }
    public GameState getState() { return state; }
    public Set<UUID> getPlayers() { return players; }
    public Set<UUID> getSpectators() { return spectators; }
    public int getPlayerCount() { return players.size(); }
    public Location getArenaLocation() { return arenaLocation; }
    public GameManager getGameManager() { return gameManager; }
    
    public boolean isFull() { 
        return players.size() >= gameManager.getConfigManager().getMaxPlayers(); 
    }
    
    public boolean canJoin() {
        return state == GameState.WAITING && joinable && !isFull();
    }
    
    public boolean canSpectate() {
        return state == GameState.RUNNING || state == GameState.ENDING || state == GameState.STARTING;
    }
    
    public void setState(GameState newState) {
        GameState oldState = this.state;
        this.state = newState;
        gameManager.onGameStateChange(this, oldState, newState);
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public boolean isJoinable() {
        return joinable;
    }
    
    public int getRemainingSlots() {
        return Math.max(0, gameManager.getConfigManager().getMaxPlayers() - players.size());
    }
}