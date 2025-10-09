package fr.celestia.events.game.types;

import fr.celestia.events.game.Game;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class BuildBattleGame implements GameType {
    
    private final List<String> themes = Arrays.asList(
    "Médiéval", "Hiver", "Printemps", "Été", "Automne", "Pomme", "Arbre", "Maison", "Fleur", "Animal",
    "Nourriture", "Véhicule", "Sport", "Musique", "Cœur", "Étoile", "Lune", "Soleil", "Montagne", "Mer",
    "Rivière", "Forêt", "Village", "Château", "Pont", "Temple", "Robot", "Monstre", "Arc-en-ciel", "Nuage",
    "Oiseau", "Poisson", "Chat", "Chien", "Dragon", "Licorne", "Fée", "Sorcier", "Super-héros", "Vaisseau spatial",
    "Fusée", "Avion", "Voiture", "Bateau", "Train", "Vélo", "Ballon", "Ballon de football", "Panier de basket", "Raquette",
    "Gâteau", "Glace", "Pizza", "Hamburger", "Frites", "Bonbon", "Chocolat", "Fruit", "Légume", "Fromage",
    "École", "Bibliothèque", "Hôpital", "Magasin", "Restaurant", "Cinéma", "Parc", "Jardin", "Plage", "Desert",
    "Neige", "Pluie", "Vent", "Tempête", "Éclair", "Feu", "Eau", "Terre", "Air", "Glace",
    "Volcan", "Île", "Cascade", "Grotte", "Caverne", "Pyramide", "Statue", "Tour", "Gratte-ciel", "Ferme",
    "Usine", "Laboratoire", "Musée", "Château fort", "Palais", "Manoir", "Cabane", "Igloo", "Tente", "Yourte",
    "Royaume", "Empire", "Planète", "Galaxie", "Univers", "Cosmos", "Astéroïde", "Comète", "Satellite", "OVNI",
    "Dinosaure", "Mammouth", "Tigre", "Lion", "Éléphant", "Girafe", "Panda", "Koala", "Pingouin", "Dauphin",
    "Papillon", "Coccinelle", "Abeille", "Fourmi", "Araignée", "Serpent", "Tortue", "Crocodile", "Hibou", "Aigle",
    "Rêve", "Cauchemar", "Aventure", "Mignon", "Magie", "Reine", "Victoire", "Défaite", "Amour", "Amitié",
    "Famille", "Noël", "Halloween", "Pâques", "Anniversaire", "Fête", "Célébration", "Vacances", "Voyage", "Exploration"
);
    
    private String currentTheme;
    private final Map<Player, Location> playerPlots = new HashMap<>();
    private final Map<Player, Integer> plotScores = new HashMap<>();
    private final Map<Player, Map<Player, Integer>> votes = new HashMap<>();
    private BossBar bossBar;
    private BukkitTask buildTimerTask;
    private BukkitTask voteTimerTask;
    private int timeLeft;
    private boolean votingPhase = false;
    private List<Player> voteOrder;
    private int currentVoteIndex = 0;
    
    // Configuration des plots
    private final int PLOT_SIZE = 27;
    private final int PLOT_SPACING = 34;
    private final int BUILD_TIME = 7 * 60; // 7 minutes en secondes
    private final int VOTE_TIME = 15; // 15 secondes par plot
    
    @Override
    public void onStart(Game game) {
        game.broadcastMessage("§6§lBuild Battle §ecommence !");
        
        // Choisir un thème aléatoire
        currentTheme = themes.get(new Random().nextInt(themes.size()));
        

        // On le fait avant les 5 secondes du début sinon c'est bizarre
        // // Téléporter les joueurs
        // teleportPlayersToPlots(game);
        
        // Afficher le thème
        displayTheme(game);
        
        // Initialiser la bossbar
        initializeBossBar(game);
        
        // Démarrer le timer de construction
        startBuildTimer(game);
    }
    
    @Override
    public void onEnd(Game game) {
        // Arrêter les tâches
        if (buildTimerTask != null) {
            buildTimerTask.cancel();
            buildTimerTask = null;
        }
        if (voteTimerTask != null) {
            voteTimerTask.cancel();
            voteTimerTask = null;
        }
        
        // Supprimer la bossbar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        
        // Nettoyer les données
        playerPlots.clear();
        plotScores.clear();
        votes.clear();
        votingPhase = false;
        currentVoteIndex = 0;
    }
    
    @Override
    public void onPlayerJoin(Game game, Player player) {
        player.sendMessage("§b§l=== BUILD BATTLE ===");
        player.sendMessage("§e• Vous avez 7 minutes pour construire !");
        player.sendMessage("§e• Respectez le thème imposé");
        player.sendMessage("§e• Phase de vote à la fin pour élire le meilleur build");
        player.sendMessage("§aBonne construction !");
        
        if (game.getState() == fr.celestia.events.game.GameState.RUNNING && !votingPhase) {
            // Assigner un plot au joueur qui rejoint
            assignPlot(game, player);
            teleportToPlot(player);
            preparePlayerForBuild(player);
        }
    }
    
    @Override
    public void onPlayerLeave(Game game, Player player) {
        // Nettoyer les données du joueur
        playerPlots.remove(player);
        plotScores.remove(player);
        votes.remove(player);
        
        // Retirer de la bossbar
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }
    
    @Override
    public void onPlayerMove(Game game, PlayerMoveEvent event) {
        if (!votingPhase) {
            // Pendant la phase de construction, vérifier les limites du plot
            checkPlotBoundaries(game, event);
        } else {
            // Pendant la phase de vote, vérifier les limites du plot visité
            checkVotePlotBoundaries(game, event);
        }
    }
    
    @Override
    public void onPlayerDamage(Game game, EntityDamageEvent event) {
        // Annuler tous les dégâts
        event.setCancelled(true);
    }
    
    @Override
    public void onBlockBreak(Game game, BlockBreakEvent event) {
        if (votingPhase) {
            event.setCancelled(true);
            return;
        }
        
        // Vérifier si le bloc cassé est dans le plot du joueur
        if (!isInPlayerPlot(event.getPlayer(), event.getBlock().getLocation(), 1)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cVous ne pouvez pas casser de blocs en dehors de votre plot !");
        }
    }
    
    @Override
    public void onBlockPlace(Game game, BlockPlaceEvent event) {
        if (votingPhase) {
            event.setCancelled(true);
            return;
        }
        
        // Vérifier si le bloc placé est dans le plot du joueur
        if (!isInPlayerPlot(event.getPlayer(), event.getBlock().getLocation(), 1)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cVous ne pouvez pas placer de blocs en dehors de votre plot !");
        }
    }
    
    @Override
    public boolean checkWinConditions(Game game) {
        // Pour BuildBattle, on vérifie si la phase de vote est terminée
        return votingPhase && currentVoteIndex >= voteOrder.size();
    }
    
    @Override
    public void preparePlayer(Game game, Player player) {
        // Utiliser la préparation par défaut
        GameType.super.preparePlayer(game, player);
        
        // Mode créatif pour construire
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        
        // Clear l'inventaire
        player.getInventory().clear();

        // Initialiser les plots pour chaque joueur
        initializePlots(game);
        
        // Téléporter directement dans le plot au lieu de l'arène
        if (game.getState() == fr.celestia.events.game.GameState.STARTING) {
            Bukkit.getScheduler().runTaskLater(game.getGameManager().getPlugin(), () -> {
                if (playerPlots.containsKey(player)) {
                    teleportToPlot(player);
                    preparePlayerForBuild(player);
                }
            }, 5L); // Petit délai pour être sûr que tout est initialisé
        }
    }
        
    @Override
    public void resetPlayer(Game game, Player player) {
        // Utiliser le reset par défaut
        GameType.super.resetPlayer(game, player);
        
        // Retour en survival/adventure
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        
        // Clear l'inventaire
        player.getInventory().clear();
    }
    
    @Override
    public String getDisplayName() {
        return "Build Battle";
    }
    
    @Override
    public String getDescription() {
        return "Construisez la meilleure création selon le thème et votez pour élire le gagnant !";
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public int getWaitingTime() {
        return 120;
    }
    
    private void initializePlots(Game game) {
        Location arenaLoc = game.getArenaLocation();
        int index = 0;
        
        // Régénérer tous les plots
        for (int i = 0; i < game.getActivePlayers().size(); i++) {
            clearPlot(arenaLoc.clone().add(i * PLOT_SPACING, 0, 0));
        }
        
        for (Player player : game.getActivePlayers()) {
            assignPlot(game, player, index);
            index++;
        }
    }


    // TODO - Optimisation du clear 
    private void clearPlot(Location plotBase) {
        World world = plotBase.getWorld();
        
        for (int x = 0; x < PLOT_SIZE; x++) {
            for (int z = 0; z < PLOT_SIZE; z++) {
                for (int y = 0; y < PLOT_SIZE; y++) {
                    Location blockLoc = plotBase.clone().add(x, y, z);
                    
                    // Sol
                    if (y == 0) {
                        blockLoc.getBlock().setType(Material.LIGHT_GRAY_TERRACOTTA);
                    } else { // Sinon on clear
                        blockLoc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
    }
    
    private void assignPlot(Game game, Player player) {
        // Trouver le premier plot disponible
        int index = playerPlots.size();
        assignPlot(game, player, index);
    }
    
    private void assignPlot(Game game, Player player, int index) {
        Location arenaLoc = game.getArenaLocation();
        int xOffset = index * PLOT_SPACING;
        Location plotBase = arenaLoc.clone().add(xOffset, 0, 0);
        playerPlots.put(player, plotBase);
        plotScores.put(player, 0);
        votes.put(player, new HashMap<>());
    }
    
    private void teleportPlayersToPlots(Game game) {
        for (Player player : game.getActivePlayers()) {
            teleportToPlot(player);
            preparePlayerForBuild(player);
        }
    }
    
    private void teleportToPlot(Player player) {
        Location plotBase = playerPlots.get(player);
        if (plotBase != null) {
            Location center = plotBase.clone().add(
                PLOT_SIZE / 2.0, 
                2, 
                PLOT_SIZE / 2.0
            );
            center.setPitch(0);
            center.setYaw(180);
            player.teleport(center);
        }
    }
    
    private void preparePlayerForBuild(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        
        // Donner quelques blocs de base
        ItemStack[] buildingBlocks = {
            new ItemStack(Material.WHITE_WOOL, 64),
            new ItemStack(Material.OAK_PLANKS, 64),
            new ItemStack(Material.STONE, 64),
            new ItemStack(Material.GLASS, 64)
        };
        
        player.getInventory().addItem(buildingBlocks);
    }
    
    private void displayTheme(Game game) {
        for (Player player : game.getActivePlayers()) {
            player.sendTitle("§6§lThème", "§e" + currentTheme, 10, 60, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        game.broadcastMessage("§6§lThème du Build Battle: §e" + currentTheme);
    }
    
    private void initializeBossBar(Game game) {
        bossBar = Bukkit.createBossBar(
            "§6Build Battle - " + currentTheme + " | Temps: 7:00",
            BarColor.GREEN,
            BarStyle.SEGMENTED_20
        );
        
        for (Player player : game.getActivePlayers()) {
            bossBar.addPlayer(player);
        }
        
        bossBar.setVisible(true);
    }
    
    private void startBuildTimer(Game game) {
        timeLeft = BUILD_TIME;
        
        buildTimerTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (timeLeft <= 0) {
                    endBuildPhase(game);
                    return;
                }
                
                // Mettre à jour la bossbar
                updateBossBar();
                
                timeLeft--;
                
                // Annonces de temps
                if (timeLeft == 300) { // 5 minutes
                    game.broadcastMessage("§eIl reste 5 minutes !");
                } else if (timeLeft == 180) { // 3 minutes
                    game.broadcastMessage("§eIl reste 3 minutes !");
                } else if (timeLeft == 60) { // 1 minute
                    game.broadcastMessage("§cIl reste 1 minute !");
                } else if (timeLeft == 30) { // 30 secondes
                    game.broadcastMessage("§cIl reste 30 secondes !");
                } else if (timeLeft <= 10 && timeLeft > 0) { // Compte à rebours
                    game.broadcastMessage("§c" + timeLeft + "...");
                    for (Player player : game.getActivePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
                
            }, 0L, 20L);
    }
    
    private void updateBossBar() {
        if (bossBar == null) return;
        
        double progress = (double) timeLeft / BUILD_TIME;
        bossBar.setProgress(progress);
        
        int minutes = timeLeft / 60;
        int seconds = timeLeft % 60;
        String timeString = String.format("%d:%02d", minutes, seconds);
        
        if (votingPhase) {
            bossBar.setTitle("§6Phase de Vote | " + timeString);
            bossBar.setColor(BarColor.PURPLE);
        } else {
            bossBar.setTitle("§6" + currentTheme + " | Temps: " + timeString);
            bossBar.setColor(timeLeft <= 60 ? BarColor.RED : timeLeft <= 180 ? BarColor.YELLOW : BarColor.GREEN);
        }
    }
    
    private void endBuildPhase(Game game) {
        votingPhase = true;
        buildTimerTask.cancel();
        
        game.broadcastMessage("§6§lPhase de construction terminée !");
        game.broadcastMessage("§ePhase de vote commence...");
        
        // Préparer la phase de vote
        prepareVotePhase(game);
        startVotePhase(game);
    }
    
    private void prepareVotePhase(Game game) {
        voteOrder = new ArrayList<>(game.getActivePlayers());
        Collections.shuffle(voteOrder);
        currentVoteIndex = 0;
        
        // Donner l'équipement de vote à tous les joueurs
        for (Player player : game.getActivePlayers()) {
            preparePlayerForVote(player);
        }
    }
    
    private void preparePlayerForVote(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        
        // Désactiver les dégâts de chute et autres
        player.setFallDistance(0);
        
        // Items de vote avec différentes couleurs
        ItemStack[] voteItems = {
            createVoteItem(Material.BROWN_WOOL, "§8Nul"),
            createVoteItem(Material.RED_WOOL, "§cPas fou"),
            createVoteItem(Material.LIME_WOOL, "§aOk"),
            createVoteItem(Material.GREEN_WOOL, "§2Pas mal"),
            createVoteItem(Material.PURPLE_WOOL, "§5Épique"),
            createVoteItem(Material.YELLOW_WOOL, "§eFabuleux")
        };
        
        player.getInventory().setContents(voteItems);
    }

    private void teleportToVoteLocation(Game game, Player player) {
        if (currentVoteIndex >= voteOrder.size()) return;
        
        Player plotOwner = voteOrder.get(currentVoteIndex);
        Location plotBase = playerPlots.get(plotOwner);
        
        // Position safe au-dessus du plot (centre + hauteur)
        Location voteLocation = plotBase.clone().add(
            PLOT_SIZE / 2.0, 
            15,  // Hauteur suffisante pour être au-dessus des constructions
            PLOT_SIZE / 2.0
        );
        
        // S'assurer que l'emplacement est safe (pas de blocs)
        voteLocation = findSafeLocation(voteLocation);
        
        player.teleport(voteLocation);
        player.setFlying(true);
    }

    private Location findSafeLocation(Location location) {
        World world = location.getWorld();
        
        // Vérifier s'il y a des blocs à la position actuelle
        if (location.getBlock().getType() != Material.AIR) {
            // Monter jusqu'à trouver un endroit sans bloc
            for (int y = (int) location.getY(); y < location.getWorld().getMaxHeight(); y++) {
                Location testLoc = location.clone();
                testLoc.setY(y);
                if (testLoc.getBlock().getType() == Material.AIR) {
                    return testLoc.add(0, 1, 0); // Un bloc au-dessus pour être safe
                }
            }
        }
        
        return location;
    }
    
    private ItemStack createVoteItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(
            "§7Cliquez pour attribuer"
        ));
        item.setItemMeta(meta);
        return item;
    }
    
    private void startVotePhase(Game game) {
        if (voteOrder.isEmpty()) {
            endVotePhase(game);
            return;
        }
        
        timeLeft = VOTE_TIME;
        teleportToNextPlot(game);
        
        voteTimerTask = game.getGameManager().getPlugin().getServer().getScheduler().runTaskTimer(
            game.getGameManager().getPlugin(), () -> {
                if (timeLeft <= 0) {
                    endCurrentVote(game);
                    return;
                }
                
                updateBossBar();
                
                if (timeLeft <= 5) {
                    for (Player player : game.getActivePlayers()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                    }
                }
                
                timeLeft--;
                
            }, 0L, 20L);
    }
    
    private void teleportToNextPlot(Game game) {
        if (currentVoteIndex >= voteOrder.size()) {
            endVotePhase(game);
            return;
        }
        
        Player plotOwner = voteOrder.get(currentVoteIndex);
        
        for (Player voter : game.getActivePlayers()) {
            teleportToVoteLocation(game, voter);
            voter.sendMessage("§6Votez pour la construction de §e" + plotOwner.getName());
            voter.sendMessage("§7Vous avez 15 secondes pour voter !");
            voter.playSound(voter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            // S'assurer que le mode vol est activé
            voter.setFlying(true);
        }
        
        timeLeft = VOTE_TIME;
    }
        
    private void endCurrentVote(Game game) {
        // Appliquer les votes manquants (note 3/6)
        Player plotOwner = voteOrder.get(currentVoteIndex);
        
        for (Player voter : game.getActivePlayers()) {
            if (!votes.get(plotOwner).containsKey(voter)) {
                // Joueur n'a pas voté - pénalité
                votes.get(plotOwner).put(voter, 3);
                voter.sendMessage("§cVous n'avez pas voté ! Note par défaut: 3/6 + pénalité");
                
                // Pénalité d'un point sur sa propre note
                if (votes.containsKey(voter)) {
                    // On réduira la note plus tard lors du calcul
                }
            }
        }
        
        currentVoteIndex++;
        
        if (currentVoteIndex < voteOrder.size()) {
            teleportToNextPlot(game);
            timeLeft = VOTE_TIME;
        } else {
            endVotePhase(game);
        }
    }
    
    private void endVotePhase(Game game) {
        voteTimerTask.cancel();
        calculateScores(game);
        game.endGame(announceResults(game));
    }
    
    private void calculateScores(Game game) {
        // Calculer les scores bruts
        for (Player plotOwner : plotScores.keySet()) {
            int totalScore = 0;
            Map<Player, Integer> plotVotes = votes.get(plotOwner);
            
            for (int vote : plotVotes.values()) {
                totalScore += vote;
            }
            
            plotScores.put(plotOwner, totalScore);
        }
        
        // Appliquer les pénalités
        for (Player voter : game.getActivePlayers()) {
            if (!hasVotedForAll(voter)) {
                // Réduire la note du joueur d'un point
                int currentScore = plotScores.getOrDefault(voter, 0);
                plotScores.put(voter, Math.max(0, currentScore - 2));
            }
        }
    }
    
    private boolean hasVotedForAll(Player voter) {
        for (Player plotOwner : votes.keySet()) {
            if (!votes.get(plotOwner).containsKey(voter)) {
                return false;
            }
        }
        return true;
    }
    
    // Renvoie le gagnant et annonce les résultats
    private List<Player> announceResults(Game game) {
        game.broadcastMessage("§6§l=== RÉSULTATS DU BUILD BATTLE ===");

        List<Player> winners = new ArrayList<Player>();
        
        // Trier par score
        List<Map.Entry<Player, Integer>> sortedScores = new ArrayList<>(plotScores.entrySet());
        sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (int i = 0; i < sortedScores.size(); i++) {
            Map.Entry<Player, Integer> entry = sortedScores.get(i);
            String position = getPositionColor(i + 1);
            game.broadcastMessage(position + (i + 1) + ". §e" + entry.getKey().getName() + 
                " §7- §6" + entry.getValue() + " points");
        }
        
        // Annoncer le gagnant
        if (!sortedScores.isEmpty()) {
            Player winner = sortedScores.get(0).getKey();
            game.broadcastMessage("");
            game.broadcastMessage("§6§lFÉLICITATIONS À §e§l" + winner.getName() + " §6§l!");
            game.broadcastMessage("§7Thème: §e" + currentTheme);
            
            for (Player player : game.getActivePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                player.sendTitle(
                    "§6§l" + winner.getName(), 
                    "§ea gagné le Build Battle !", 
                    10, 60, 20
                );
            }
            winners.add(winner);
        }

        return winners;
    }
    
    private String getPositionColor(int position) {
        switch (position) {
            case 1: return "§6§l";
            case 2: return "§7§l";
            case 3: return "§c§l";
            default: return "§f";
        }
    }

    private boolean isInPlayerPlot(Player player, Location location, int decrementer) {
        Location plotBase = playerPlots.get(player);
        if (plotBase == null) return false;
        
        double minX = plotBase.getX();
        double maxX = plotBase.getX() + PLOT_SIZE - decrementer;
        double minY = plotBase.getY();
        double maxY = plotBase.getY() + PLOT_SIZE;
        double minZ = plotBase.getZ();
        double maxZ = plotBase.getZ() + PLOT_SIZE - decrementer;
        
        return location.getX() >= minX && location.getX() <= maxX &&
               location.getY() >= minY && location.getY() <= maxY &&
               location.getZ() >= minZ && location.getZ() <= maxZ;
    }
    
    private void checkPlotBoundaries(Game game, PlayerMoveEvent event) {
        if (votingPhase) return;
        
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        if (!isInPlayerPlot(player, to, 0)) {
            // Calcul plus doux du retour dans le plot
            Location plotBase = playerPlots.get(player);
            Location plotCenter = plotBase.clone().add(PLOT_SIZE / 2.0, 2, PLOT_SIZE / 2.0);
            
            // Trouver la direction vers le centre
            Vector toCenter = plotCenter.toVector().subtract(to.toVector());
            
            // Déterminer de quel côté le joueur sort
            double deltaX = 0, deltaY = 0, deltaZ = 0;
            
            if (to.getX() < plotBase.getX()) {
                deltaX = plotBase.getX() - to.getX() + 0.1;
            } else if (to.getX() > plotBase.getX() + PLOT_SIZE) {
                deltaX = (plotBase.getX() + PLOT_SIZE) - to.getX() - 0.1;
            }
            
            if (to.getY() < plotBase.getY()) {
                deltaY = plotBase.getY() - to.getY() + 0.1;
            } else if (to.getY() > plotBase.getY() + PLOT_SIZE) {
                deltaY = (plotBase.getY() + PLOT_SIZE) - to.getY() - 0.1;
            }
            
            if (to.getZ() < plotBase.getZ()) {
                deltaZ = plotBase.getZ() - to.getZ() + 0.1;
            } else if (to.getZ() > plotBase.getZ() + PLOT_SIZE) {
                deltaZ = (plotBase.getZ() + PLOT_SIZE) - to.getZ() - 0.1;
            }
            
            // Appliquer le déplacement minimal pour revenir dans le plot
            Location safeLocation = to.clone().add(deltaX, deltaY, deltaZ);
            safeLocation.setYaw(to.getYaw());
            safeLocation.setPitch(to.getPitch());
            
            event.setTo(safeLocation);
            
            if (event.getFrom().distanceSquared(event.getTo()) > 1) {
                player.sendMessage("§cVous ne pouvez pas sortir de votre plot !");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
            }
        }
    }
    
    private void checkVotePlotBoundaries(Game game, PlayerMoveEvent event) {
        if (currentVoteIndex >= voteOrder.size()) return;
        
        Player player = event.getPlayer();
        Player plotOwner = voteOrder.get(currentVoteIndex);
        Location plotBase = playerPlots.get(plotOwner);
        Location to = event.getTo();
        
        if (!isInPlayerPlot(plotOwner, to, 0)) {
            // Calcul plus doux du retour dans le plot de vote
            Location plotCenter = plotBase.clone().add(PLOT_SIZE / 2.0, 10, PLOT_SIZE / 2.0);
            
            // Trouver la direction vers le centre
            Vector toCenter = plotCenter.toVector().subtract(to.toVector());
            
            // Déterminer de quel côté le joueur sort
            double deltaX = 0, deltaY = 0, deltaZ = 0;
            
            if (to.getX() < plotBase.getX()) {
                deltaX = plotBase.getX() - to.getX() + 0.1;
            } else if (to.getX() > plotBase.getX() + PLOT_SIZE) {
                deltaX = (plotBase.getX() + PLOT_SIZE) - to.getX() - 0.1;
            }
            
            if (to.getY() < plotBase.getY() + 5) { // Marge verticale basse pendant le vote
                deltaY = (plotBase.getY() + 5) - to.getY() + 0.1;
            } else if (to.getY() > plotBase.getY() + PLOT_SIZE) {
                deltaY = (plotBase.getY() + PLOT_SIZE) - to.getY() - 0.1;
            }
            
            if (to.getZ() < plotBase.getZ()) {
                deltaZ = plotBase.getZ() - to.getZ() + 0.1;
            } else if (to.getZ() > plotBase.getZ() + PLOT_SIZE) {
                deltaZ = (plotBase.getZ() + PLOT_SIZE) - to.getZ() - 0.1;
            }
            
            // Appliquer le déplacement minimal pour revenir dans le plot
            Location safeLocation = to.clone().add(deltaX, deltaY, deltaZ);
            safeLocation.setYaw(to.getYaw());
            safeLocation.setPitch(to.getPitch());
            
            event.setTo(safeLocation);
            
            if (event.getFrom().distanceSquared(event.getTo()) > 1) {
                player.sendMessage("§cRestez dans la zone de vote !");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 1.0f);
            }
        }
    }
    
    // Méthode pour gérer les votes (à appeler depuis un listener d'interaction)
    public void handleVote(Player voter, ItemStack item) {
        if (!votingPhase || currentVoteIndex >= voteOrder.size()) return;
        
        Player plotOwner = voteOrder.get(currentVoteIndex);
        Material material = item.getType();

        // Empêcher de voter pour soi-même
        if (voter.equals(plotOwner)) {
            voter.sendMessage("§cVous ne pouvez pas voter pour votre propre construction !");
            voter.playSound(voter.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        int score = getScoreFromMaterial(material);
        String name = getNameFromMaterial(material);
        if (score != -1) {
            votes.get(plotOwner).put(voter, score);
            voter.sendMessage("§aVous avez attribué la note §6" + name + " §aà " + plotOwner.getName());
            voter.playSound(voter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
        }
    }

    public boolean isTimeToVote(){
        return votingPhase;
    }
    
    private int getScoreFromMaterial(Material material) {
        switch (material) {
            case BROWN_WOOL: return 1;
            case RED_WOOL: return 2;
            case LIME_WOOL: return 4;
            case GREEN_WOOL: return 7;
            case PURPLE_WOOL: return 10;
            case YELLOW_WOOL: return 14;
            default: return -1;
        }
    }

    private String getNameFromMaterial(Material material) {
        switch (material) {
            case BROWN_WOOL: return "§8Nul";
            case RED_WOOL: return "§cPas fou";
            case LIME_WOOL: return "§aOk";
            case GREEN_WOOL: return "§2Pas mal";
            case PURPLE_WOOL: return "§5Épique";
            case YELLOW_WOOL: return "§eFabuleux";
            default: return "";
        }
    }



}