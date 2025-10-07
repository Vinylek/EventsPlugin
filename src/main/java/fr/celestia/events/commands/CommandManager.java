package fr.celestia.events.commands;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.managers.ConfigManager;
import fr.celestia.events.managers.GameManager;
import fr.celestia.events.managers.InventoryManager;
import fr.celestia.events.managers.TokenManager;
import fr.celestia.events.game.Game;
import fr.celestia.events.game.GameState;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class CommandManager implements CommandExecutor, TabExecutor {
    
    private final EventsPlugin plugin;
    private final ConfigManager configManager;
    private final GameManager gameManager;
    private final TokenManager tokenManager;
    private final InventoryManager inventoryManager;
    
    public CommandManager(EventsPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.gameManager = plugin.getGameManager();
        this.tokenManager = plugin.getTokenManager();
        this.inventoryManager = plugin.getInventoryManager();
    }
    
    public void registerCommands() {
        plugin.getCommand("events").setExecutor(new EventsCommand(this));
        plugin.getCommand("game").setExecutor(new EventsCommand(this));
        plugin.getCommand("eventtokens").setExecutor(new TokenCommand(tokenManager, this));
        plugin.getCommand("voteparty").setExecutor(new VotePartyCommand(this));
    }

    public void joinGame(Player player) {
        gameManager.joinCurrentGame(player);
    }

    public void leaveGame(Player player) {
        gameManager.leaveGame(player);
    }

    public void spectateGame(Player player) {
        boolean success = gameManager.spectateCurrentGame(player);
        if (success) {
            player.sendMessage("§7Vous observez maintenant l'événement.");
        }
    }

    public void listGames(Player player) {
        Optional<Game> activeGameOpt = gameManager.getActiveGame();
        
        player.sendMessage("§6§l=== ÉVÉNEMENT ACTUEL ===");
        
        if (activeGameOpt.isPresent()) {
            Game game = activeGameOpt.get();
            
            String statusColor = game.getState() == GameState.WAITING ? "§a" : 
                            game.getState() == GameState.RUNNING ? "§c" : "§e";
            String statusText = game.getState() == GameState.WAITING ? "En attente" : 
                            game.getState() == GameState.RUNNING ? "En cours" : "En fin";
            
            player.sendMessage("§eType: §6" + game.getGameType());
            player.sendMessage("§eMap: §6" + game.getArenaName());
            player.sendMessage("§eStatut: " + statusColor + statusText);
            player.sendMessage("§eJoueurs: §6" + game.getPlayerCount() + "§e/§6" + 
                            gameManager.getConfigManager().getMaxPlayers());
            player.sendMessage("§eID: §f" + game.getGameId());
            
            if (game.getState() == GameState.WAITING) {
                if (game.isJoinable()) {
                    player.sendMessage("");
                    player.sendMessage("§a✅ Rejoignable avec §e/game join");
                } else {
                    player.sendMessage("");
                    player.sendMessage("§c❌ Inscriptions fermées");
                }
            }
            
        } else {
            player.sendMessage("§7Aucun événement en cours.");
            player.sendMessage("");
            player.sendMessage("§eCréez un événement avec §6/events");
        }
    }


    public void cancelCurrentGame(Player player) {
        Optional<Game> activeGameOpt = gameManager.getActiveGame();
        if (activeGameOpt.isPresent()) {
            Game game = activeGameOpt.get();
            game.forceEnd();
            player.sendMessage("§cÉvénement annulé!");
        } else {
            player.sendMessage("§cAucun événement en cours à annuler!");
        }
    }


    

    public void openGamesMenu(Player player) {
        // Vérifier si le joueur est déjà dans un jeu
        if (gameManager.getPlayerGame(player).isPresent()) {
            player.sendMessage("§cVous êtes déjà dans un événement! Utilisez /game leave pour le quitter.");
            return;
        }

        inventoryManager.openGamesMenu(player);
    }
    
    public void openMapsMenu(Player player, String gameType) {
        inventoryManager.openMapsMenu(player, gameType);
    }
    
    public void reloadConfig(CommandSender sender) {
        configManager.reloadConfig();
        sender.sendMessage("§aConfiguration rechargée avec succès!");
        
        // Recharger aussi les tokens si nécessaire
        tokenManager.loadTokens();
        sender.sendMessage("§aTokens rechargés avec succès!");
    }

    // Implémentation de TabExecutor pour l'auto-complétion
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Cette méthode est déjà gérée par EventsCommand
        return false;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("join");
            completions.add("leave");
            completions.add("list");
            if (sender.hasPermission("events.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("join"))) {
            // Auto-complétion des IDs de jeux en attente
            for (Game game : gameManager.getWaitingGames()) {
                if (game.isJoinable()) {
                    completions.add(game.getGameId().substring(0, 8)); // Premiers 8 caractères de l'ID
                }
            }
        }
        
        return completions;
    }

    // Méthode utilitaire pour créer un jeu
    public boolean createGame(Player player, String gameType, String arenaName) {
        Optional<Game> gameOpt = gameManager.createGame(player, gameType, arenaName);
        if (gameOpt.isPresent()) {
            player.sendMessage("§aÉvénement créé avec succès! ID: §e" + 
                gameOpt.get().getGameId().substring(0, 8));
            return true;
        } else {
            player.sendMessage("§cErreur lors de la création de l'événement!");
            return false;
        }
    }

    public void startRandomEvent(CommandSender sender) {
        // Vérifier les permissions
        if (!sender.hasPermission("events.admin") && !sender.isOp()) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return;
        }

        // Vérifier s'il y a déjà un event en cours
        if (gameManager.getActiveGame().isPresent()) {
            sender.sendMessage("§cUn événement est déjà en cours! Annulez-le d'abord avec /game cancel");
            return;
        }

        try {
            // Choisir un type d'event random
            String randomGameType = getRandomGameType();
            
            // Choisir une map random pour cet event
            String randomMap = getRandomMapForGameType(randomGameType);
            
            if (randomMap == null) {
                sender.sendMessage("§cAucune map disponible pour l'event " + randomGameType + "!");
                return;
            }

            plugin.getLogger().info("VoteParty - Lancement de " + randomGameType + " sur " + randomMap);
            
            // Créer l'event (utiliser la console comme créateur)
            String gameId = UUID.randomUUID().toString();
            
            // Si c'est un joueur qui exécute, l'utiliser comme créateur, sinon utiliser la console
            Player creator = (sender instanceof Player) ? (Player) sender : null;
            
            Optional<Game> gameOpt = gameManager.createGame(creator, randomGameType, randomMap);
            
            if (gameOpt.isPresent()) {
                Game game = gameOpt.get();
                
                // Message de broadcast
                String formattedMessage = String.format(
                "§6§l╔══════════════════════════════╗\n" +
                "       §e§l🎉 VOTE PARTY 🎉         \n" +
                "       §eType: §6%-15s \n" +
                "       §eMap: §6%-15s  \n" +
                "    §a/game join §epour jouer!  \n" +
                "§6§l╚══════════════════════════════╝\n",
                randomGameType, randomMap
                );
                
                Bukkit.broadcastMessage(formattedMessage);
                
                
            } else {
                sender.sendMessage("§cErreur lors de la création de l'événement random!");
            }
            
        } catch (Exception e) {
            sender.sendMessage("§cErreur lors du lancement du VoteParty: " + e.getMessage());
            plugin.getLogger().severe("Erreur VoteParty: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String getRandomGameType() {
        // Liste des types d'events disponibles
        String[] availableGameTypes = {
            "FFA", 
            "BlockParty",
            "TNTTag",
            "TNTRun",
            "Spleef"
        };
        
        Random random = new Random();
        return availableGameTypes[random.nextInt(availableGameTypes.length)];
    }
    
    private String getRandomMapForGameType(String gameType) {
        // Récupérer les maps disponibles pour ce type d'event
        List<String> availableMaps = configManager.getAvailableMaps(gameType);
        
        if (availableMaps == null || availableMaps.isEmpty()) {
            return null;
        }
        
        Random random = new Random();
        return availableMaps.get(random.nextInt(availableMaps.size()));
    }
}

