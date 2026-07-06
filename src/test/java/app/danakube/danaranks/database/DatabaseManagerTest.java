package app.danakube.danaranks.database;

import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.core.profile.PlayerProfileBuilder;
import app.danakube.danaranks.core.profile.HistoryEntry;
import app.danakube.danaranks.core.profile.EloService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {

    @Test
    public void testDatabaseIntegration() throws Exception {
        // Arrange
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        ProfileRepository profileRepo = new ProfileRepository(dbManager);
        HistoryRepository historyRepo = new HistoryRepository(dbManager);
        
        UUID uuid = UUID.randomUUID();
        String name = "DbTestPlayer";
        
        // Act & Assert 1 (Load profile)
        PlayerProfile profile = profileRepo.loadProfile(uuid, name).get().orElseGet(() -> 
                PlayerProfileBuilder.aProfile().uuid(uuid).name(name).build()
        );
        assertNotNull(profile);
        assertEquals(name, profile.getPlayerName());
        assertEquals(1, profile.getRankLevel());
        assertEquals(0, profile.getElo());
        
        // Act 2 (Modify profile and save)
        profile.setRankLevel(5);
        profile.setElo(45);
        Map<String, Object> quota = new HashMap<>();
        quota.put("job_xp", 500.0);
        quota.put("lumens_gained", 1200.0);
        profile.setQuotaProgress(quota);
        profileRepo.saveProfile(profile).get();

        // Assert 2 (Check saved profile values)
        PlayerProfile loaded = profileRepo.loadProfile(uuid, name).get().orElse(null);
        assertNotNull(loaded);
        assertEquals(5, loaded.getRankLevel());
        assertEquals(45, loaded.getElo());
        assertEquals(500.0, ((Double) loaded.getQuotaProgress().get("job_xp")), 0.01);
        assertEquals(1200.0, ((Double) loaded.getQuotaProgress().get("lumens_gained")), 0.01);

        // Act 3 (Log history and fetch)
        historyRepo.logHistory(uuid, "QUOTA_SUCCESS", 10, 55, "Completed job quest").get();
        Thread.sleep(10);
        historyRepo.logHistory(uuid, "RUSH", 20, 75, "Won rush event").get();

        // Assert 3 (Check history entries)
        List<HistoryEntry> history = historyRepo.fetchHistory(uuid, 10).get();
        assertEquals(2, history.size());
        assertEquals("RUSH", history.get(0).type());
        assertEquals(20, history.get(0).eloChange());
        assertEquals(75, history.get(0).newElo());
        assertEquals("Won rush event", history.get(0).description());
        
        assertEquals("QUOTA_SUCCESS", history.get(1).type());
        
        // Cleanup
        dbManager.close();
    }

    @Test
    public void testOfflineEloModification() throws Exception {
        // Arrange
        class PermissionHookSpy implements app.danakube.danaranks.hooks.PermissionHook {
            int callCount = 0;
            UUID lastUuid = null;
            int lastRanks = 0;

            @Override
            public void promote(UUID uuid, int ranks) {
                callCount++;
                lastUuid = uuid;
                lastRanks = ranks;
            }

            @Override
            public void demote(UUID uuid, int ranks) {
            }
        }

        PermissionHookSpy spy = new PermissionHookSpy();

        try {
            DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
            ProfileRepository profileRepo = new ProfileRepository(dbManager);
            HistoryRepository historyRepo = new HistoryRepository(dbManager);
            
            UUID uuid = UUID.randomUUID();
            String name = "OfflinePlayer";

            PlayerProfile profile = profileRepo.loadProfile(uuid, name).get().orElseGet(() -> 
                    PlayerProfileBuilder.aProfile().uuid(uuid).name(name).build()
            );

            EloService eloService = new EloService(spy, historyRepo);
            
            // Act
            eloService.addElo(profile, 150, "TEST");

            // Assert
            assertEquals(1, spy.callCount);
            assertEquals(uuid, spy.lastUuid);
            assertEquals(1, spy.lastRanks);
            profileRepo.saveProfile(profile).get();

            PlayerProfile loaded = profileRepo.loadProfile(uuid, name).get().orElse(null);
            assertNotNull(loaded);
            assertEquals(2, loaded.getRankLevel());
            assertEquals(50, loaded.getElo());

            // Cleanup
            dbManager.close();
        } finally {
            // Cleanup
        }
    }

    @Test
    public void testSqlInjectionSafety() throws Exception {
        // Arrange
        DatabaseManager dbManager = new DatabaseManager("jdbc:sqlite::memory:");
        ProfileRepository profileRepo = new ProfileRepository(dbManager);
        
        UUID uuid = UUID.randomUUID();
        String maliciousName = "Jeux'; DROP TABLE danaranks_profiles; --";
        
        PlayerProfile profile = PlayerProfileBuilder.aProfile().uuid(uuid).name(maliciousName).rank(10).elo(80).build();
        
        // Act
        profileRepo.saveProfile(profile).get();
        
        // Assert
        PlayerProfile loaded = profileRepo.loadProfile(uuid, maliciousName).get().orElse(null);
        assertNotNull(loaded);
        assertEquals(maliciousName, loaded.getPlayerName());
        assertEquals(10, loaded.getRankLevel());
        assertEquals(80, loaded.getElo());

        Optional<PlayerProfile> testOther = profileRepo.loadProfile(UUID.randomUUID(), "Other").get();
        assertNotNull(testOther);
        assertTrue(testOther.isEmpty());
        
        // Cleanup
        dbManager.close();
    }
}
