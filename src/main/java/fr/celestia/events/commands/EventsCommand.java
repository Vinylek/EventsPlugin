package fr.celestia.events.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EventsCommand implements CommandExecutor {
    
    private final CommandManager commandManager;
    
    public EventsCommand(CommandManager commandManager) {
        this.commandManager = commandManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            commandManager.openGamesMenu(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (player.hasPermission("events.admin")) {
                    commandManager.reloadConfig(player);
                } else {
                    player.sendMessage("§cVous n'avez pas la permission!");
                }
                break;
                
            case "join":
                commandManager.joinGame(player);
                break;
                
                
            case "leave":
                commandManager.leaveGame(player);
                break;
                
            case "list":
            case "info":
                commandManager.listGames(player);
                break;
                
            case "create":
                if (player.hasPermission("events.admin")) {
                    if (args.length >= 3) {
                        String gameType = args[1];
                        String arenaName = args[2];
                        commandManager.createGame(player, gameType, arenaName);
                    } else {
                        player.sendMessage("§cUtilisation: /" + label + " create <type> <map>");
                        player.sendMessage("§7Types disponibles: TNT_RUN, SPLEEF, PARKOUR");
                    }
                } else {
                    player.sendMessage("§cVous n'avez pas la permission!");
                }
                break;
                
            case "cancel":
                if (player.hasPermission("events.admin")) {
                    // Implémentation pour annuler l'événement actuel
                    commandManager.cancelCurrentGame(player);
                } else {
                    player.sendMessage("§cVous n'avez pas la permission!");
                }
                break;
                
            default:
                sendHelp(player, label);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(Player player, String label) {
        player.sendMessage("§6§l=== AIDE - SYSTEME D'EVENEMENTS ===");
        player.sendMessage("§e/" + label + " §7- Ouvrir le menu des événements");
        player.sendMessage("§e/" + label + " join §7- Rejoindre l'événement en cours");
        player.sendMessage("§e/" + label + " leave §7- Quitter l'événement actuel");
        player.sendMessage("§e/" + label + " list §7- Voir l'événement en cours");
        
        if (player.hasPermission("events.admin")) {
            player.sendMessage("§e/" + label + " create <type> <map> §7- Créer un événement");
            player.sendMessage("§e/" + label + " cancel §7- Annuler l'événement");
            player.sendMessage("§e/" + label + " reload §7- Recharger la configuration");
        }
        
        player.sendMessage("§7Les événements nécessitent §e1 jeton §7pour participer.");
    }
}