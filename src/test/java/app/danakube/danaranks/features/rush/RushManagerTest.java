package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.database.ProfileRepository;
import app.danakube.danaranks.core.profile.PlayerProfile;
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
        
        Instant lateTime = startInstant.plusSeconds(15 * 60);
        UUID p1 = UUID.randomUUID();
        
        rushManager.handleResourceGain(p1, rushManager.getDailyResource(), 500, startInstant.minusSeconds(10));
        
        assertTrue(rushManager.registerPlayer(p1, lateTime));
        assertEquals(0.0, rushManager.getPlayerScore(p1));
        
        rushManager.handleResourceGain(p1, rushManager.getDailyResource(), 100, lateTime.plusSeconds(10));
        assertEquals(100.0, rushManager.getPlayerScore(p1));

        Instant afterRush = startInstant.plusSeconds((duration + 5) * 60);
        UUID p2 = UUID.randomUUID();
        assertFalse(rushManager.registerPlayer(p2, afterRush));

        rushManager.handleResourceGain(p1, rushManager.getDailyResource(), 100, afterRush);
        assertEquals(100.0, rushManager.getPlayerScore(p1));
    }

    @Test
    public void testIntraRankEloRedistribution() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

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

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 1000, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 900, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p3, res, 450, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p4, res, 50, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();

        assertEquals(20, prof1.getElo());
        assertEquals(15, prof2.getElo());
        assertEquals(0, prof3.getElo());
        
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

        assertEquals(70, prof1.getElo());
        assertEquals(65, prof2.getElo());
        assertEquals(42, prof3.getElo());
        assertEquals(22, prof4.getElo());
    }

    @Test
    public void testIntraRankLowGap() throws Exception {
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

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
        rushManager.handleResourceGain(plat1, res, 100, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(plat2, res, 80, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(or1, res, 500, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(fer1, res, 100, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();

        assertEquals(52, profPlat1.getElo());
        assertEquals(48, profPlat2.getElo());

        assertEquals(59, profOr.getElo());
        assertEquals(44, profFer.getElo());
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
        rushManager.handleResourceGain(p2, res, 500, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();

        assertEquals(0, prof1.getElo());
        assertEquals(25, prof1.getRankLevel());
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

        prof1.setElo(50); prof2.setElo(50);
        rushManager.setupDaily(time0800);
        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);
        rushManager.endRush(endInstant).join();
        assertEquals(50, prof1.getElo());
        assertEquals(50, prof2.getElo());

        UUID soloUuid = UUID.randomUUID();
        PlayerProfile soloProf = new PlayerProfile(soloUuid, "Solo", 15, 50, startInstant, new HashMap<>());
        cache.clear();
        cache.put(soloUuid, soloProf);
        rushManager.setupDaily(time0800);
        rushManager.registerPlayer(soloUuid, startInstant);
        rushManager.handleResourceGain(soloUuid, res, 500, startInstant.plusSeconds(5));
        rushManager.endRush(endInstant).join();
        assertEquals(50, soloProf.getElo());
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

        rushManager.registerPlayer(p1, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 150, startInstant.plusSeconds(5));
        assertEquals(150.0, rushManager.getPlayerScore(p1));
        cache.remove(p1);

        cache.put(p1, prof1);
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

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "Online", 5, 50, startInstant, new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "Offline", 5, 50, startInstant, new HashMap<>());
        new ProfileRepository(dbManager).saveProfile(prof2).get();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 100, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 900, startInstant.plusSeconds(5));

        rushManager.endRush(endInstant).join();
        PlayerProfile updatedProf2 = new ProfileRepository(dbManager).loadProfile(p2, "Offline").get().orElse(null);
        assertEquals(72, updatedProf2.getElo());
        assertTrue(updatedProf2.getQuotaProgress().containsKey("rush_pending_summary"));
        assertEquals("+22 ELO", updatedProf2.getQuotaProgress().get("rush_pending_summary"));

        cache.put(p2, updatedProf2);
        StringBuilder messageSent = new StringBuilder();
        rushManager.checkOfflineSummary(updatedProf2, (msg) -> messageSent.append(msg)).join();

        String text = messageSent.toString();
        assertTrue(text.contains("gagné") && text.contains("+22 ELO"));
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

        rushManager.handleResourceGain(p1, res, 100, startInstant.plusSeconds(5));
        assertEquals(100.0, rushManager.getPlayerScore(p1));

        class AdminCommandSimulator {
            void run() {
                rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(10));
            }
        }
        
        new AdminCommandSimulator().run();

        assertEquals(100.0, rushManager.getPlayerScore(p1));
    }
}
