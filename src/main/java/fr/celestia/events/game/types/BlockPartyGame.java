package fr.celestia.events.game.types;

import fr.celestia.events.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.regex.Matcher;

public class BlockPartyGame implements GameType {
    
    private BukkitTask gameTask = null;
    private BukkitTask roundTask = null;
    private int gameTime = 0;
    private int currentRound = 0;
    private final Random random = new Random();
    
    // Configuration du BlockParty
    private final int BASE_ROUND_TIME = 7; // Temps de base pour le premier round
    private final int ROUND_DECREASE = 1; // Réduction du temps par round
    private final int MIN_ROUND_TIME = 3; // Temps minimum par round
    private final int REGENERATION_TIME = 3; // Temps avant régénération après disparition
    private final int ARENA_RADIUS = 15;
    
    // Couleurs des plates-formes
    private final Material[] PLATFORM_COLORS = {
        Material.WHITE_WOOL,
        Material.ORANGE_WOOL,
        Material.MAGENTA_WOOL,
        Material.LIGHT_BLUE_WOOL,
        Material.YELLOW_WOOL,
        Material.LIME_WOOL,
        Material.PINK_WOOL,
        Material.GRAY_WOOL,
        Material.LIGHT_GRAY_WOOL,
        Material.CYAN_WOOL,
        Material.PURPLE_WOOL,
        Material.BLUE_WOOL,
        Material.BROWN_WOOL,
        Material.GREEN_WOOL,
        Material.RED_WOOL,
        Material.BLACK_WOOL
    };
    
    private Material currentColor;
    private Material nextColor;
    private final Set<Location> platformBlocks = new HashSet<>();
    private final Set<Location> safeBlocks = new HashSet<>();
    private boolean isRoundActive = false;
    private int roundTimeLeft = 0;

    @Override
    public void onStart(Game game) {
        game.broadcastMessage("§d§lBLOCK PARTY §ecommence !");
        game.broadcastMessage("§7Survivez en restant sur la bonne couleur !");
        game.broadcastMessage("§6🎨 §eLa couleur safe change à chaque round");
        game.broadcastMessage("§c⚡ §eLes rounds deviennent de plus en plus rapides !");
        
        // Téléporter les joueurs
        teleportPlayersToPlatform(game);
        
        // Générer la plate-forme initiale
        generatePlatform(game);
        
        // Démarrer le premier round
        startNewRound(game);
        
        // Démarrer la boucle principale
        startGameLoop(game);
    }
    
    @Override
    public void onEnd(Game game) {
        stopAllTasks();
        platformBlocks.clear();
        safeBlocks.clear();
        
        // Restaurer la plate-forme
        restorePlatform(game);
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        preparePlayer(game, player);
        if (game.getState() == fr.celestia.events.game.GameState.RUNNING) {
            player.teleport(game.getArenaLocation().add(0, 1, 0));
        }
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        // Rien
    }
    
    @Override
    public void onPlayerMove(Game game, PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        // Vérifier si le joueur tombe
        if (to.getY() < getPlatformCenter(game).getY() - 10 && (player.getGameMode() == GameMode.ADVENTURE)) {
            eliminatePlayer(game, player, "est tombé dans le vide");
            return;
        }
        
    }
    
    
    @Override
    public void onBlockBreak(Game game, BlockBreakEvent event) {
        event.setCancelled(true);
    }
    
    @Override
    public boolean checkWinConditions(Game game) {
        List<Player> activePlayers = game.getActivePlayers();
        
        if (activePlayers.size() == 1) {
            Player winner = activePlayers.get(0);
            game.broadcastMessage("§6🎉 " + winner.getName() + " a gagné le Block Party !");
            game.broadcastMessage("§7Rounds survécus: §e" + currentRound);
            return true;
        }
        
        if (activePlayers.isEmpty()) {
            game.broadcastMessage("§cMatch nul ! Personne n'a survécu.");
            return true;
        }
        
        return false;
    }
    
    @Override
    public void preparePlayer(Game game, Player player) {
        GameType.super.preparePlayer(game, player);
        
        // Instructions
        player.sendMessage("§d§l=== BLOCK PARTY ===");
        player.sendMessage("§e• Survivre en restant sur la bonne couleur !");
        player.sendMessage("§e• Tous les blocs non-safe disparaissent d'un coup");
        player.sendMessage("§e• Les rounds deviennent de plus en plus rapides");
        player.sendMessage("§aAmusez-vous bien !");
    }
    
