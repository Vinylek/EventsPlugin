package fr.celestia.events.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InventoryManager {
    
    private final ConfigManager configManager;
    
    public InventoryManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public void openGamesMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, "§6Événements - Choisir un jeu");
        
        // Exemple d'items pour différents jeux
        ItemStack tntRun = createMenuItem(Material.TNT, "§cTNT Run", 
            Arrays.asList("§7Clique pour choisir une map", "§eDernier survivant gagne!"));
        
        ItemStack tntTag = createMenuItem(Material.TNT_MINECART, "§6TNT Tag", 
        Arrays.asList("§7Clique pour choisir une map", "§ePassez la TNT avant qu'elle n'explose!"));

        ItemStack spleef = createMenuItem(Material.IRON_SHOVEL, "§bSpleef", 
            Arrays.asList("§7Clique pour choisir une map", "§eCassez les blocs sous vos adversaires!"));
        
        ItemStack ffa = createMenuItem(Material.DIAMOND_SWORD, "§4FFA", 
        Arrays.asList("§7Clique pour choisir une map", "§eTous contre tous !", "§6Lootez les coffres !"));

        ItemStack blockParty = createMenuItem(Material.YELLOW_WOOL, "§dBlockParty", 
        Arrays.asList("§7Clique pour choisir une map", "§eReste sur la bonne couleur !"));
    
        menu.setItem(9, tntRun);
        menu.setItem(11, tntTag);
        menu.setItem(13, spleef);
        menu.setItem(15, ffa);
        menu.setItem(17, blockParty);
        
        player.openInventory(menu);
    }
    
    public void openMapsMenu(Player player, String gameType) {
        Inventory menu = Bukkit.createInventory(null, 27, "§6Maps - " + gameType);
        
        // Exemple de maps
        ItemStack map1 = createMenuItem(Material.MAP, "§eMap 1", 
            Arrays.asList("§7Clique pour sélectionner", "§6Difficulté: §aFacile"));
        
        ItemMeta meta = map1.getItemMeta();
        meta.setDisplayName("§eMap 1");
        meta.setLore(Collections.singletonList("map1")); // identifiant exact
        map1.setItemMeta(meta);


        menu.setItem(12, map1);
        
        player.openInventory(menu);
    }
    
    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}