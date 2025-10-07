package fr.celestia.events.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;

public class VotePartyCommand implements CommandExecutor, TabExecutor {
    
    private final CommandManager commandManager;
    
    public VotePartyCommand(CommandManager commandManager) {
        this.commandManager = commandManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        // Vérifier la permission
        if (!sender.hasPermission("events.voteparty") && !sender.isOp()) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }
        
        // Option: /voteparty confirm pour éviter les lancements accidentels
        if (args.length == 0) {
            sender.sendMessage("§6§l🎉 VOTE PARTY 🎉");
            sender.sendMessage("§eCette commande lancera un événement aléatoire!");
            sender.sendMessage("§eUtilisation: §6/voteparty confirm");
            return true;
        }
        
        if (args.length >= 1 && args[0].equalsIgnoreCase("confirm")) {
            commandManager.startRandomEvent(sender);
            return true;
        }
        
        sender.sendMessage("§cUtilisation: /voteparty confirm");
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("confirm");
        }
        
        return completions;
    }
}