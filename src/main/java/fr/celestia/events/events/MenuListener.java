package fr.celestia.events.events;

import fr.celestia.events.EventsPlugin;
import fr.celestia.events.managers.GameManager;
import fr.celestia.events.managers.TokenManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
            } else if (itemName.equals("§eBuildBattle")) {
                plugin.getCommandManager().openMapsMenu(player, "BuildBattle");
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
        // Message principal avec effet de néon
        TextComponent invitation = new TextComponent("§d§l⚡ §5§lÉVÉNEMENT §d§l⚡\n§6✦ §e" + host.getName() + " §6a lancé un §l" + gameType.toUpperCase() + "§r§6 !\n§6✦ Carte: §b" + mapName + " §6✦");
        
        // Bouton REJOINDRE très visible
        TextComponent clickToJoin = new TextComponent("§a§l✨ [ REJOINDRE L'ÉVÉNEMENT ] ✨");
        clickToJoin.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/game join " + gameId));
        clickToJoin.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("§a§l🎯 CLIQUEZ POUR REJOINDRE !\n\n" +
                            "§6✦ Hôte: §e" + host.getName() + "\n" +
                            "§6✦ Jeu: §b" + gameType + "\n" +
                            "§6✦ Carte: §a" + mapName + "\n\n" +
                            "§7➤ Cliquez ou tapez §e/game join §7\n" +
                            "§7➤ Places limitées ! Rejoignez vite !").create()));

        // Effet de son et titre pour attirer l'attention
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Sons d'attention
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
            
            // Titre flashy
            onlinePlayer.sendTitle(
                "§6§l🎊 ÉVÉNEMENT 🎊", 
                "§e" + host.getName() + " §6a lancé un " + gameType, 
                10, 60, 20
            );

            // Message dans le chat avec formatage élaboré
            onlinePlayer.sendMessage("§5§m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤");
            onlinePlayer.sendMessage("");
            onlinePlayer.spigot().sendMessage(invitation);
            onlinePlayer.sendMessage("");
            onlinePlayer.spigot().sendMessage(clickToJoin);
            onlinePlayer.sendMessage("");
            onlinePlayer.sendMessage("§7§o✨ Événement créé par §e" + host.getName() + "§7§o - Rejoignez rapidement !");
            onlinePlayer.sendMessage("§5§m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤");
            
            // Action bar pour rappel
            onlinePlayer.sendActionBar("§6§l🎮 Événement en cours! §e/game join");
        }
        
        // Log dans la console pour tracking
        Bukkit.getLogger().info("§d[EVENT] " + host.getName() + " a lance un " + gameType + " sur " + mapName + " (ID: " + gameId + ")");
    }
    
    public String getPendingGameId(UUID hostUuid) {
        return pendingInvitations.get(hostUuid);
    }
    
    public boolean hasPendingInvitation(UUID hostUuid) {
        return pendingInvitations.containsKey(hostUuid);
    }
}