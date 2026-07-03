package app.danakube.danaranks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class DanaRanksTest {

    @Test
    public void testEloProgression() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", 1, 0, Instant.now(), new HashMap<>());
        int ranksGained = profile.addElo(120);
        assertEquals(1, ranksGained);
        assertEquals(2, profile.getRankLevel());
        assertEquals(20, profile.getElo());
    }

    @Test
    public void testEloMinLimit() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", 1, 10, Instant.now(), new HashMap<>());
        int ranksGained = profile.addElo(-20);
        assertEquals(0, ranksGained);
        assertEquals(1, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }

    @Test
    public void testDatabaseIntegration() throws Exception {
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        UUID uuid = UUID.randomUUID();
        String name = "DbTestPlayer";
        
        PlayerProfile profile = dbManager.loadProfile(uuid, name).get();
        assertNotNull(profile);
        assertEquals(name, profile.getPlayerName());
        assertEquals(1, profile.getRankLevel());
        assertEquals(0, profile.getElo());
        
        profile.setRankLevel(5);
        profile.setElo(45);
        Map<String, Object> quota = new HashMap<>();
        quota.put("job_xp", 500.0);
        quota.put("lumens_gained", 1200.0);
        profile.setQuotaProgress(quota);
        
        dbManager.saveProfile(profile).get();

        PlayerProfile loaded = dbManager.loadProfile(uuid, name).get();
        assertNotNull(loaded);
        assertEquals(5, loaded.getRankLevel());
        assertEquals(45, loaded.getElo());
        assertEquals(500.0, ((Double) loaded.getQuotaProgress().get("job_xp")), 0.01);
        assertEquals(1200.0, ((Double) loaded.getQuotaProgress().get("lumens_gained")), 0.01);

        dbManager.logHistory(uuid, "QUOTA_SUCCESS", 10, 55, "Completed job quest").get();
        Thread.sleep(10);
        dbManager.logHistory(uuid, "RUSH", 20, 75, "Won rush event").get();

        List<HistoryEntry> history = dbManager.fetchHistory(uuid, 10).get();
        assertEquals(2, history.size());
        assertEquals("RUSH", history.get(0).getType());
        assertEquals(20, history.get(0).getEloChange());
        assertEquals(75, history.get(0).getNewElo());
        assertEquals("Won rush event", history.get(0).getDescription());
        
        assertEquals("QUOTA_SUCCESS", history.get(1).getType());
        
        dbManager.close();
    }

    @Test
    public void testMultiPromotion() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", 1, 0, Instant.now(), new HashMap<>());
        int ranksGained = profile.addElo(250);
        assertEquals(2, ranksGained);
        assertEquals(3, profile.getRankLevel());
        assertEquals(50, profile.getElo());
    }

    @Test
    public void testOfflineEloModification() throws Exception {
        class PromotionSpy implements BiConsumer<UUID, Integer> {
            int callCount = 0;
            UUID lastUuid = null;
            int lastRanks = 0;

            @Override
            public void accept(UUID uuid, Integer ranks) {
                callCount++;
                lastUuid = uuid;
                lastRanks = ranks;
            }
        }

        PromotionSpy spy = new PromotionSpy();
        PlayerProfile.setPromotionCallback(spy);

        try {
            DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
            UUID uuid = UUID.randomUUID();
            String name = "OfflinePlayer";

            PlayerProfile profile = dbManager.loadProfile(uuid, name).get();

            profile.addElo(150);

            assertEquals(1, spy.callCount);
            assertEquals(uuid, spy.lastUuid);
            assertEquals(1, spy.lastRanks);
            dbManager.saveProfile(profile).get();

            PlayerProfile loaded = dbManager.loadProfile(uuid, name).get();
            assertEquals(2, loaded.getRankLevel());
            assertEquals(50, loaded.getElo());

            dbManager.close();
        } finally {
            PlayerProfile.setPromotionCallback(null);
        }
    }

    @Test
    public void testSqlInjectionSafety() throws Exception {
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        UUID uuid = UUID.randomUUID();
        String maliciousName = "Jeux'; DROP TABLE danaranks_profiles; --";
        
        PlayerProfile profile = new PlayerProfile(uuid, maliciousName);
        profile.setRankLevel(10);
        profile.setElo(80);
        
        dbManager.saveProfile(profile).get();
        
        PlayerProfile loaded = dbManager.loadProfile(uuid, maliciousName).get();
        assertNotNull(loaded);
        assertEquals(maliciousName, loaded.getPlayerName());
        assertEquals(10, loaded.getRankLevel());
        assertEquals(80, loaded.getElo());

        PlayerProfile testOther = dbManager.loadProfile(UUID.randomUUID(), "Other").get();
        assertNotNull(testOther);
        
        dbManager.close();
    }

    @Test
    public void testConcurrentEloAccess() throws Exception {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "ConcurrentPlayer", 1, 0, Instant.now(), new HashMap<>());
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                profile.addElo(10);
            });
        }
        
        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }
        
        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
        
        int cumulativeElo = (profile.getRankLevel() - 1) * 100 + profile.getElo();
        assertEquals(100, cumulativeElo);
    }

    @Test
    public void testMessageLoading(@TempDir Path tempDir) throws Exception {
        Logger logger = Logger.getLogger("TestLogger");

        MessageManager manager = new MessageManager(
                tempDir.toFile(),
                logger,
                (path, replace) -> { throw new IllegalArgumentException("Resource not found in jar"); }
        );

        java.io.File expectedFile = new java.io.File(tempDir.toFile(), "lang/fr.yml");
        assertTrue(expectedFile.exists());

        assertEquals("§c[DanaRanks] Impossible de charger vos données de rang. Veuillez vous reconnecter.",
                manager.getMessage("kick-database-error"));
        assertEquals("§cVous n'avez pas la permission d'exécuter cette commande.",
                manager.getMessage("no-permission"));
        assertEquals("§aVotre profil de rang a été correctement chargé !",
                manager.getMessage("profile-loaded"));

        Component kickComponent = manager.getMessageComponent("kick-database-error");
        assertNotNull(kickComponent);

        assertEquals("§cFallback", manager.getMessage("non-existent-key", "<red>Fallback"));
    }

    @Test
    public void testImmediateBaseEloReward() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.max-surplus-elo", 10);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player1", 1, 0, Instant.now(), new HashMap<>());
        qm.resetQuotaProgress(profile, 1);

        qm.incrementProgress(profile, "lumens_gained", 999);
        assertEquals(0, profile.getElo());
        assertFalse(qm.isBaseRewarded(profile, "lumens_gained"));

        qm.incrementProgress(profile, "lumens_gained", 1);
        assertEquals(5, profile.getElo());
        assertTrue(qm.isBaseRewarded(profile, "lumens_gained"));

        qm.incrementProgress(profile, "lumens_gained", 500);
        assertEquals(5, profile.getElo());
    }

    @Test
    public void testSurplusCalculationAtReset() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.max-surplus-elo", 10);
        config.set("quotas-settings.surplus-multiplier", 10.0);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player2", 1, 0, Instant.now(), new HashMap<>());
        qm.resetQuotaProgress(profile, 1);

        qm.incrementProgress(profile, "lumens_gained", 5500);
        assertEquals(5, profile.getElo());
        
        qm.processGlobalReset(profile, Instant.now());
        assertEquals(8, profile.getElo());
    }

    @Test
    public void testNoQuotaReloadOnRankUp() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.scaling.multiplier-per-rank", 1.15);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player3", 4, 90, Instant.now(), new HashMap<>());
        qm.resetQuotaProgress(profile, 4);

        double targetRank4 = qm.getObjectiveConfig(4, "lumens_gained").getTarget();
        assertEquals(1521.0, targetRank4);

        profile.addElo(20);
        assertEquals(5, profile.getRankLevel());

        int activeRank = qm.getActiveQuotaRank(profile);
        assertEquals(4, activeRank);
        assertEquals(1521.0, qm.getObjectiveConfig(activeRank, "lumens_gained").getTarget());
    }

    @Test
    public void testResetCalculation() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.reference-date", "2026-07-03");
        config.set("reset.hour", 4);
        qm.loadConfig(config, null);

        Instant now = Instant.parse("2026-07-04T12:00:00Z");
        Instant nextReset = qm.getNextResetInstant(3, now);

        Instant expected = LocalDateTime.of(2026, 7, 6, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        assertEquals(expected, nextReset);
    }

    @Test
    public void testProportionalLoss() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.job-xp.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.fail-penalty", 10);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player4", 21, 50, Instant.now(), new HashMap<>());
        
        qm.resetQuotaProgress(profile, 1);
        qm.setActiveQuotaRank(profile, 1);
        qm.incrementProgress(profile, "job_xp", 650);
        qm.processGlobalReset(profile, Instant.now());
        assertEquals(46, profile.getElo());

        PlayerProfile profile2 = new PlayerProfile(UUID.randomUUID(), "Player4_2", 21, 50, Instant.now(), new HashMap<>());
        qm.resetQuotaProgress(profile2, 1);
        qm.setActiveQuotaRank(profile2, 1);
        qm.incrementProgress(profile2, "job_xp", 660);
        qm.processGlobalReset(profile2, Instant.now());
        assertEquals(47, profile2.getElo());
    }

    @Test
    public void testConfigValidation() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.hour", 25);
        config.set("reset.reference-date", "invalid-date-format");
        config.set("quotas-settings.surplus-multiplier", -2.5);
        config.set("quotas-settings.scaling.multiplier-per-rank", 0.8);

        qm.loadConfig(config, null);

        assertEquals(4, qm.getResetHour());
        assertEquals("2026-07-03", qm.getRefDateStr());
        assertEquals(10.0, qm.getSurplusMultiplier());
        assertEquals(1.15, qm.getScalingMultiplierPerRank());
    }

    @Test
    public void testOfflineCatchUpMultipleCycles() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.reference-date", "2026-07-03");
        config.set("reset.hour", 4);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.fail-penalty", 5);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.target", 500);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.fail-penalty", 5);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "PlayerOffline", 21, 25, Instant.now(), new HashMap<>());
        
        Instant ref = LocalDateTime.of(2026, 7, 3, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        profile.setLastReset(ref);
        qm.resetQuotaProgress(profile, 1);

        Instant now = ref.plusSeconds(15L * 86400);

        qm.handleOfflineCatchUp(profile, now);
        assertEquals(21, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }

    @Test
    public void testRank50PrestigeAndDecay() {
        QuotaManager qm = new QuotaManager();
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.reference-date", "2026-07-03");
        config.set("reset.hour", 4);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.fail-penalty", 5);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.target", 500);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.fail-penalty", 5);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "PrestigePlayer", 50, 90, Instant.now(), new HashMap<>());
        profile.addElo(150);
        assertEquals(50, profile.getRankLevel());
        assertEquals(240, profile.getElo());

        Instant ref = LocalDateTime.of(2026, 7, 3, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        profile.setLastReset(ref);
        qm.resetQuotaProgress(profile, 1);

        Instant now = ref.plusSeconds(100L * 86400);

        qm.handleOfflineCatchUp(profile, now);
        assertEquals(50, profile.getRankLevel());
        assertEquals(40, profile.getElo());
        
        profile.setElo(50);
        profile.setLastReset(ref);
        qm.resetQuotaProgress(profile, 1);
        
        qm.handleOfflineCatchUp(profile, now);
        assertEquals(50, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }
}
