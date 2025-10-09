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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BlockPartyGame implements GameType {
    
    private BukkitTask gameTask = null;
    private BukkitTask roundTask = null;
    private int gameTime = 0;
    private int currentRound = 0;
    private final Random random = new Random();
    
    // Configuration du BlockParty
    private final int BASE_ROUND_TIME = 7;
    private final int MIN_ROUND_TIME = 3;
    private final int REGENERATION_TIME = 3;
    private final int ARENA_SIZE = 40; // Taille du carré
    private final int CHUNK_SIZE = 4; // Taille des
    
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
        
        // Générer la plateforme initiale
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
        
        // Restaurer la plateforme
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
    public void onBlockPlace(Game game, BlockPlaceEvent event) {
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
        return 120;
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
        
        // Annoncer le nouveau round
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
        
        for (Player player : game.getOnlinePlayers()) {
            player.sendMessage(roundMessage);
            player.sendMessage(safeColorMessage);
            player.sendMessage(timeMessage);
        }
        
        // Effets visuels et sonores
        for (Player player : game.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
            
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
    
    // Suppression des blocs
    private void removeUnsafeBlocks(Game game) {
        game.broadcastMessage("§cDISPARITION DES BLOCS !");
        
        int blocksRemoved = 0;
        
        // Supprimer tous les blocs non-safe 
        for (Location blockLoc : platformBlocks) {
            if (!safeBlocks.contains(blockLoc)) {
                Block block = blockLoc.getBlock();
                if (block.getType() != Material.AIR && block.getType() != currentColor) {
                    // Supprimer le bloc
                    block.setType(Material.AIR);
                    blocksRemoved++;
                }
            }
        }
        
        // Son
        for (Player player : game.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        }
        
        game.broadcastMessage("§c" + blocksRemoved + " blocs ont disparu !");
    }
    
    private void regeneratePlatformAndNextRound(Game game) {
        // Régénérer
        regeneratePlatform();
        
        // Démarrer le prochain round après un court délai
        Bukkit.getScheduler().runTaskLater(
            game.getGameManager().getPlugin(),
            () -> startNewRound(game),
            60L // 3 secondes de pause
        );
    }
    
    private void regeneratePlatform() {
        // Régénérer tous les blocs en une fois
        for (Location blockLoc : platformBlocks) {
            Block block = blockLoc.getBlock();
            Material color = generatePatternColor(blockLoc);
            block.setType(color);
        }
    }
    
    
    
    private void eliminatePlayer(Game game, Player player, String reason) {
        game.eliminatePlayer(player, reason, true);
        
        // Effets d'élimination
        player.sendTitle("§c💀 ÉLIMINÉ", "§7Round " + currentRound, 10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_DEATH, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.LAVA, player.getLocation(), 15); // Réduit les particules
    }
    
    private void restorePlatform(Game game) {  
        generatePlatform(game);
    }

    // NGénérer la platforme
    private void generatePlatform(Game game) {
        Location center = getPlatformCenter(game);
        World world = center.getWorld();
        
        platformBlocks.clear();
        safeBlocks.clear();
        
        int halfSize = ARENA_SIZE / 2;
        
        // Générer un plateau carré avec patterns
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                Location blockLoc = center.clone().add(x, 0, z);
                
                // Générer une couleur basée sur le pattern
                Material color = generatePatternColor(blockLoc);
                world.getBlockAt(blockLoc).setType(color);
                
                platformBlocks.add(blockLoc);
            }
        }
        
        // Ajouter des bordures pour délimiter la zone
        createPlatformBorders(center, world, halfSize);
    }
    
    private void createPlatformBorders(Location center, World world, int halfSize) {
        Material borderMaterial = Material.SMOOTH_QUARTZ;
        
        // Bordures extérieures
        for (int x = -halfSize - 1; x <= halfSize + 1; x++) {
            for (int z = -halfSize - 1; z <= halfSize + 1; z++) {
                // Ne placer que sur les bords
                if (Math.abs(x) == halfSize + 1 || Math.abs(z) == halfSize + 1) {
                    Location borderLoc = center.clone().add(x, -1, z);
                    world.getBlockAt(borderLoc).setType(borderMaterial);
                    
                    // Ajouter une deuxième couche pour plus de visibilité
                    Location borderLocTop = center.clone().add(x, 0, z);
                    world.getBlockAt(borderLocTop).setType(borderMaterial);
                }
            }
        }
    }

    // Génération de patterns avec variation par round
    private Material generatePatternColor(Location location) {
        double x = location.getX();
        double z = location.getZ();
        
        // Pattern en zones de taille variable selon le round
        int zoneSize = 2 + (currentRound % 4); // Taille de zone entre 2 et 5 blocs
        int zoneX = (int) Math.floor(x / zoneSize);
        int zoneZ = (int) Math.floor(z / zoneSize);
        
        // Hash déterministe basé sur la zone et le round
        int hash = (zoneX * 73856093) ^ (zoneZ * 19349663) ^ (currentRound * 83492791);
        hash = Math.abs(hash) % PLATFORM_COLORS.length;
        
        return PLATFORM_COLORS[hash];
    }
    
    // Téléportation aléatoire sur la platforme
    private void teleportPlayersToPlatform(Game game) {
        Location center = game.getArenaLocation().clone();
        int halfSize = ARENA_SIZE / 2 - 2; // -2 pour éviter les bords
        
        for (Player player : game.getActivePlayers()) {
            // Position aléatoire sur la plateforme carrée
            int x = random.nextInt(halfSize * 2) - halfSize;
            int z = random.nextInt(halfSize * 2) - halfSize;
            
            double y = center.getY() + 1;
            
            Location spawnLoc = new Location(center.getWorld(), 
                center.getX() + x, y, center.getZ() + z,
                random.nextFloat() * 360 - 180, 0);
            
            player.teleport(spawnLoc);
        }
    }
    
    private Location getPlatformCenter(Game game) {
        Location arenaLoc = game.getArenaLocation();
        if (arenaLoc == null) {
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
    
    private Component getHexColorComponent(Material material) {
        TextColor color;
        String colorName;
        
        switch (material) {
            case WHITE_WOOL: 
                color = TextColor.color(0xE9ECEC);
                colorName = "BLANC";
                break;
            case ORANGE_WOOL: 
                color = TextColor.color(0xF07613);
                colorName = "ORANGE";
                break;
            case MAGENTA_WOOL: 
                color = TextColor.color(0xBD44B3);
                colorName = "MAGENTA";
                break;
            case LIGHT_BLUE_WOOL: 
                color = TextColor.color(0x3AAFD9);
                colorName = "BLEU CLAIR";
                break;
            case YELLOW_WOOL: 
                color = TextColor.color(0xF8C527);
                colorName = "JAUNE";
                break;
            case LIME_WOOL: 
                color = TextColor.color(0x70B919);
                colorName = "VERT CLAIR";
                break;
            case PINK_WOOL: 
                color = TextColor.color(0xED8DAC);
                colorName = "ROSE";
                break;
            case GRAY_WOOL: 
                color = TextColor.color(0x3E4447);
                colorName = "GRIS FONCÉ";
                break;
            case LIGHT_GRAY_WOOL: 
                color = TextColor.color(0x8E8E86);
                colorName = "GRIS CLAIR";
                break;
            case CYAN_WOOL: 
                color = TextColor.color(0x158991);
                colorName = "CYAN";
                break;
            case PURPLE_WOOL: 
                color = TextColor.color(0x792AAC);
                colorName = "VIOLET";
                break;
            case BLUE_WOOL: 
                color = TextColor.color(0x35399D);
                colorName = "BLEU";
                break;
            case BROWN_WOOL: 
                color = TextColor.color(0x724728);
                colorName = "MARRON";
                break;
            case GREEN_WOOL: 
                color = TextColor.color(0x546D1B);
                colorName = "VERT";
                break;
            case RED_WOOL: 
                color = TextColor.color(0xA02722);
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