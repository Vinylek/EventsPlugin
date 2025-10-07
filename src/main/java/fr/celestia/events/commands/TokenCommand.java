package fr.celestia.events.commands;

import fr.celestia.events.managers.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TokenCommand implements CommandExecutor, TabExecutor {
    
    private final TokenManager tokenManager;
    private final CommandManager commandManager;
    
    public TokenCommand(TokenManager tokenManager, CommandManager commandManager) {
        this.tokenManager = tokenManager;
        this.commandManager = commandManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                // Afficher ses propres jetons
                Player player = (Player) sender;
                int tokens = tokenManager.getTokens(player);
                player.sendMessage("§6§lVOS JETONS");
                player.sendMessage("§7Vous possédez: §e" + tokens + " §7jeton(s)");
                player.sendMessage("§7Utilisez §e/events §7pour voir les événements disponibles.");
            } else {
                sender.sendMessage("§cUtilisation: /" + label + " <give|remove|set|check> <joueur> [montant]");
            }
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUtilisation: /" + label + " <give|remove|set|check> <joueur> [montant]");
            return true;
        }
        
        if (!sender.hasPermission("events.tokens.manage")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }
        
        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);
        
        if (target == null) {
            sender.sendMessage("§cJoueur non trouvé!");
            return true;
        }
        
        switch (action) {
            case "give":
                if (args.length < 3) {
                    sender.sendMessage("§cUtilisation: /" + label + " give <joueur> <montant>");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0) {
                        sender.sendMessage("§cLe montant doit être positif!");
                        return true;
                    }
                    tokenManager.addTokens(target, amount);
                    sender.sendMessage("§aVous avez donné §e" + amount + " §ajetons à §6" + target.getName());
                    target.sendMessage("§aVous avez reçu §e" + amount + " §ajetons!");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cMontant invalide!");
                }
                break;
                
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§cUtilisation: /" + label + " remove <joueur> <montant>");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0) {
                        sender.sendMessage("§cLe montant doit être positif!");
                        return true;
                    }
                    tokenManager.removeTokens(target, amount);
                    sender.sendMessage("§aVous avez retiré §e" + amount + " §ajetons à §6" + target.getName());
                    target.sendMessage("§c§l- " + amount + " jetons");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cMontant invalide!");
                }
                break;
                
            case "set":
                if (args.length < 3) {
                    sender.sendMessage("§cUtilisation: /" + label + " set <joueur> <montant>");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0) {
                        sender.sendMessage("§cLe montant ne peut pas être négatif!");
                        return true;
                    }
                    tokenManager.setTokens(target, amount);
                    sender.sendMessage("§aVous avez défini §e" + amount + " §ajetons pour §6" + target.getName());
                    target.sendMessage("§6Vos jetons ont été mis à jour: §e" + amount + " jetons");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cMontant invalide!");
                }
                break;
                
            case "check":
                int tokens = tokenManager.getTokens(target);
                sender.sendMessage("§6" + target.getName() + " §apossède §e" + tokens + " §ajetons");
                break;
                
            default:
                sender.sendMessage("§cAction inconnue! Utilisez: give, remove, set ou check");
                break;
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("give", "remove", "set", "check"));
            if (sender instanceof Player) {
                completions.add(0, ""); // Option pour voir ses propres jetons
            }
        } else if (args.length == 2) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}