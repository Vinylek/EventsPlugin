package fr.celestia.events.game.types;

import fr.celestia.events.game.Game;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class SpleefGame implements GameType {
    
    private final Set<Location> brokenBlocks = new HashSet<>();
    private final Map<Player, Integer> blocksBroken = new HashMap<>();
    private BukkitTask blockRegenTask = null;
    private BukkitTask gameTimerTask = null;
    private int gameTime = 0;
    private boolean powerUpsEnabled = true;
    private final Random random = new Random();

    private final Map<org.bukkit.entity.ArmorStand, PowerUpType> activePowerUpStands = new HashMap<>();
    private final Map<Player, PowerUpType> activePowerUps = new HashMap<>();
    private final Map<Player, BukkitTask> powerUpTasks = new HashMap<>();
    
    // Configuration du Spleef
    private final Map<PowerUpType, String> POWER_UP_TEXTURES = Map.of(
    PowerUpType.SPEED_BOOST, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDZlMDNlZTA3YTBkZDNlZWNmMDE2YmI3YzM4OWQ2Yjk0ZjJkZWVkYjkyOGQ1ZjA0NmFjOWZhNzFhZDQ1MjQ0ZSJ9fX0=",
    PowerUpType.JUMP_BOOST, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGRkOWFkZjI5YjE1ZWE3NWZiNmQyZGRlMDUzOGQ1YzA1YjNhODk1YzJkM2U3MjViYzlmOTM4YzU3ZTAyY2Y2OSJ9fX0="
    );

    private final double POWER_UP_SPAWN_CHANCE = 0.8; // Pourcentage de chance

    private final int ARENA_LAYERS = 3; // Nombre d'étages
    private final int LAYER_HEIGHT = 8; // Hauteur entre chaque étage
    private final int LAYER_RADIUS = 30; // Rayon de chaque disque
    private final int FALL_DEATH_MARGIN = 5; // Marge sous le dernier étage pour la chute mortelle
    

    private final Map<Snowball, Player> snowballThrowers = new HashMap<>();
    private final double SNOWBALL_KNOCKBACK = 1.2;
    private boolean snowballsEnabled = true;
    
    @Override
    public void onStart(Game game) {
        game.broadcastMessage("§b§lSpleef §ecommence !");

        // Préparer l'arène
        prepareArena(game);
        
        // Donner les pelles aux joueurs
        giveSpleefEquipment(game);
        
        // Démarrer le timer du jeu
        startGameTimer(game);
    
        
        // Démarrer le spawn des power-ups
        if (powerUpsEnabled) {
            startPowerUpSpawning(game);
        }
    }
    
    @Override
    public void onEnd(Game game) {
        stopAllTasks();
        
        // Nettoyer tous les power-ups actifs et les ArmorStands
        for (Player player : new HashSet<>(activePowerUps.keySet())) {
            removePlayerPowerUp(player);
        }
        
        for (org.bukkit.entity.ArmorStand armorStand : new HashSet<>(activePowerUpStands.keySet())) {
            removePowerUpStand(armorStand);
        }
        
        brokenBlocks.clear();
        blocksBroken.clear();
        activePowerUps.clear();
        activePowerUpStands.clear();
        
        // Régénérer l'arène
        prepareArena(game);
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        // Instructions
        player.sendMessage("§b§lInstructions Spleef:");
        player.sendMessage("§e• Cassez les blocs sous vos adversaires");
        player.sendMessage("§e• Évitez de tomber");
        player.sendMessage("§e• Collectez les power-ups pour des avantages");
        player.sendMessage("§e• Le dernier survivant gagne !");
        player.sendMessage("§b❄ Utilisez les boules de neige pour pousser les joueurs !");
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        blocksBroken.remove(player);
    }
    
    @Override
    public void onPlayerMove(Game game, PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        // Vérifier si le joueur est tombé sous le dernier étage
        int lowestLayerY = getLowestLayerY(game);
        if (to.getY() < lowestLayerY - FALL_DEATH_MARGIN) {
            handlePlayerFall(game, player);
            return;
        }
        
    }

    
    
    @Override
    public void onPlayerDamage(Game game, EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // Autoriser seulement les dégâts de chute et de lave
            if (event.getCause() != EntityDamageEvent.DamageCause.FALL && 
                event.getCause() != EntityDamageEvent.DamageCause.LAVA &&
                event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
                event.setCancelled(true);
            }
        }
    }
    
    @Override
    public void onBlockBreak(Game game, BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Vérifier si le bloc peut être cassé
        if (!isBreakableBlock(block.getType())) {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez casser que les blocs de neige et de glace !");
            return;
        }
        
        // Vérifier si le bloc est déjà en cours de régénération
        if (brokenBlocks.contains(block.getLocation())) {
            event.setCancelled(true);
            return;
        }
        
        // Autoriser la casse et gérer les effets
        handleBlockBreak(game, player, block);
        event.setDropItems(false);
        event.setCancelled(false); // Laisser Minecraft gérer la casse normale
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
        // Utiliser la préparation par défaut
        GameType.super.preparePlayer(game, player);
        player.setGameMode(GameMode.SURVIVAL);
        
        // Effet de night vision pour mieux voir
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.NIGHT_VISION, 
            Integer.MAX_VALUE, 0, false, false));
        


    }
    
    @Override
    public void resetPlayer(Game game, Player player) {
        // Utiliser le reset par défaut
        GameType.super.resetPlayer(game, player);
        
        // Retirer les effets
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
    }
    
    @Override
    public String getDisplayName() {
        return "Spleef";
    }
    
    @Override
    public String getDescription() {
        return "Cassez les blocs sous vos adversaires pour les faire tomber dans la lave !";
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public int getWaitingTime() {
        return 60;
    }
    
    // Méthodes spécifiques au Spleef
    
    private void prepareArena(Game game) {
        Location arenaLocation = game.getArenaLocation();
        if (arenaLocation == null) return;
        
        World world = arenaLocation.getWorld();
        int centerX = arenaLocation.getBlockX();
        int centerZ = arenaLocation.getBlockZ();
        int baseY = arenaLocation.getBlockY() - 1; // Le point central du disque le plus haut
        
        
        // Créer 3 étages empilés
        for (int layer = 0; layer < ARENA_LAYERS; layer++) {
            int layerY = baseY - (layer * LAYER_HEIGHT);
            createArenaLayer(world, centerX, layerY, centerZ, LAYER_RADIUS, layer);
        }
        
    }

    private void createArenaLayer(World world, int centerX, int centerY, int centerZ, int radius, int layerIndex) {
        // Créer un disque de neige pour cet étage
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                // Vérifier si on est dans le cercle
                if (Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2) <= Math.pow(radius, 2)) {
                    Block block = world.getBlockAt(x, centerY, z);
                    block.setType(Material.SNOW_BLOCK);
                    
                    // Ajouter quelques blocs de glace pour la variété (10% de chance)
                    if (layerIndex > 0 && new Random().nextInt(100) < 10) {
                        block.setType(Material.PACKED_ICE);
                    }
                }
            }
        }
    }
    
    private void giveSpleefEquipment(Game game) {
        for (Player player : game.getActivePlayers()) {
            // Pelle en diamant avec enchantements
            ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
            ItemMeta meta = shovel.getItemMeta();
            meta.setDisplayName("§bPelle de Spleef");
            meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            shovel.setItemMeta(meta);
            
            player.getInventory().setItem(0, shovel);
            
            // Bottes avec feather falling pour réduire les dégâts de chute
            ItemStack boots = new ItemStack(Material.IRON_BOOTS);
            ItemMeta bootsMeta = boots.getItemMeta();
            bootsMeta.addEnchant(Enchantment.FEATHER_FALLING, 2, true);
            boots.setItemMeta(bootsMeta);
            
            player.getInventory().setBoots(boots);
        }
    }
    
    private void handleBlockBreak(Game game, Player player, Block block) {
        Location blockLocation = block.getLocation();
        
        // Ajouter aux blocs cassés (pour la régénération)
        brokenBlocks.add(blockLocation);
        
        // Compter les blocs cassés par le joueur
        blocksBroken.put(player, blocksBroken.getOrDefault(player, 0) + 1);
        
        // Effets visuels et sonores
        player.playSound(blockLocation, Sound.BLOCK_SNOW_BREAK, 1.0f, 1.0f);
        block.getWorld().spawnParticle(Particle.CLOUD, blockLocation.add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.1);
        
        // Chance de spawner un power-up
        if (powerUpsEnabled && random.nextDouble(100) < POWER_UP_SPAWN_CHANCE) {
            spawnPowerUp(game, blockLocation);
        }
        
        player.give(new ItemStack(Material.SNOWBALL));
        
    }

    public void handleSnowballLaunch(Game game, Player player, Snowball snowball) {
        if (!snowballsEnabled) return;
        
        // Enregistrer le lanceur
        snowballThrowers.put(snowball, player);
        
        // Effets visuels au lancement
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);
        
        // Suivi de la boule de neige
        trackSnowball(game, snowball);
    }
    
    /**
     * Gère l'impact des boules de neige
     */
    public void handleSnowballImpact(Game game, Snowball snowball, org.bukkit.entity.Entity hitEntity) {
        Player thrower = snowballThrowers.get(snowball);
        if (thrower == null) return;
        
        Location impactLocation = snowball.getLocation();
        
        // Effet sur les joueurs
        if (hitEntity instanceof Player) {
            handleSnowballPlayerImpact(game, thrower, (Player) hitEntity, snowball);
        }
        
        // Effet sur les blocs
        handleSnowballBlockImpact(game, thrower, impactLocation);
        
        // Effets visuels d'impact
        createSnowballImpactEffects(impactLocation);
        
        // Nettoyer
        snowballThrowers.remove(snowball);
    }
    
    private void handleSnowballPlayerImpact(Game game, Player thrower, Player hitPlayer, Snowball snowball) {
        // Vérifier que les deux joueurs sont dans le jeu
        if (!game.getPlayers().contains(thrower.getUniqueId()) || 
            !game.getPlayers().contains(hitPlayer.getUniqueId())) {
            return;
        }
        
        // Calculer la direction du knockback
        org.bukkit.util.Vector direction = hitPlayer.getLocation()
            .subtract(thrower.getLocation())
            .toVector()
            .normalize();
        
        // Appliquer le knockback
        direction.multiply(SNOWBALL_KNOCKBACK);
        direction.setY(direction.getY() + 0.3); // Léger effet de lift
        
        hitPlayer.setVelocity(direction);
        
        // Effets visuels et sonores
        hitPlayer.playSound(hitPlayer.getLocation(), Sound.ENTITY_SNOW_GOLEM_HURT, 1.0f, 1.2f);
        hitPlayer.spawnParticle(Particle.END_ROD, hitPlayer.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
        
    }
    
    private void handleSnowballBlockImpact(Game game, Player thrower, Location impactLocation) {
        
        // Vérifier si le bloc est cassable et dans l'arène
        for (int y = -1; y <= 1; y++) {
            Block block = impactLocation.clone().add(0, y, 0).getBlock();
            
            if (isBreakableBlock(block.getType()) && isBlockInArena(game, block.getLocation())) {
                block.breakNaturally();
                brokenBlocks.add(block.getLocation());
                    
            }
        }
    }

    
    private boolean isBlockInArena(Game game, Location blockLocation) {
        Location arenaLocation = game.getArenaLocation();
        if (arenaLocation == null) return false;
        
        // Vérifier si le bloc est sur l'un des étages de l'arène
        for (int layer = 0; layer < ARENA_LAYERS; layer++) {
            int layerY = getArenaBaseY(game) - (layer * LAYER_HEIGHT);
            
            if (blockLocation.getBlockY() == layerY) {
                double distance = blockLocation.distance(new Location(
                    arenaLocation.getWorld(), 
                    arenaLocation.getX(), 
                    layerY, 
                    arenaLocation.getZ()
                ));
                
                return distance <= LAYER_RADIUS;
            }
        }
        
        return false;
    }
    
    private void createSnowballImpactEffects(Location location) {
        // Particules d'explosion de neige
        location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 30, 1.0, 1.0, 1.0, 0.2);
        location.getWorld().spawnParticle(Particle.CLOUD, location, 20, 1.0, 1.0, 1.0, 0.1);
        
        // Son d'impact
        location.getWorld().playSound(location, Sound.BLOCK_SNOW_BREAK, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.8f);
        
        // Effet de poudre de neige qui retombe
    }
    
    private void trackSnowball(Game game, Snowball snowball) {
        final Snowball[] snowballRef = new Snowball[]{snowball};
        
        BukkitTask trackTask = Bukkit.getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (snowballRef[0] == null || snowballRef[0].isDead()) {
                    return;
                }
                
                // Traînée de particules
                Location snowballLoc = snowballRef[0].getLocation();
                snowballLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, snowballLoc, 2, 0.1, 0.1, 0.1, 0.01);
                
            }, 0L, 2L); // Toutes les 2 ticks
        
        // Arrêter le tracking après 5 secondes (au cas où)
        Bukkit.getScheduler().runTaskLater(
            game.getGameManager().getPlugin(), () -> {
                trackTask.cancel();
                snowballThrowers.remove(snowballRef[0]);
            }, 100L);
    }

    private void handlePlayerFall(Game game, Player player) {
        if (!game.getPlayers().contains(player.getUniqueId())) return;
        
        player.sendTitle("§cÉliminé!", "§7Tu es tombé!", 10, 40, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        
        // Effets d'explosion
        player.getWorld().spawnParticle(Particle.LAVA, player.getLocation(), 20);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
        
        // Éliminer le joueur (devenir spectateur)
        game.eliminatePlayer(player, "est tombé", false);
        
        game.broadcastMessage("§6" + player.getName() + " §cest tombé ! " + 
                             game.getPlayerCount() + " joueur(s) restant(s).");
    }
    
    private void startGameTimer(Game game) {
        gameTimerTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
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
                
                // Événements spéciaux selon le temps
                handleTimedEvents(game);
            }, 0L, 20L);
    }

    
    private void startPowerUpSpawning(Game game) {
        game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
                    return;
                }
                
                // Spawner un power-up à un endroit aléatoire
                if (random.nextInt(100) < 20) { // 20% de chance toutes les 10 secondes
                    spawnRandomPowerUp(game);
                }
            }, 200L, 200L); // Toutes les 10 secondes
    }

    
    /**
     * Power-ups aléatoires qui donnent des avantages temporaires
     */

    private void spawnRandomPowerUp(Game game) {
        Location arenaLocation = game.getArenaLocation();
        if (arenaLocation == null) return;
        
        // Trouver un emplacement sûr sur l'un des étages
        int randomLayer = random.nextInt(ARENA_LAYERS);
        int layerY = getArenaBaseY(game) - (randomLayer * LAYER_HEIGHT);
        
        // Position aléatoire sur le disque de l'étage
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * (LAYER_RADIUS - 3); // Éviter les bords
        
        int x = (int) (arenaLocation.getX() + Math.cos(angle) * distance);
        int z = (int) (arenaLocation.getZ() + Math.sin(angle) * distance);
        
        Location spawnLocation = new Location(arenaLocation.getWorld(), x, layerY + 1, z);
        
        // Vérifier que l'emplacement est libre
        if (isLocationSafeForPowerUp(spawnLocation)) {
            spawnPowerUp(game, spawnLocation);
        }
    }

    private boolean isLocationSafeForPowerUp(Location location) {
        // Vérifier qu'il n'y a pas déjà un power-up à proximité
        for (org.bukkit.entity.ArmorStand existingStand : activePowerUpStands.keySet()) {
            if (existingStand.isValid() && existingStand.getLocation().distance(location) < 3.0) {
                return false;
            }
        }
        
        // Vérifier que l'emplacement n'est pas occupé par un bloc
        return location.getBlock().getType() == Material.AIR &&
            location.clone().add(0, 1, 0).getBlock().getType() == Material.AIR;
    }
    
    private void spawnPowerUp(Game game, Location location) {
        PowerUpType[] powerUps = PowerUpType.values();
        PowerUpType randomPowerUp = powerUps[random.nextInt(powerUps.length)];
        
        // Créer l'entité du power-up
        org.bukkit.entity.ArmorStand armorStand = createPowerUpArmorStand(game, location, randomPowerUp);
        activePowerUpStands.put(armorStand, randomPowerUp);
        
        // Effets visuels de spawn
        location.getWorld().spawnParticle(Particle.END_ROD, location.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_EYE_LAUNCH, 1.0f, 1.5f);
    
        
        // Supprimer le power-up après 30 secondes
        Bukkit.getScheduler().runTaskLater(
        game.getGameManager().getPlugin(), () -> {
            if (armorStand.isValid()) {
                removePowerUpStand(armorStand);
            }
        }, 600L);
    }
    
    private org.bukkit.entity.ArmorStand createPowerUpArmorStand(Game game, Location location, PowerUpType powerUp) {
        World world = location.getWorld();
        
        // Créer l'ArmorStand
        org.bukkit.entity.ArmorStand armorStand = world.spawn(location.add(0.5, 0, 0.5), org.bukkit.entity.ArmorStand.class);
        
        // Configurer l'ArmorStand pour la collision
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setCanPickupItems(false);
        armorStand.setCollidable(true); 
        armorStand.setMarker(false); 
        armorStand.setCustomName(powerUp.getName());
        armorStand.setCustomNameVisible(true);
        
        
        // Taille légèrement plus grande pour faciliter la collision
        armorStand.setSmall(false);
        
        // Ajouter la tête custom
        String texture = POWER_UP_TEXTURES.get(powerUp);
        if (texture != null) {
            ItemStack head = createCustomHead(texture, powerUp.getName());
            armorStand.setHelmet(head);
        }
        
        // Effet de glow
        armorStand.setGlowing(true);
        
        return armorStand;
    }

    // TODO: A fixer
    private ItemStack createCustomHead(String texture, String displayName) {
        try {
            // Créer une tête custom avec la texture
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            
            // Utiliser la reflection pour définir la texture (méthode plus moderne)
            java.lang.reflect.Method method = meta.getClass().getMethod("setPlayerProfile", org.bukkit.profile.PlayerProfile.class);
            org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID());
            
            java.lang.reflect.Method getTexturesMethod = profile.getClass().getMethod("getTextures");
            org.bukkit.profile.PlayerTextures textures = (org.bukkit.profile.PlayerTextures) getTexturesMethod.invoke(profile);
            
            java.net.URL textureUrl = new java.net.URL("http://textures.minecraft.net/texture/" + texture);
            java.lang.reflect.Method setSkinMethod = textures.getClass().getMethod("setSkin", java.net.URL.class);
            setSkinMethod.invoke(textures, textureUrl);
            
            java.lang.reflect.Method setTexturesMethod = profile.getClass().getMethod("setTextures", org.bukkit.profile.PlayerTextures.class);
            setTexturesMethod.invoke(profile, textures);
            
            method.invoke(meta, profile);
            meta.setDisplayName(displayName);
            head.setItemMeta(meta);
            
            return head;
        } catch (Exception e) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    

    private void removePowerUpStand(org.bukkit.entity.ArmorStand armorStand) {
        // Effets de disparition
        if (armorStand.isValid()) {
            Location loc = armorStand.getLocation();
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.3, 0.3, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            armorStand.remove();
        }
        activePowerUpStands.remove(armorStand);
    }
    
    /**
     * Gère la collecte d'un power-up
     */

    public void handlePowerUpCollision(Game game, Player player, org.bukkit.entity.ArmorStand armorStand) {
        PowerUpType powerUp = activePowerUpStands.get(armorStand);
        if (powerUp == null) return;
        
        // Vérifier que le joueur est actif (pas spectateur)
        if (!game.getPlayers().contains(player.getUniqueId())) {
            return;
        }
        
        // Retirer l'ancien power-up si le joueur en a déjà un
        removePlayerPowerUp(player);
        
        // Appliquer le nouvel effet
        applyPowerUpEffect(game, player, powerUp);
        
        // Stocker le power-up actif
        activePowerUps.put(player, powerUp);
        
        // Supprimer l'ArmorStand avec effets
        removePowerUpStandWithEffects(armorStand, player.getLocation());
        
        // Effets visuels et sonores de collecte
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.1);
        
        // Message au joueur
        player.sendMessage("§a⚡ Power-up collecté: " + powerUp.getName() + " §7(" + powerUp.getDuration() + "s)");
                
        // Planifier la fin de l'effet (sauf pour les power-ups à usage unique)
        if (powerUp.getDuration() > 0) {
            BukkitTask powerUpTask = Bukkit.getScheduler().runTaskLater(
                game.getGameManager().getPlugin(), () -> {
                    removePlayerPowerUp(player);
                    player.sendMessage("§7L'effet " + powerUp.getName() + " §7s'est dissipé.");
                }, powerUp.getDuration() * 20L);
            
            powerUpTasks.put(player, powerUpTask);
        }
    }

    private void removePowerUpStandWithEffects(org.bukkit.entity.ArmorStand armorStand, Location playerLocation) {
        // Effets de collecte
        if (armorStand.isValid()) {
            Location loc = armorStand.getLocation();
            
            // Particules directionnelles vers le joueur
            org.bukkit.util.Vector direction = playerLocation.toVector().subtract(loc.toVector()).normalize();
            for (int i = 0; i < 10; i++) {
                loc.getWorld().spawnParticle(Particle.END_ROD, 
                    loc, 1, direction.getX() * 0.1, direction.getY() * 0.1, direction.getZ() * 0.1, 0.1);
            }
            
            // Son de collecte
            loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
            
            armorStand.remove();
        }
        activePowerUpStands.remove(armorStand);
    }
    
    private void applyPowerUpEffect(Game game, Player player, PowerUpType powerUp) {
        switch (powerUp) {
            case SPEED_BOOST:
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, powerUp.getDuration() * 20, 1, false, true));
                break;
                
            case JUMP_BOOST:
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.JUMP_BOOST, powerUp.getDuration() * 20, 1, false, true));
                break;
                
            
        }
    }
    
    private void removePlayerPowerUp(Player player) {
        PowerUpType oldPowerUp = activePowerUps.remove(player);
        if (oldPowerUp != null) {
            // Retirer les effets spécifiques
            switch (oldPowerUp) {
                case SPEED_BOOST:
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
                    break;
                case JUMP_BOOST:
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
                    break;
            }
        }
        
        // Arrêter la tâche du power-up
        BukkitTask task = powerUpTasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }
    

    
    
    /**
     * Événements temporels pour garder le jeu dynamique
     */
    private void handleTimedEvents(Game game) {
        // Toutes les 30 secondes
        if (gameTime % 30 == 0) {
            game.broadcastMessage("§6⚡ §eL'arène se rétrécit ! §6⚡");
            shrinkArena(game);
        }
    
    }
    
    private void shrinkArena(Game game) {
        Location arenaLocation = game.getArenaLocation();
        if (arenaLocation == null) return;
        
        World world = arenaLocation.getWorld();
        
        // Réduire le rayon de 2 blocs toutes les 30 secondes
        int shrinkAmount = (gameTime / 30) * 2;
        int currentRadius = LAYER_RADIUS - shrinkAmount;
        
        // Empêcher le rayon de devenir trop petit
        if (currentRadius < 5) {
            currentRadius = 5;
        }
        
        // Rétrécir chaque étage
        for (int layer = 0; layer < ARENA_LAYERS; layer++) {
            int layerY = getArenaBaseY(game) - (layer * LAYER_HEIGHT);
            shrinkArenaLayer(game, world, arenaLocation.getBlockX(), layerY, arenaLocation.getBlockZ(), 
                            LAYER_RADIUS, currentRadius, layer);
        }
        
        // Effets sonores et visuels
        for (Player player : game.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
            player.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 2, 0), 20, 2, 1, 2, 0.1);
        }
    }

    private void shrinkArenaLayer(Game game, World world, int centerX, int centerY, int centerZ, 
                                int originalRadius, int currentRadius, int layerIndex) {
        // Zone à casser : entre le rayon actuel et le rayon original
        int breakStartRadius = currentRadius;
        int breakEndRadius = originalRadius;
        
        for (int x = centerX - breakEndRadius; x <= centerX + breakEndRadius; x++) {
            for (int z = centerZ - breakEndRadius; z <= centerZ + breakEndRadius; z++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                
                // Casser les blocs dans la zone annulaire à rétrécir
                if (distance > breakStartRadius && distance <= breakEndRadius) {
                    Block block = world.getBlockAt(x, centerY, z);
                    
                    if (isBreakableBlock(block.getType())) {
                        // Effets avant la casse
                        block.getWorld().spawnParticle(Particle.BLOCK, 
                            block.getLocation().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0.1, block.getBlockData());
                        
                        // Casser le bloc après un court délai pour l'effet dramatique
                        final Block finalBlock = block;
                        Bukkit.getScheduler().runTaskLater(
                            game.getGameManager().getPlugin(), () -> {
                                if (finalBlock.getType() != Material.AIR && isBreakableBlock(finalBlock.getType())) {
                                    finalBlock.breakNaturally();
                                    brokenBlocks.add(finalBlock.getLocation());
                                    
                                    // Son de casse
                                    finalBlock.getWorld().playSound(finalBlock.getLocation(), 
                                        Sound.BLOCK_SNOW_BREAK, 0.5f, 1.0f);
                                }
                            }, layerIndex * 3L); // Délai progressif selon l'étage
                    }
                    

                    }
                }
            }
        }




    
    // Méthodes utilitaires
    private boolean isBreakableBlock(Material material) {
        return material == Material.SNOW_BLOCK || 
               material == Material.PACKED_ICE || 
               material == Material.ICE ||
               material == Material.BLUE_ICE;
    }
    
    private int getLowestLayerY(Game game) {
        Location arenaLocation = game.getArenaLocation();
        return arenaLocation.getBlockY() - 1 - ((ARENA_LAYERS - 1) * LAYER_HEIGHT);
    }
    
    private int getArenaBaseY(Game game) {
        return game.getArenaLocation().getBlockY() - 1;
    }
    
    private void regenerateArena(Game game) {
        // Implémentation similaire à prepareArena
        prepareArena(game);
    }
    
    private void regenerateSomeBlocks(Game game) {
        // Régénérer quelques blocs cassés aléatoirement
        int blocksToRegen = Math.min(10, brokenBlocks.size());
        
        List<Location> blocksList = new ArrayList<>(brokenBlocks);
        Collections.shuffle(blocksList);
        
        for (int i = 0; i < blocksToRegen; i++) {
            Location location = blocksList.get(i);
            Block block = location.getBlock();
            
            // Remettre un bloc cassable (alternance neige/glace)
            Material blockType = (location.getBlockY() % 2 == 0) ? Material.SNOW_BLOCK : Material.PACKED_ICE;
            block.setType(blockType);
            
            brokenBlocks.remove(location);
            
            // Effet visuel
            block.getWorld().spawnParticle(Particle.SNOWFLAKE, location.add(0.5, 0.5, 0.5), 5);
        }
    }
    
    private void updateActionBars(Game game) {
        for (Player player : game.getOnlinePlayers()) {
            int blocksBrokenCount = blocksBroken.getOrDefault(player, 0);
            player.sendActionBar("§bSpleef §7| §fBlocs: §e" + blocksBrokenCount + " §7| §fTemps: §e" + gameTime + "s");
        }
    }
    
    private void stopAllTasks() {
        if (blockRegenTask != null) {
            blockRegenTask.cancel();
            blockRegenTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
    }
    
    // Enumération des power-ups
    public enum PowerUpType {
        SPEED_BOOST("§bBoost de Vitesse", Material.SUGAR, "§7Vitesse augmentée pendant 15s", 15),
        JUMP_BOOST("§aSuper Saut", Material.FEATHER, "§7Saut amélioré pendant 15s", 15);
        
        private final String name;
        private final Material material;
        private final String description;
        private final int duration;
        
        PowerUpType(String name, Material material, String description, int duration) {
            this.name = name;
            this.material = material;
            this.description = description;
            this.duration = duration;
        }

        public String getName() { return name; }
        public int getDuration() { return duration; }
        
        public ItemStack getItem() {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(
                description,
                "§8Durée: " + (duration > 0 ? duration + "s" : "Usage unique")
            ));
            item.setItemMeta(meta);
            return item;
        }
    }
    
    // Getters pour les statistiques
    public int getGameTime() {
        return gameTime;
    }
    
    public Map<Player, Integer> getBlocksBroken() {
        return new HashMap<>(blocksBroken);
    }
    
    public int getTotalBrokenBlocks() {
        return brokenBlocks.size();
    }
}