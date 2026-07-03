package app.danakube.danaranks;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

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
}
