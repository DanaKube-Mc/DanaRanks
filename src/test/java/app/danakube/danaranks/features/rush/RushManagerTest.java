package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.database.ProfileRepository;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.core.profile.PlayerProfileBuilder;
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
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);

        // Act
        rushManager.setupDaily(time0800);

        // Assert
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
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        UUID p1 = UUID.randomUUID();

        // Act
        rushManager.registerPlayer(p1, startInstant.plusSeconds(300));

        // Assert
        assertEquals(0.0, rushManager.getPlayerScore(p1));
    }

    @Test
    public void testRegistrationBlockedAfterEnd() {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);
        UUID p1 = UUID.randomUUID();

        // Act
        boolean registered = rushManager.registerPlayer(p1, endInstant.plusSeconds(1));

        // Assert
        assertFalse(registered);
        assertEquals(-1.0, rushManager.getPlayerScore(p1));
    }

    @Test
    public void testScoreIncrementDuringActiveRush() {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        UUID p1 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Player1").rank(1).elo(0).build();
        profileCache.put(p1, prof1);

        rushManager.registerPlayer(p1, startInstant);

        // Act
        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 100.0, startInstant.plusSeconds(5));

        // Assert
        assertEquals(100.0, rushManager.getPlayerScore(p1));
    }

    @Test
    public void testIntraRankEloRedistribution() throws Exception {
        // Arrange
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

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Fer1").rank(1).elo(0).lastReset(startInstant).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("Fer2").rank(1).elo(0).lastReset(startInstant).build();
        PlayerProfile prof3 = PlayerProfileBuilder.aProfile().uuid(p3).name("Fer3").rank(1).elo(0).lastReset(startInstant).build();
        PlayerProfile prof4 = PlayerProfileBuilder.aProfile().uuid(p4).name("Fer4").rank(1).elo(0).lastReset(startInstant).build();

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

        // Act 1
        rushManager.endRush(endInstant).join();

        // Assert 1
        assertEquals(20, prof1.getElo());
        assertEquals(15, prof2.getElo());
        assertEquals(0, prof3.getElo());
        
        // Act 2 (Reset scores & run a second rush)
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

        // Assert 2
        assertEquals(70, prof1.getElo());
        assertEquals(65, prof2.getElo());
        assertEquals(42, prof3.getElo());
        assertEquals(22, prof4.getElo());
    }

    @Test
    public void testIntraRankLowGap() throws Exception {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Plat1").rank(45).elo(50).lastReset(startInstant).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("Plat2").rank(45).elo(50).lastReset(startInstant).build();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 45, startInstant.plusSeconds(5));

        // Act
        rushManager.endRush(endInstant).join();

        // Assert
        assertEquals(51, prof1.getElo());
        assertEquals(49, prof2.getElo());
    }

    @Test
    public void testIntraRankHighGap() throws Exception {
        // Arrange
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

        PlayerProfile profPlat1 = PlayerProfileBuilder.aProfile().uuid(plat1).name("Plat1").rank(45).elo(50).lastReset(startInstant).build();
        PlayerProfile profPlat2 = PlayerProfileBuilder.aProfile().uuid(plat2).name("Plat2").rank(45).elo(50).lastReset(startInstant).build();
        PlayerProfile profOr = PlayerProfileBuilder.aProfile().uuid(or1).name("Or1").rank(35).elo(50).lastReset(startInstant).build();
        PlayerProfile profFer = PlayerProfileBuilder.aProfile().uuid(fer1).name("Fer1").rank(5).elo(50).lastReset(startInstant).build();

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
        rushManager.handleResourceGain(plat2, res, 90, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(or1, res, 200, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(fer1, res, 300, startInstant.plusSeconds(5));

        // Act
        rushManager.endRush(endInstant).join();

        // Assert
        assertEquals(51, profPlat1.getElo());
        assertEquals(49, profPlat2.getElo());
        assertEquals(44, profOr.getElo());
        assertEquals(58, profFer.getElo());
    }

    @Test
    public void testRushEloMinLimit() throws Exception {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Argent1").rank(25).elo(5).lastReset(startInstant).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("Argent2").rank(25).elo(80).lastReset(startInstant).build();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 500, startInstant.plusSeconds(5));

        // Act
        rushManager.endRush(endInstant).join();

        // Assert
        assertEquals(0, prof1.getElo());
        assertEquals(25, prof1.getRankLevel());
    }

    @Test
    public void testRushEdgeCases() throws Exception {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        String res = rushManager.getDailyResource();

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Player1").rank(15).elo(50).lastReset(startInstant).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("Player2").rank(15).elo(50).lastReset(startInstant).build();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        cache.put(p2, prof2);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        // Act & Assert 1 (Gain of 0.0)
        rushManager.handleResourceGain(p1, res, 0.0, startInstant.plusSeconds(5));
        assertEquals(0.0, rushManager.getPlayerScore(p1));

        // Act & Assert 2 (Null/empty score list end)
        rushManager.endRush(endInstant).join();
        assertEquals(50, prof1.getElo());
        assertEquals(50, prof2.getElo());
    }

    @Test
    public void testSingleParticipantReward() throws Exception {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        UUID soloUuid = UUID.randomUUID();
        PlayerProfile soloProf = PlayerProfileBuilder.aProfile().uuid(soloUuid).name("Solo").rank(15).elo(50).lastReset(startInstant).build();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(soloUuid, soloProf);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(soloUuid, startInstant);

        // Act
        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(soloUuid, res, 500, startInstant.plusSeconds(5));
        rushManager.endRush(endInstant).join();

        // Assert
        assertEquals(50, soloProf.getElo());
    }

    @Test
    public void testRushDisconnectReconnect() {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        UUID p1 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Player1").rank(1).elo(0).lastReset(startInstant).build();
        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);

        // Act & Assert 1
        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 150, startInstant.plusSeconds(5));
        assertEquals(150.0, rushManager.getPlayerScore(p1));
        cache.remove(p1);

        // Act & Assert 2
        cache.put(p1, prof1);
        assertEquals(150.0, rushManager.getPlayerScore(p1));
        rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(10));
        assertEquals(200.0, rushManager.getPlayerScore(p1));
    }

    @Test
    public void testRushOfflineDistribution() throws Exception {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        int duration = rushManager.getDurationMinutes();
        Instant endInstant = startInstant.plusSeconds(duration * 60);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Online").rank(5).elo(50).lastReset(startInstant).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("Offline").rank(5).elo(50).lastReset(startInstant).build();
        new ProfileRepository(dbManager).saveProfile(prof2).get();

        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);
        rushManager.registerPlayer(p2, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 100, startInstant.plusSeconds(5));
        rushManager.handleResourceGain(p2, res, 900, startInstant.plusSeconds(5));

        // Act 1
        rushManager.endRush(endInstant).join();

        // Assert 1
        PlayerProfile updatedProf2 = new ProfileRepository(dbManager).loadProfile(p2, "Offline").get().orElse(null);
        assertEquals(72, updatedProf2.getElo());
        assertTrue(updatedProf2.getQuotaProgress().containsKey("rush_pending_summary"));
        assertEquals("+22 ELO", updatedProf2.getQuotaProgress().get("rush_pending_summary"));

        // Act 2
        cache.put(p2, updatedProf2);
        StringBuilder messageSent = new StringBuilder();
        rushManager.checkOfflineSummary(updatedProf2, (msg) -> messageSent.append(msg)).join();

        // Assert 2
        String text = messageSent.toString();
        assertTrue(text.contains("gagné") && text.contains("+22 ELO"));
        assertFalse(updatedProf2.getQuotaProgress().containsKey("rush_pending_summary"));
    }

    @Test
    public void testRushAdminCommandExclusion() {
        // Arrange
        LocalDateTime time0800 = LocalDateTime.of(2026, 7, 3, 8, 0);
        rushManager.setupDaily(time0800);
        
        LocalDateTime start = rushManager.getStartTime();
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        UUID p1 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("Player1").rank(1).elo(0).lastReset(startInstant).build();
        Map<UUID, PlayerProfile> cache = new HashMap<>();
        cache.put(p1, prof1);
        rushManager.setProfileCacheOverride(cache);

        rushManager.registerPlayer(p1, startInstant);

        String res = rushManager.getDailyResource();
        rushManager.handleResourceGain(p1, res, 100, startInstant.plusSeconds(5));
        assertEquals(100.0, rushManager.getPlayerScore(p1));

        // Act
        class AdminCommandSimulator {
            void run() {
                rushManager.handleResourceGain(p1, res, 50, startInstant.plusSeconds(10));
            }
        }
        new AdminCommandSimulator().run();

        // Assert
        assertEquals(100.0, rushManager.getPlayerScore(p1));
    }
}