    @Override
    public void resetPlayer(Game game, Player player) {
        GameType.super.resetPlayer(game, player);
    }
    
    @Override
    public String getDisplayName() {
        return "Block Party";
    }
    
    @Override
    public String getDescription() {
        return "Survivez en restant sur la bonne couleur ! Les rounds deviennent de plus en plus rapides !";
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public int getWaitingTime() {
        return 30;
    }
    
    @Override
    public void onPlayerDamage(Game game, EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            // Annuler tous les dégâts sauf le vide
            if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
            }
        }
    }
    
    // MÉTHODES SPÉCIFIQUES AU BLOCK PARTY
    
    private void startGameLoop(Game game) {
        gameTask = Bukkit.getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(),
            () -> {
                if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
                    stopAllTasks();
                    return;
                }
                
                gameTime++;
                updateActionBars(game);
            },
            0L, 20L
        );
    }
    
    private void startNewRound(Game game) {
        if (game.getState() != fr.celestia.events.game.GameState.RUNNING) {
            return; 
        }

        currentRound++;
        isRoundActive = true;
        
        // Calculer le temps pour ce round (de plus en plus court)
        roundTimeLeft = Math.max(MIN_ROUND_TIME, BASE_ROUND_TIME - (currentRound - 1));
        
        // Choisir une nouvelle couleur safe
        Material newColor;
        do {
            newColor = PLATFORM_COLORS[random.nextInt(PLATFORM_COLORS.length)];
        } while (newColor == currentColor);
        
        currentColor = newColor;
        
        // Préparer la couleur suivante pour l'annonce
        do {
            nextColor = PLATFORM_COLORS[random.nextInt(PLATFORM_COLORS.length)];
        } while (nextColor == currentColor);
        
        // Identifier les blocs safe
        identifySafeBlocks();
        
        // Annoncer le nouveau round - AVEC COMPONENTS
        Component roundMessage = Component.text()
            .append(Component.text("ROUND " + currentRound).color(TextColor.color(0xD500F9)).decorate(TextDecoration.BOLD))
            .build();
        
        Component safeColorMessage = Component.text()
            .append(Component.text("🎨 COULEUR SAFE: ").color(TextColor.color(0xD500F9)))
            .append(getHexColorComponent(currentColor))
            .build();
        
        Component timeMessage = Component.text()
            .append(Component.text("⏰ Temps: ").color(TextColor.color(0xFFA500)))
            .append(Component.text(roundTimeLeft + " secondes").color(TextColor.color(0xFFFF00)))
            .build();
        
        // Utiliser l'API Adventure pour broadcast
        for (Player player : game.getOnlinePlayers()) {
            player.sendMessage(roundMessage);
            player.sendMessage(safeColorMessage);
            player.sendMessage(timeMessage);
        }
        
        // Effets visuels et sonores
        for (Player player : game.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
            
            // Title avec components
            Component title = Component.text()
                .append(Component.text("✦ ").color(TextColor.color(0xFFFFFF)))
                .append(getHexColorComponent(currentColor))
                .append(Component.text(" ✦").color(TextColor.color(0xFFFFFF)))
                .build();
                
            Component subtitle = Component.text()
                .append(Component.text("Round " + currentRound + " | " + roundTimeLeft + "s").color(TextColor.color(0x808080)))
                .build();
                
            player.showTitle(Title.title(title, subtitle, 
                Title.Times.times(java.time.Duration.ofMillis(500), 
                                java.time.Duration.ofMillis(3000), 
                                java.time.Duration.ofMillis(1000))));
        }
        
        // Démarrer le compte à rebours du round
        startRoundCountdown(game);
    }
    
    private void startRoundCountdown(Game game) {
        if (roundTask != null) {
            roundTask.cancel();
        }
        
        roundTask = Bukkit.getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(),
            () -> {
                if (!isRoundActive) return;
                
                roundTimeLeft--;
                
                // Avertissements sonores
                if (roundTimeLeft <= 5 && roundTimeLeft > 0) {
                    for (Player player : game.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 
                                       0.5f, 1.0f + (5 - roundTimeLeft) * 0.1f);
                    }
                }
                
                // Fin du round - faire disparaître les blocs non-safe
                if (roundTimeLeft <= 0) {
                    isRoundActive = false;
                    removeUnsafeBlocks(game);
                    
                    // Démarrer la régénération après un délai
                    Bukkit.getScheduler().runTaskLater(
                        game.getGameManager().getPlugin(),
                        () -> regeneratePlatformAndNextRound(game),
                        REGENERATION_TIME * 20L
                    );
                    
                    roundTask.cancel();
                }
            },
            0L, 20L
        );
    }
    
    private void identifySafeBlocks() {
        safeBlocks.clear();
        
        for (Location blockLoc : platformBlocks) {
            Block block = blockLoc.getBlock();
            if (block.getType() == currentColor) {
                safeBlocks.add(blockLoc);
            }
        }
    }
    
    private void removeUnsafeBlocks(Game game) {
        int blocksRemoved = 0;
        
        game.broadcastMessage("§cDISPARITION DES BLOCS !");
        
        for (Location blockLoc : platformBlocks) {
            if (!safeBlocks.contains(blockLoc)) {
                Block block = blockLoc.getBlock();
                if (block.getType() != Material.AIR && block.getType() != currentColor) {
                    // Effets de destruction
                    block.getWorld().spawnParticle(
                        Particle.BLOCK,
                        blockLoc.clone().add(0.5, 0.5, 0.5),
                        15, 0.3, 0.3, 0.3,
                        block.getBlockData()
                    );
                    block.getWorld().playSound(blockLoc, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    
                    // Supprimer le bloc
                    block.setType(Material.AIR);
                    blocksRemoved++;
                }
            }
        }
        
        game.broadcastMessage("§c" + blocksRemoved + " blocs ont disparu !");
    }
    
    
    private void regeneratePlatformAndNextRound(Game game) {
        // Régénérer tous les blocs de la plate-forme
        regeneratePlatform();
        
        
        // Effets de régénération
        for (Player player : game.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 20, 1, 1, 1);
        }
        
        // Démarrer le prochain round après un court délai
        Bukkit.getScheduler().runTaskLater(
            game.getGameManager().getPlugin(),
            () -> startNewRound(game),
            60L // 3 secondes de pause
        );
    }
    
    
    private void eliminatePlayer(Game game, Player player, String reason) {
        game.eliminatePlayer(player, reason, true);
        
        // Effets d'élimination
        player.sendTitle("§c💀 ÉLIMINÉ", "§7Round " + currentRound, 10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_DEATH, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.LAVA, player.getLocation(), 20);
        
    }
    
    
    private void restorePlatform(Game game) {  
        generatePlatform(game);
    }

    private void regeneratePlatform() {
        // Régénérer tous les blocs (pas seulement les air) pour un pattern cohérent
        for (Location blockLoc : platformBlocks) {
            Block block = blockLoc.getBlock();
            Material color = generatePatternColor(blockLoc);
            block.setType(color);
        }
    }

    private void generatePlatform(Game game) {
        Location center = getPlatformCenter(game);
        World world = center.getWorld();
        
        platformBlocks.clear();
        safeBlocks.clear();
        
        // Générer un plateau circulaire avec patterns
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                // Vérifier si c'est dans le cercle
                if (x * x + z * z <= ARENA_RADIUS * ARENA_RADIUS) {
                    Location blockLoc = center.clone().add(x, 0, z);
                    
                    // Générer une couleur basée sur le pattern
                    Material color = generatePatternColor(blockLoc);
                    world.getBlockAt(blockLoc).setType(color);
                    
                    platformBlocks.add(blockLoc);
                }
            }
        }
    }

    // Méthodes pour la génération de patterns
    private Material generatePatternColor(Location location) {
        double x = location.getX();
        double z = location.getZ();
        
        // Pattern en zones de 3x3 blocs pour créer des "taches" de couleur
        int zoneX = (int) Math.floor(x / 3);
        int zoneZ = (int) Math.floor(z / 3);
        
        // Hash déterministe basé sur la zone
        int hash = (zoneX * 73856093) ^ (zoneZ * 19349663);
        hash = Math.abs(hash) % PLATFORM_COLORS.length;
        
        return PLATFORM_COLORS[hash];
    }

    
    private Material generateOrganicPatternColor(Location location) {
        double x = location.getX();
        double z = location.getZ();
        
        // Bruit plus simple et cohérent
        double noise1 = Math.sin(x * 0.1) * Math.cos(z * 0.1);
        double noise2 = Math.sin(x * 0.05 + z * 0.05) * 0.5;
        double combinedNoise = (noise1 + noise2 + 1.0) / 2.0; // Normaliser entre 0 et 1
        
        // Répartir les couleurs de façon égale
        int colorIndex = (int) (combinedNoise * PLATFORM_COLORS.length);
        return PLATFORM_COLORS[Math.abs(colorIndex) % PLATFORM_COLORS.length];
    }


    private Material generateSimplePattern(double x, double z) {
        // Pattern en cercles concentriques
        double distance = Math.sqrt(x * x + z * z);
        int ring = (int) (distance / 3) % PLATFORM_COLORS.length; // Cercles tous les 3 blocs
        
        // Pattern en secteurs
        double angle = Math.atan2(z, x);
        int sector = (int) ((angle + Math.PI) / (Math.PI / 4)) % PLATFORM_COLORS.length; // 8 secteurs
        
        // Alterner entre cercles et secteurs
        if (((int) x + (int) z) % 2 == 0) {
            return PLATFORM_COLORS[ring];
        } else {
            return PLATFORM_COLORS[sector];
        }
    }

    private double getCombinedNoise(double x, double z) {
        double scale1 = 0.1;  // Pattern large
        double scale2 = 0.3;  // Pattern moyen  
        double scale3 = 0.8;  // Pattern fin
        
        double noise1 = simplexNoise(x * scale1, z * scale1) * 0.5;
        double noise2 = simplexNoise(x * scale2, z * scale2) * 0.3;
        double noise3 = simplexNoise(x * scale3, z * scale3) * 0.2;
        
        // Combinaison pondérée
        return noise1 + noise2 + noise3;
    }

    private double simplexNoise(double x, double z) {
        return improvedNoise(x, z);
    }

    private double improvedNoise(double x, double z) {
        int xInt = (int) Math.floor(x);
        int zInt = (int) Math.floor(z);
        
        double xFrac = x - xInt;
        double zFrac = z - zInt;
        
        // Interpolation cosinus
        double xFade = fade(xFrac);
        double zFade = fade(zFrac);
        
        // Gradients
        double n0 = grad(xInt, zInt, xFrac, zFrac);
        double n1 = grad(xInt + 1, zInt, xFrac - 1, zFrac);
        double ix0 = lerp(n0, n1, xFade);
        
        double n2 = grad(xInt, zInt + 1, xFrac, zFrac - 1);
        double n3 = grad(xInt + 1, zInt + 1, xFrac - 1, zFrac - 1);
        double ix1 = lerp(n2, n3, xFade);
        
        return lerp(ix0, ix1, zFade);
    }

    private double fade(double t) {
        // Courbe de fade 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private double grad(int x, int z, double xFrac, double zFrac) {
        // Hash simple pour les gradients
        int hash = (x * 1619 + z * 31337) & 0x7fffffff;
        hash = (hash << 13) ^ hash;
        
        double grad = 1.0 + (hash & 7); // Gradient entre 1 et 8
        return (hash & 1) == 0 ? xFrac * grad : zFrac * grad;
    }

    private Material getColorFromNoiseValue(double noiseValue) {
        // Normaliser la valeur de bruit entre 0 et 1
        double normalized = (noiseValue + 1.0) / 2.0; // De -1,1 à 0,1
        normalized = Math.max(0, Math.min(1, normalized)); // Clamp

        double ratio = 1 / 16;
        
        // Répartir les couleurs selon des seuils
        if (normalized < ratio) return Material.BROWN_WOOL; 
        else if (normalized < ratio * 2)return Material.PINK_WOOL; 
        else if (normalized < ratio * 3) return Material.BLACK_WOOL;     
        else if (normalized < ratio * 4) return Material.GRAY_WOOL;     
        else if (normalized < ratio * 5) return Material.LIGHT_GRAY_WOOL;
        else if (normalized < ratio * 6) return Material.WHITE_WOOL;    
        else if (normalized < ratio * 7) return Material.RED_WOOL;      
        else if (normalized < ratio * 8) return Material.ORANGE_WOOL;   
        else if (normalized < ratio * 9) return Material.YELLOW_WOOL;   
        else if (normalized < ratio * 10) return Material.LIME_WOOL;     
        else if (normalized < ratio * 11) return Material.GREEN_WOOL;    
        else if (normalized < ratio * 12) return Material.CYAN_WOOL;    
        else if (normalized < ratio * 13) return Material.LIGHT_BLUE_WOOL; 
        else if (normalized < ratio * 14) return Material.BLUE_WOOL;   
        else if (normalized < ratio * 15) return Material.PURPLE_WOOL;   
        else return Material.MAGENTA_WOOL;                        
    }



    private Material generateThematicPatternColor(double x, double z) {
        double distance = Math.sqrt(x * x + z * z);
        
        // Choisir un pattern aléatoire
        long seed = (long) (x * 1000 + z * 1000);
        Random localRandom = new Random(seed);
        int patternType = localRandom.nextInt(4);
        
        switch (patternType) {
            case 0: return generateRadialPattern(x, z, distance);
            case 1: return generateSpiralPattern(x, z);
            case 2: return generateStripesPattern(x, z);
            case 3: return generateCheckerboardPattern(x, z);
            default: return PLATFORM_COLORS[localRandom.nextInt(PLATFORM_COLORS.length)];
        }
    }

    private Material generateRadialPattern(double x, double z, double distance) {
        // Pattern radial concentrique
        double angle = Math.atan2(z, x);
        double radialValue = (Math.sin(distance * 0.3) + 1) / 2; // Ondulations
        
        int colorIndex = (int) ((radialValue + angle / (2 * Math.PI)) * PLATFORM_COLORS.length);
        return PLATFORM_COLORS[Math.abs(colorIndex) % PLATFORM_COLORS.length];
    }

    private Material generateSpiralPattern(double x, double z) {
        // Pattern spiralé
        double angle = Math.atan2(z, x);
        double distance = Math.sqrt(x * x + z * z);
        double spiralValue = (angle + distance * 0.5) / (2 * Math.PI);
        
        spiralValue = spiralValue - Math.floor(spiralValue); // Normaliser 0-1
        int colorIndex = (int) (spiralValue * PLATFORM_COLORS.length);
        return PLATFORM_COLORS[Math.abs(colorIndex) % PLATFORM_COLORS.length];
    }

    private Material generateStripesPattern(double x, double z) {
        // Rayures horizontales/verticales
        double stripeValue = (Math.sin(x * 0.2) + Math.cos(z * 0.2)) / 2 + 0.5;
        int colorIndex = (int) (stripeValue * PLATFORM_COLORS.length);
        return PLATFORM_COLORS[Math.abs(colorIndex) % PLATFORM_COLORS.length];
    }

    private Material generateCheckerboardPattern(double x, double z) {
        // Damier
        int xCell = (int) Math.floor(x);
        int zCell = (int) Math.floor(z);
        
        if ((xCell + zCell) % 2 == 0) {
            return PLATFORM_COLORS[random.nextInt(PLATFORM_COLORS.length / 2)]; // Première moitié
        } else {
            return PLATFORM_COLORS[PLATFORM_COLORS.length / 2 + random.nextInt(PLATFORM_COLORS.length / 2)]; // Seconde moitié
        }
    }
    
    private void teleportPlayersToPlatform(Game game) {
        Location center = game.getArenaLocation().clone();
        
        for (Player player : game.getActivePlayers()) {
            // Position aléatoire sur la plateforme
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * (ARENA_RADIUS - 3);
            
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            double y = center.getY() + 1;
            
            Location spawnLoc = new Location(center.getWorld(), x, y, z, 
                (float) (angle * 180 / Math.PI), 0);
            
            player.teleport(spawnLoc);
        }
    }
    
    private Location getPlatformCenter(Game game) {
        Location arenaLoc = game.getArenaLocation();
        if (arenaLoc == null) {
            // Fallback si aucune arène n'est définie
            World world = Bukkit.getWorlds().get(0);
            return new Location(world, 0, 100, 0);
        }
        return arenaLoc.clone().add(0, -1, 0);
    }
    
    private void updateActionBars(Game game) {
        if (currentColor == null) return;
        
        for (Player player : game.getOnlinePlayers()) {
            Component actionBar = Component.text()
                .append(Component.text("Round " + currentRound + " | ").color(TextColor.color(0xD500F9)))
                .append(Component.text("Temps: " + roundTimeLeft + "s | ").color(TextColor.color(0x808080)))
                .append(Component.text("Safe: ").color(TextColor.color(0x808080)))
                .append(getHexColorComponent(currentColor))
                .build();
            
            player.sendActionBar(actionBar);
        }
    }
    

    
    // private String getHexColor(Material material) {
    //     switch (material) {
    //         case WHITE_WOOL: return "§F§F§F§F§F§F";
    //         case ORANGE_WOOL: return "§F§F§8§C§0§0";
    //         case MAGENTA_WOOL: return "§F§F§0§0§F§F";
    //         case LIGHT_BLUE_WOOL: return "§0§0§B§F§F§F";
    //         case YELLOW_WOOL: return "§F§F§F§F§0§0";
    //         case LIME_WOOL: return "§3§2§C§D§3§2";
    //         case PINK_WOOL: return "§F§F§C§0§C§B";
    //         case GRAY_WOOL: return "§8§0§8§0§8§0";
    //         case LIGHT_GRAY_WOOL: return "§D§3§D§3§D§3";
    //         case CYAN_WOOL: return "§0§0§F§F§F§F";
    //         case PURPLE_WOOL: return "§8§0§0§0§8§0";
    //         case BLUE_WOOL: return "§0§0§0§0§F§F";
    //         case BROWN_WOOL: return "§8§B§4§5§1§3";
    //         case GREEN_WOOL: return "§0§0§8§0§0§0";
    //         case RED_WOOL: return "§F§F§0§0§0§0";
    //         case BLACK_WOOL: return "§0";
    //         default: return "§f";
    //     }
    // }


    private Component getHexColorComponent(Material material) {
        TextColor color;
        String colorName;
        
        switch (material) {
            case WHITE_WOOL: 
                color = TextColor.color(0xFFFFFF);
                colorName = "BLANC";
                break;
            case ORANGE_WOOL: 
                color = TextColor.color(0xFF8C00); // Orange plus vif
                colorName = "ORANGE";
                break;
            case MAGENTA_WOOL: 
                color = TextColor.color(0xFF00FF); // Magenta pur
                colorName = "MAGENTA";
                break;
            case LIGHT_BLUE_WOOL: 
                color = TextColor.color(0x87CEEB);
                colorName = "BLEU CLAIR";
                break;
            case YELLOW_WOOL: 
                color = TextColor.color(0xFFFF00);
                colorName = "JAUNE";
                break;
            case LIME_WOOL: 
                color = TextColor.color(0x32CD32); // Lime plus vert
                colorName = "VERT CLAIR";
                break;
            case PINK_WOOL: 
                color = TextColor.color(0xFF69B4); // Pink hot
                colorName = "ROSE";
                break;
            case GRAY_WOOL: 
                color = TextColor.color(0x696969); // Gris foncé
                colorName = "GRIS";
                break;
            case LIGHT_GRAY_WOOL: 
                color = TextColor.color(0xD3D3D3);
                colorName = "GRIS CLAIR";
                break;
            case CYAN_WOOL: 
                color = TextColor.color(0x00CED1); // Cyan plus intense
                colorName = "CYAN";
                break;
            case PURPLE_WOOL: 
                color = TextColor.color(0x9370DB); // Violet medium
                colorName = "VIOLET";
                break;
            case BLUE_WOOL: 
                color = TextColor.color(0x4169E1); // Blue royal
                colorName = "BLEU";
                break;
            case BROWN_WOOL: 
                color = TextColor.color(0x8B4513); // Brown sienna
                colorName = "MARRON";
                break;
            case GREEN_WOOL: 
                color = TextColor.color(0x228B22); // Forest green
                colorName = "VERT";
                break;
            case RED_WOOL: 
                color = TextColor.color(0xFF4500); // Red orange
                colorName = "ROUGE";
                break;
            case BLACK_WOOL: 
                color = TextColor.color(0x000000);
                colorName = "NOIR";
                break;
            default: 
                color = TextColor.color(0xFFFFFF);
                colorName = "INCONNU";
        }
        
        return Component.text(colorName).color(color).decorate(TextDecoration.BOLD);
    }
        
    private void stopAllTasks() {
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        if (roundTask != null) {
            roundTask.cancel();
            roundTask = null;
        }
    }
    
    // Getters pour les statistiques
    public int getGameTime() {
        return gameTime;
    }
    
    public int getCurrentRound() {
        return currentRound;
    }
    
    public Material getCurrentColor() {
        return currentColor;
    }
    
    public int getRoundTimeLeft() {
        return roundTimeLeft;
    }
}