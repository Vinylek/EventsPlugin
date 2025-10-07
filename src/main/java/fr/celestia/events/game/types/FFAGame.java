package fr.celestia.events.game.types;

import fr.celestia.events.game.Game;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class FFAGame implements GameType {
    
    private final Set<Location> lootedChests = new HashSet<>();
    private final Map<Player, Integer> kills = new HashMap<>();
    private BukkitTask gameTimerTask = null;
    private BukkitTask pvpTask = null;
    private int gameTime = 0;
    private boolean pvpEnabled = false;
    private final Random random = new Random();
    
    // Configuration du FFA
    private final int CHEST_REFILL_TIME = 180; // Secondes avant respawn des coffres
    private final int PVP_START_TIME = 10; // Secondes avant le début du pvp

    @Override
    public void onStart(Game game) {
        game.broadcastMessage("§4§lFFA §ecommence !");
        game.broadcastMessage("§7Tous contre tous ! Le dernier survivant gagne !");
        game.broadcastMessage("§6⚔️ §eCherchez des coffres pour obtenir de l'équipement !");
        game.broadcastMessage("§c🏹 §eLe PVP sera activé dans §c10 secondes§e !");
        
        
        // Téléporter les joueurs au centre
        teleportPlayersToCenter(game);
        
        // Démarrer le timer du jeu
        startGameTimer(game);
        
        // Démarrer le compte à rebours pour le PVP
        startPvPCountdown(game);
        
        // Démarrer le respawn des coffres
        refillNearbyChests(game);
        startChestRefillTask(game);
        
    }
    
    @Override
    public void onEnd(Game game) {
        stopAllTasks();
        lootedChests.clear();
        kills.clear();
        pvpEnabled = false;

        clearAllChests(game);

    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        // Instructions
        player.sendMessage("§4§l=== FFA - FREE FOR ALL ===");
        player.sendMessage("§e• Être le dernier survivant !");
        player.sendMessage("§e• Cherchez les coffres pour de l'équipement");
        player.sendMessage("§e• PVP activé dans 10 secondes");
        player.sendMessage("§aBonne chance !");

        // Préparer le joueur sans équipement
        preparePlayer(game, player);
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        kills.remove(player);
    }
    
    @Override
    public void onPlayerMove(Game game, PlayerMoveEvent event) {
        // Rien de spécial
    
    }
    
    public void handlePlayerAttack(Game game, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
    
        Player player = (Player) event.getEntity();
        
        // Si le PVP n'est pas activé, annuler les dégâts entre joueurs
        if (!pvpEnabled && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            if (event.getDamager() instanceof Player) {
                event.setCancelled(true);
                ((Player) event.getDamager()).sendMessage("§cLe PVP n'est pas encore activé !");
                return;
            }
        }
        
        // Gérer la mort du joueur
        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true); // Empêcher la mort naturelle
            handlePlayerDeath(game, player, event.getDamager());
        }
    }
    
    @Override
    public void onBlockBreak(Game game, BlockBreakEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendActionBar("§cVous ne pouvez pas casser de blocs !");
    }
    
    @Override
    public boolean checkWinConditions(Game game) {
        List<Player> activePlayers = game.getActivePlayers();
        
        // Victoire si un seul joueur reste
        if (activePlayers.size() == 1) {
            return true;
        }
        
        // Match nul si aucun joueur
        if (activePlayers.isEmpty()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public void preparePlayer(Game game, Player player) {
        // Reset complet du joueur
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        
        // Retirer tous les effets
        player.getActivePotionEffects().forEach(effect -> 
            player.removePotionEffect(effect.getType()));
        
        
    }
    
    @Override
    public void resetPlayer(Game game, Player player) {
        // Reset standard
        GameType.super.resetPlayer(game, player);
    }
    
    @Override
    public String getDisplayName() {
        return "FFA";
    }
    
    @Override
    public String getDescription() {
        return "Tous contre tous ! Trouvez de l'équipement et soyez le dernier survivant !";
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public int getWaitingTime() {
        return 60;
    }
    
    // MÉTHODES SPÉCIFIQUES AU FFA

    private void startChestRefillTask(Game game) {
        Bukkit.getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(),
            () -> {
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) return;
                refillNearbyChests(game);
                
            },
            CHEST_REFILL_TIME * 20L,
            CHEST_REFILL_TIME * 20L
        );
    }
    
    private void refillNearbyChests(Game game) {
        Location center = game.getArenaLocation();
        if (center == null) return;

        int radius = 50;
        int refilled = 0;
        World world = center.getWorld();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    Block block = world.getBlockAt(loc);
                    
                    if (block.getType() == Material.CHEST) {
                        fillChestWithLoot(block);
                        refilled++;
                        
                    }
                }
            }
        }
        
        game.broadcastMessage("§eLes coffres ont été remplis !");
    }

    private void fillChestWithLoot(Block chestBlock) {
        if (chestBlock.getType() != Material.CHEST) return;

        try {
            // Obtenir directement l'inventaire du bloc coffre
            Chest chest = (Chest) chestBlock.getState();
            Inventory inv = chest.getBlockInventory();
            
            // Vider complètement le coffre
            inv.clear();
            
            // Générer le loot
            LootType lootType = LootType.values()[random.nextInt(LootType.values().length)];
            List<ItemStack> loot = generateLoot(lootType);

            // Ajouter les items dans des slots aléatoires
            for (ItemStack item : loot) {
                if (item != null) {
                    int slot = random.nextInt(inv.getSize());
                    // Essayer plusieurs slots si le premier est occupé
                    for (int i = 0; i < inv.getSize(); i++) {
                        int trySlot = (slot + i) % inv.getSize();
                        if (inv.getItem(trySlot) == null) {
                            inv.setItem(trySlot, item);
                            // Bukkit.getLogger().info("[FFA] Item ajouté: " + item.getType() + 
                            //                     " x" + item.getAmount() + 
                            //                     " dans coffre " + chestBlock.getLocation());
                            break;
                        }
                    }
                }
            }
            
            
        } catch (Exception e) {
            Bukkit.getLogger().severe("[FFA] Erreur lors du remplissage du coffre: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearAllChests(Game game) {
        Location center = game.getArenaLocation();
        if (center == null) return;

        int radius = 50;
        World world = center.getWorld();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(center.clone().add(x, y, z));
                    if (block.getType() == Material.CHEST && block.getState() instanceof Chest) {
                        Chest chest = (Chest) block.getState();
                        chest.getInventory().clear();
                    }
                }
            }
        }
    }
    
    private List<ItemStack> generateLoot(LootType lootType) {
        List<ItemStack> loot = new ArrayList<>();
        Random random = new Random();

        switch (lootType) {
            case WEAPONS:
                if (random.nextDouble() < 0.5) {
                    loot.add(createEnchantedItem(Material.IRON_SWORD, 1,
                            Map.of(Enchantment.SHARPNESS, 1)));
                } else {
                    loot.add(createEnchantedItem(Material.STONE_AXE, 1,
                            Map.of(Enchantment.SHARPNESS, 2)));
                }
                if (random.nextDouble() < 0.3) {
                    loot.add(createEnchantedItem(Material.BOW, 1,
                            Map.of(Enchantment.POWER, 1)));
                    loot.add(new ItemStack(Material.ARROW, 8 + random.nextInt(9)));
                }
                loot.add(new ItemStack(Material.GOLDEN_APPLE, 1 + random.nextInt(2)));
                break;

            case ARMOR:
                // Armure mixte 
                Material[] armors = {
                    Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                    Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                    Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_BOOTS
                };
                Material chosen = armors[random.nextInt(armors.length)];
                loot.add(createEnchantedItem(chosen, 1,
                        Map.of(Enchantment.PROTECTION, random.nextInt(2) + 1)));

                if (random.nextDouble() < 0.25) {
                    loot.add(new ItemStack(Material.SHIELD));
                }
                break;

            case POTIONS:
                int roll = random.nextInt(3);
                switch (roll) {
                    case 0:
                        loot.add(createPotion(PotionEffectType.SPEED, 200, 0)); // 10s speed 1
                        break;
                    case 1:
                        loot.add(createPotion(PotionEffectType.REGENERATION, 200, 0)); // regen 10s
                        break;
                    case 2:
                        loot.add(createPotion(PotionEffectType.INVISIBILITY, 100, 0)); // 5s
                        break;
                }
                loot.add(new ItemStack(Material.BREAD, 3 + random.nextInt(3)));
                break;

            case MIXED:
                loot.add(new ItemStack(Material.COOKED_BEEF, 4 + random.nextInt(5)));
                loot.add(new ItemStack(Material.COOKED_CHICKEN, 3 + random.nextInt(3)));

                if (random.nextDouble() < 0.4) {
                    loot.add(createPotion(PotionEffectType.INSTANT_HEALTH, 1, 0));
                }
                if (random.nextDouble() < 0.3) {
                    loot.add(createEnchantedItem(Material.STONE_SWORD, 1,
                            Map.of(Enchantment.KNOCKBACK, 1)));
                }
                if (random.nextDouble() < 0.2) {
                    loot.add(new ItemStack(Material.DIAMOND_BOOTS, 1));
                    loot.add(new ItemStack(Material.DIAMOND_HELMET, 1));
                }
                break;
        }
        return loot;
    }


    
    private ItemStack createEnchantedItem(Material material, int amount, Map<Enchantment, Integer> enchantments) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createPotion(PotionEffectType effect, int duration, int amplifier) {
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        
        // Créer une potion custom
        PotionMeta potionMeta = (PotionMeta) meta;
        potionMeta.addCustomEffect(new PotionEffect(effect, duration, amplifier), true);
        
        // Nom personnalisé selon l'effet
        String potionName = getPotionName(effect);
        potionMeta.setDisplayName(potionName);
        
        potion.setItemMeta(potionMeta);
        return potion;
    }
    
    private String getPotionName(PotionEffectType effect) {
        switch (effect.getName().toLowerCase()) {
            case "speed": return "§bPotion de Vitesse";
            case "invisibility": return "§7Potion d'Invisibilité";
            case "regeneration": return "§dPotion de Régénération";
            case "strength": return "§cPotion de Force";
            case "instant_health": return "§aPotion de Soin";
            default: return "§ePotion Mystérieuse";
        }
    }
    
    private void teleportPlayersToCenter(Game game) {
        Location center = game.getArenaLocation();
        if (center == null) return;
        
        for (Player player : game.getActivePlayers()) {
            // Position aléatoire autour du centre (rayon de 5 blocs)
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * 5;
            
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            
            Location spawnLocation = new Location(center.getWorld(), x, center.getY(), z, 
                (float) (angle * 180 / Math.PI), 0);
            
            player.teleport(spawnLocation);
            player.sendMessage("§6Vous avez spawn au centre de l'arène !");

            // Donner un item de base (pierre ou bois)
            ItemStack starterItem = new ItemStack(Material.WOODEN_SWORD);
            ItemMeta meta = starterItem.getItemMeta();
            meta.setDisplayName("§7Épée de Départ");
            starterItem.setItemMeta(meta);
            
            player.getInventory().setItem(0, starterItem);
        }
    }
    
    private void startPvPCountdown(Game game) {
        final int[] countdown = {PVP_START_TIME};
        
        pvpTask = Bukkit.getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (countdown[0] > 0) {
                    if (countdown[0] <= 10 || countdown[0] == 30) {
                        game.broadcastMessage("§cPVP activé dans §e" + countdown[0] + " §csecondes !");
                        
                        // Effets sonores pour les derniers instants
                        if (countdown[0] <= 5) {
                            for (Player player : game.getOnlinePlayers()) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                            }
                        }
                    }
                    countdown[0]--;
                } else {
                    // Activer le PVP
                    pvpEnabled = true;
                    game.broadcastMessage("§cLE PVP EST MAINTENANT ACTIVÉ ! ");
                    game.broadcastMessage("§cTous les coups sont permis ! Bonne chance !");
                    
                    // Effets dramatiques
                    for (Player player : game.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                        player.spawnParticle(Particle.LAVA, player.getLocation(), 10);
                    }
                    
                    // Arrêter la tâche
                    if (pvpTask != null) {
                        pvpTask.cancel();
                    }
                }
            }, 0L, 20L);
    }
    
    public void handlePlayerDeath(Game game, Player player, org.bukkit.entity.Entity killer) {
        // Message de mort
        String deathMessage = getDeathMessage(player, killer);
        game.broadcastMessage(deathMessage);
        
        // Compter le kill si c'est un joueur
        if (killer instanceof Player) {
            Player killerPlayer = (Player) killer;
            kills.put(killerPlayer, kills.getOrDefault(killerPlayer, 0) + 1);
            
            // Récompense le tueur
            rewardKiller(killerPlayer);
        }
        
        // Éliminer le joueur (devenir spectateur)
        game.eliminatePlayer(player, "a été éliminé", false);
        
        // Effets de mort
        player.sendTitle("§c💀 ÉLIMINÉ", "§7" + deathMessage, 10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 5);
        
        // Statistiques
        player.sendMessage("§6Statistiques:");
        player.sendMessage("§7Kills: §e" + kills.getOrDefault(player, 0));
        player.sendMessage("§7Temps de survie: §e" + gameTime + " secondes");
    }
    
    private String getDeathMessage(Player player, org.bukkit.entity.Entity killer) {
        if (killer instanceof Player) {
            return "§6" + player.getName() + " §ca été tué par §6" + ((Player) killer).getName() + "§c !";
        } else if (killer != null) {
            return "§6" + player.getName() + " §cest mort(e) de " + killer.getName() + "§c !";
        } else {
            return "§6" + player.getName() + " §cest mort(e) !";
        }
    }
    
    private void rewardKiller(Player killer) {
        // Soigner le tueur
        killer.setHealth(Math.min(killer.getHealth() + 4, killer.getMaxHealth()));
        
        // Effets visuels
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        killer.spawnParticle(Particle.HEART, killer.getLocation().add(0, 2, 0), 5);
        
        // Message
        killer.sendMessage("§a+1 kill ! §7(Vous avez été soigné)");
    }
    
    
    
    
    private void startGameTimer(Game game) {
        gameTimerTask = Bukkit.getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
                    if (gameTimerTask != null) {
                        gameTimerTask.cancel();
                    }
                    return;
                }
                
                gameTime++;
                
                // Mettre à jour l'action bar
                updateActionBars(game);
            }, 0L, 20L);
    }
    
    private void updateActionBars(Game game) {
        for (Player player : game.getOnlinePlayers()) {
            int playerKills = kills.getOrDefault(player, 0);
            String pvpStatus = pvpEnabled ? "§4⚔ PVP ON" : "§a⚔ PVP dans " + (10 - Math.min(10, gameTime)) + "s";
            
            player.sendActionBar("§8| §7Kills: §e" + playerKills + " §8| " + pvpStatus);
        }
    }
    
    private void stopAllTasks() {
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        if (pvpTask != null) {
            pvpTask.cancel();
            pvpTask = null;
        }
    }
    
    // Enumérations
    private enum LootType {
        WEAPONS, ARMOR, POTIONS, MIXED
    }
    
    // Getters pour les statistiques
    public int getGameTime() {
        return gameTime;
    }
    
    public Map<Player, Integer> getKills() {
        return new HashMap<>(kills);
    }
    
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }
    

    @Override
    public void onPlayerDamage(Game game, EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // Annuler tous les dégâts environnementaux
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && 
                event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK &&
                event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE
                ){
                event.setCancelled(true);
            }
        }
    }
}