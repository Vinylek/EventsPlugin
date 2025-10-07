package fr.celestia.events.events;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.managers.GameManager;
import fr.celestia.events.managers.TokenManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuListener implements Listener {
    
    private final EventsPlugin plugin;
    private final GameManager gameManager;
    private final TokenManager tokenManager;
    private final Map<UUID, String> pendingInvitations; // Stocke les invitations en attente
    
    public MenuListener(EventsPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.tokenManager = plugin.getTokenManager();
        this.pendingInvitations = new HashMap<>();
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String inventoryTitle = event.getView().getTitle();
        
        if (inventoryTitle.equals("§6Événements - Choisir un jeu")) {
            event.setCancelled(true);
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            
            String itemName = clicked.getItemMeta().getDisplayName();
            
            // Vérifier les jetons
            if (!tokenManager.hasTokens(player, 1)) {
                player.sendMessage("§cVous n'avez pas assez de jetons pour participer!");
                player.closeInventory();
                return;
            }
            
            if (itemName.equals("§cTNT Run")) {
                plugin.getCommandManager().openMapsMenu(player, "TNT Run");
            } else if (itemName.equals("§6TNT Tag")) {
                plugin.getCommandManager().openMapsMenu(player, "TNT Tag");
            } else if (itemName.equals("§bSpleef")) {
                plugin.getCommandManager().openMapsMenu(player, "Spleef");
            } else if (itemName.equals("§4FFA")) {
                plugin.getCommandManager().openMapsMenu(player, "FFA");
            } else if (itemName.equals("§dBlockParty")) {
                plugin.getCommandManager().openMapsMenu(player, "BlockParty");
            } 

        } else if (inventoryTitle.startsWith("§6Maps - ")) {
            event.setCancelled(true);
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            
            String gameType = inventoryTitle.replace("§6Maps - ", "");
            String mapName = clicked.getItemMeta().getLore().get(0);
            plugin.getLogger().info("MapName " + mapName);
            // Créer le jeu
            String gameId = UUID.randomUUID().toString();
            
            
            // Consommer un jeton
            tokenManager.removeTokens(player, 1);
            
            player.closeInventory();
            player.sendMessage("§aÉvénement créé! Les joueurs peuvent maintenant rejoindre.");
            
            gameManager.createGame(player, gameType, mapName);
            // Envoyer le message d'invitation dans le chat
            sendInvitationMessage(player, gameType, mapName, gameId);
        }
    }
    
    private void sendInvitationMessage(Player host, String gameType, String mapName, String gameId) {
        TextComponent invitation = new TextComponent("§6[ÉVÉNEMENT] §e" + host.getName() + " §aa lancé un " + gameType + 
                            " sur " + mapName + "! ");
        
        TextComponent clickToJoin = new TextComponent("§2§l[REJOINDRE]");
        clickToJoin.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/game join"));
        clickToJoin.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("§aCliquez pour rejoindre!\n§7" + gameType + " sur " + mapName).create()));

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage("§m-----------------------------------------------------");
            onlinePlayer.spigot().sendMessage(invitation, clickToJoin);
            onlinePlayer.sendMessage("§m-----------------------------------------------------");
            onlinePlayer.sendMessage("§7Utilisez §e/game join §7pour participer!");
        }
    }
    
    public String getPendingGameId(UUID hostUuid) {
        return pendingInvitations.get(hostUuid);
    }
    
    public boolean hasPendingInvitation(UUID hostUuid) {
        return pendingInvitations.containsKey(hostUuid);
    }
}