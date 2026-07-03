package app.danakube.danaranks.quota;

import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.profile.PlayerProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RushManagerTest {

    private DatabaseManager dbManager;
    private YamlConfiguration config;
    private RushManager rushManager;
    private Map<UUID, PlayerProfile> profileCache;

    @BeforeEach
    public void setUp() {
        // Use a real in-memory SQLite database
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        profileCache = new HashMap<>();

        config = new YamlConfiguration();
        config.set("rush.daily-setup-hour", 8);
        config.set("rush.start-window.min-hour", 12);
        config.set("rush.start-window.max-hour", 22);
        config.set("rush.duration-range.min-minutes", 20);
        config.set("rush.duration-range.max-minutes", 60);
        config.set("rush.pre-announce-minutes", 30);
        config.set("rush.eligible-resources", Arrays.asList(
                "lumens-gained", "lumens-spent", "job-xp", "tool-xp", "vanilla-xp-gained", "vanilla-xp-spent"
        ));
        
        config.set("rush.rank-settings.fer.elo-factor", 50.0);
        config.set("rush.rank-settings.fer.gain-multiplier", 1.5);
        config.set("rush.rank-settings.fer.loss-multiplier", 0.5);

        config.set("rush.rank-settings.bronze.elo-factor", 40.0);
        config.set("rush.rank-settings.bronze.gain-multiplier", 1.25);
        config.set("rush.rank-settings.bronze.loss-multiplier", 0.75);

        config.set("rush.rank-settings.argent.elo-factor", 30.0);
        config.set("rush.rank-settings.argent.gain-multiplier", 1.0);
        config.set("rush.rank-settings.argent.loss-multiplier", 1.0);

        config.set("rush.rank-settings.or.elo-factor", 20.0);
        config.set("rush.rank-settings.or.gain-multiplier", 0.75);
        config.set("rush.rank-settings.or.loss-multiplier", 1.25);

        config.set("rush.rank-settings.platine.elo-factor", 15.0);
        config.set("rush.rank-settings.platine.gain-multiplier", 0.5);
        config.set("rush.rank-settings.platine.loss-multiplier", 1.5);

        rushManager = new RushManager(null);
        rushManager.loadConfig(config);
        rushManager.setDatabaseManagerOverride(dbManager);
        rushManager.setProfileCacheOverride(profileCache);
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    public void testDailySetupPlanning() {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);

        assertTrue(rushManager.isDailyPlanned());
        assertNotNull(rushManager.getDailyResource());
        assertTrue(config.getStringList("rush.eligible-resources").contains(rushManager.getDailyResource()));

        LocalDateTime start = rushManager.getStartTime();
        assertEquals(time0800.toLocalDate(), start.toLocalDate());
        assertTrue(start.getHour() >= 12 && start.getHour() <= 22);

        int duration = rushManager.getDurationMinutes();
        assertTrue(duration >= 20 && duration <= 60);
        
        // Les inscriptions sont bien ouvertes
        Instant nowInstant = time0800.atZone(ZoneId.systemDefault()).toInstant();
        assertTrue(rushManager.isRegistrationOpen(nowInstant));
        assertEquals(0, rushManager.getRegisteredPlayersCount());
    }

    @Test
    public void testLateRegistrationScore() {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        
        // Joueur s'inscrit en retard (15 minutes après le début)
        Instant lateTime = startInstant.plusSeconds(15 * 60);
        UUID p1 = UUID.randomUUID();
        
        // Progression effectuée avant le rush ou avant son inscription : ne doit pas compter
        rushManager.handleResourceGain(p1, rushManager.getDailyResource(), 500, startInstant.minusSeconds(10));
        
        assertTrue(rushManager.registerPlayer(p1, lateTime));
        assertEquals(0.0, rushManager.getPlayerScore(p1));
        
        // Progression après inscription
        rushManager.handleResourceGain(p1, rushManager.getDailyResource(), 100, lateTime.plusSeconds(10));
        assertEquals(100.0, rushManager.getPlayerScore(p1));

        // Inscription après la fin du Rush
        Instant afterRush = startInstant.plusSeconds((duration + 5) * 60);
        UUID p2 = UUID.randomUUID();
        assertFalse(rushManager.registerPlayer(p2, afterRush));

        // Progression après la fin du Rush
        rushManager.handleResourceGain(p1, rushManager.getDailyResource(), 100, afterRush);
        assertEquals(100.0, rushManager.getPlayerScore(p1)); // score inchangé
    }

    @Test
    public void testIntraRankEloRedistribution() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        // 4 joueurs de même rang (Fer = niveau 1 à 10)
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "Fer1", 1, 0, startInstant, new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "Fer2", 1, 0, startInstant, new HashMap<>());
        PlayerProfile prof3 = new PlayerProfile(p3, "Fer3", 1, 0, startInstant, new HashMap<>());
        PlayerProfile prof4 = new PlayerProfile(p4, "Fer4", 1, 0, startInstant, new HashMap<>());

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        cache.put(p3, prof3);
        cache.put(p4, prof4);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);
        rushManager.registerPlayer(p3, startInstant);
        rushManager.registerPlayer(p4, startInstant);

        // Simuler scores : S1=1000, S2=900, S3=450, S4=50
        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 1000, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 900, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p3, res, 450, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p4, res, 50, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();

        // P1: +20, P2: +15, P3: -8, P4: -28
        assertEquals(20, prof1.getElo());
        assertEquals(15, prof2.getElo());
        assertEquals(0, prof3.getElo()); // Commencé à 0 ELO, donc plancher de sécurité bloque à 0 (0 ELO de départ - 8 = -8 -> bloqué à 0)
        
        // Mettons un ELO de départ de 50 à tout le monde pour tester les variations exactes sans plancher
        prof1.setElo(50); prof2.setElo(50); prof3.setElo(50); prof4.setElo(50);
        rushManager.setupDaily(time0800);
        res = rushManager.getDailyResource();
        start = rushManager.getStartTime();
        startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        endInstant = startInstant.plusSeconds(rushManager.getDurationMinutes() * 60L);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);
        rushManager.registerPlayer(p3, startInstant);
        rushManager.registerPlayer(p4, startInstant);
        rushManager.handleResourceGain(p1, res, 1000, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 900, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p3, res, 450, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p4, res, 50, startInstant.plusSeconds(5));
        
        rushManager.endRush(endInstant).join();

        assertEquals(70, prof1.getElo());  // 50 + 20 = 70
        assertEquals(65, prof2.getElo());  // 50 + 15 = 65
        assertEquals(42, prof3.getElo());  // 50 - 8 = 42
        assertEquals(22, prof4.getElo());  // 50 - 28 = 22
    }

    @Test
    public void testIntraRankLowGap() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        // 2 Platine (niveau 41 à 50, elo-factor = 15.0)
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "Plat1", 45, 50, startInstant, new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "Plat2", 45, 50, startInstant, new HashMap<>());

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 45, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();

        // Attendu: Plat1 = +1 ELO, Plat2 = -1 ELO
        assertEquals(51, prof1.getElo());
        assertEquals(49, prof2.getElo());
    }

    @Test
    public void testCrossRankEloRedistribution() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        // 2 Platine, 1 Or (35), 1 Fer (5)
        UUID plat1 = UUID.randomUUID();
        UUID plat2 = UUID.randomUUID();
        UUID or1 = UUID.randomUUID();
        UUID fer1 = UUID.randomUUID();

        PlayerProfile profPlat1 = new PlayerProfile(plat1, "Plat1", 45, 50, startInstant, new HashMap<>());
        PlayerProfile profPlat2 = new PlayerProfile(plat2, "Plat2", 45, 50, startInstant, new HashMap<>());
        PlayerProfile profOr = new PlayerProfile(or1, "Or1", 35, 50, startInstant, new HashMap<>());
        PlayerProfile profFer = new PlayerProfile(fer1, "Fer1", 5, 50, startInstant, new HashMap<>());

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(plat1, profPlat1);
        cache.put(plat2, profPlat2);
        cache.put(or1, profOr);
        cache.put(fer1, profFer);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(plat1, startInstant);
        rushManager.registerPlayer(plat2, startInstant);
        rushManager.registerPlayer(or1, startInstant);
        rushManager.registerPlayer(fer1, startInstant);

        String res = rushManager.getDailyResource();
        // Pour les Platines (Intra-Rang):
        rushManager.handleResourceGain(plat1, res, 100, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(plat2, res, 80, startInstant.plusSeconds(5));
        // Pour les Orphelins (Transversale): Or = 500, Fer = 100
        rushManager.handleResourceGain(or1, res, 500, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(fer1, res, 100, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();

        // Les Platines s'évaluent entre eux
        // Plat1: S=100, Plat2: S=80. Smax=100. R1=1.0, R2=0.8. Moyenne R = 0.9. D1=0.1, D2=-0.1
        // Platine elo-factor = 15.0. 15.0 * 0.1 = +1.5 -> +2. -1.5 -> -2.
        assertEquals(52, profPlat1.getElo());
        assertEquals(48, profPlat2.getElo());

        // Or et Fer s'évaluent de manière transversale.
        // Or: S=500, Fer: S=100. Smax=500. R_or=1.0, R_fer=0.2. Moyenne R = 0.6. D_or=0.4, D_fer=-0.4
        // ELO raw = 30.0 * D.
        // Or ELO raw = 30.0 * 0.4 = +12. Gain mult Or = 0.75. Or ELO gain = round(12 * 0.75) = +9.
        // Fer ELO raw = 30.0 * -0.4 = -12. Loss mult Fer = 0.5. Fer ELO loss = round(-12 * 0.5) = -6.
        assertEquals(59, profOr.getElo()); // 50 + 9 = 59
        assertEquals(44, profFer.getElo()); // 50 - 6 = 44
    }

    @Test
    public void testRushEloMinLimit() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // Joueur Argent (niveau 25) avec 5 ELO
        PlayerProfile prof1 = new PlayerProfile(p1, "Argent1", 25, 5, startInstant, new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "Argent2", 25, 80, startInstant, new HashMap<>());

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 500, startInstant.plusSeconds(5)); // prof2 a un score bcp plus haut, prof1 va perdre de l'ELO

        rushManager.endRush(endInstant).join();

        // prof1 doit perdre plus de 5 ELO mais est bloqué à 0 ELO pour son niveau Argent (pas de rétrogradation)
        assertEquals(0, prof1.getElo());
        assertEquals(25, prof1.getRankLevel()); // Conserve son niveau 25
    }

    @Test
    public void testRushEdgeCases() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        String res = rushManager.getDailyResource();

        // 1. Ties (Égalités)
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        PlayerProfile prof1 = new PlayerProfile(p1, "Player1", 15, 50, startInstant, new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "Player2", 15, 50, startInstant, new HashMap<>());

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);
        rushManager.handleResourceGain(p1, res, 300, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 300, startInstant.plusSeconds(5));
        
        rushManager.endRush(endInstant).join();
        assertEquals(50, prof1.getElo());
        assertEquals(50, prof2.getElo());

        // 2. Score Max Nul
        prof1.setElo(50); prof2.setElo(50);
        rushManager.setupDaily(time0800);
        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);
        // Aucun gain de ressource (score max = 0)
        rushManager.endRush(endInstant).join();
        assertEquals(50, prof1.getElo());
        assertEquals(50, prof2.getElo());

        // 3. Joueur unique
        UUID soloUuid = UUID.randomUUID();
        PlayerProfile soloProf = new PlayerProfile(soloUuid, "Solo", 15, 50, startInstant, new HashMap<>());
        cache.clear();
        cache.put(soloUuid, soloProf);
        rushManager.setupDaily(time0800);
        rushManager.registerPlayer(soloUuid, startInstant);
        rushManager.handleResourceGain(soloUuid, res, 500, startInstant.plusSeconds(5));
        rushManager.endRush(endInstant).join();
        assertEquals(50, soloProf.getElo()); // Variation ELO = 0
    }

    @Test
    public void testRushDisconnectReconnect() {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        UUID p1 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "Player1", 1, 0, startInstant, new HashMap<>());
        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        rushManager.setProfileCacheOverride(cache);

        // Inscription
        rushManager.registerPlayer(p1, startInstant);

        // Gain initial
        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 150, startInstant.plusSeconds(5));
        assertEquals(150.0, rushManager.getPlayerScore(p1));

        // Déconnexion (retiré du cache)
        cache.remove(p1);

        // Reconnexion (remis dans le cache)
        cache.put(p1, prof1);

        // Vérification que le score est préservé et qu'il reprend à 150
        assertEquals(150.0, rushManager.getPlayerScore(p1));
        rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(10));
        assertEquals(200.0, rushManager.getPlayerScore(p1));
    }

    @Test
    public void testRushOfflineDistribution() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        UUID p1 = UUID.randomUUID(); // Joueur en ligne (Fer)
        UUID p2 = UUID.randomUUID(); // Joueur déconnecté à la fin (Fer)

        PlayerProfile prof1 = new PlayerProfile(p1, "Online", 5, 50, startInstant, new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "Offline", 5, 50, startInstant, new HashMap<>());

        // Persist the offline profile in our database so the manager can load it
        dbManager.saveProfile(prof2).get();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        // prof2 n'est PAS dans le cache (il est hors-ligne)
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 100, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 900, startInstant.plusSeconds(5)); // score plus élevé

        // Fin du Rush
        rushManager.endRush(endInstant).join();

        // Reload prof2 from DB to see if ELO was updated (50 + 22 = 72)
        PlayerProfile updatedProf2 = dbManager.loadProfile(p2, "Offline").get();
        assertEquals(72, updatedProf2.getElo());
        
        // prof2 doit avoir un message récapitulatif dans son quotaProgress
        assertTrue(updatedProf2.getQuotaProgress().containsKey("rush_pending_summary"));
        assertEquals("+22 ELO", updatedProf2.getQuotaProgress().get("rush_pending_summary"));

        // Simuler la reconnexion de prof2
        cache.put(p2, updatedProf2);
        
        // Appeler la vérification de bilan au retour
        StringBuilder messageSent = new StringBuilder();
        rushManager.checkOfflineSummary(updatedProf2, (msg) -> messageSent.append(msg)).join();

        // Message de recap doit être envoyé au joueur
        String text = messageSent.toString();
        assertTrue(text.contains("gagné") && text.contains("+22 ELO"));
        // La clé doit être nettoyée du profil
        assertFalse(updatedProf2.getQuotaProgress().containsKey("rush_pending_summary"));
    }

    @Test
    public void testRushAdminCommandExclusion() {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        UUID p1 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "Player1", 1, 0, startInstant, new HashMap<>());
        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);

        String res = rushManager.getDailyResource();

        // Gain normal (hors commande)
        rushManager.handleResourceGain(p1, res, 100, startInstant.plusSeconds(5));
        assertEquals(100.0, rushManager.getPlayerScore(p1));

        // Simuler un gain via commande administrative
        class AdminCommandSimulator {
            void run() {
                rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(10));
            }
        }
        
        new AdminCommandSimulator().run();

        // Le score doit être inchangé (toujours 100.0), car l'appelant contient "Command" dans sa stack trace
        assertEquals(100.0, rushManager.getPlayerScore(p1));
    }
}
