package app.danakube.danaranks.core.profile;

import app.danakube.danaranks.hooks.PermissionHook;
import app.danakube.danaranks.database.HistoryRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerProfileTest {

    private final PermissionHook stubPerms = new PermissionHook() {
        @Override
        public void promote(UUID uuid, int ranksGained) {}

        @Override
        public void demote(UUID uuid, int ranksLost) {}
    };

    private final HistoryRepository stubHistory = new HistoryRepository(null) {
        @Override
        public CompletableFuture<Void> logHistory(UUID uuid, String type, int eloChange, int newElo, String description) {
            return CompletableFuture.completedFuture(null);
        }
    };

    @Test
    public void testEloProgression() {
        // Arrange
        PlayerProfile profile = PlayerProfileBuilder.aProfile().rank(1).elo(0).build();
        EloService eloService = new EloService(stubPerms, stubHistory);

        // Act
        int ranksGained = eloService.addElo(profile, 120, "TEST");

        // Assert
        assertEquals(1, ranksGained);
        assertEquals(2, profile.getRankLevel());
        assertEquals(20, profile.getElo());
    }

    @Test
    public void testEloMinLimit() {
        // Arrange
        PlayerProfile profile = PlayerProfileBuilder.aProfile().rank(1).elo(10).build();
        EloService eloService = new EloService(stubPerms, stubHistory);

        // Act
        int ranksGained = eloService.addElo(profile, -20, "TEST");

        // Assert
        assertEquals(0, ranksGained);
        assertEquals(1, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }

    @Test
    public void testMultiPromotion() {
        // Arrange
        PlayerProfile profile = PlayerProfileBuilder.aProfile().rank(1).elo(0).build();
        EloService eloService = new EloService(stubPerms, stubHistory);

        // Act
        int ranksGained = eloService.addElo(profile, 250, "TEST");

        // Assert
        assertEquals(2, ranksGained);
        assertEquals(3, profile.getRankLevel());
        assertEquals(50, profile.getElo());
    }

    @Test
    public void testConcurrentEloAccess() throws Exception {
        // Arrange
        PlayerProfile profile = PlayerProfileBuilder.aProfile().name("ConcurrentPlayer").rank(1).elo(0).build();
        EloService eloService = new EloService(stubPerms, stubHistory);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                eloService.addElo(profile, 10, "TEST");
            });
        }
        
        // Act
        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
        
        // Assert
        int cumulativeElo = (profile.getRankLevel() - 1) * 100 + profile.getElo();
        assertEquals(100, cumulativeElo);
    }

    @Test
    public void testAdminAddEloMultiRankUp() {
        // Arrange
        PlayerProfile profile = PlayerProfileBuilder.aProfile().rank(1).elo(0).build();
        EloService eloService = new EloService(stubPerms, stubHistory);
        
        // Act
        int ranksGained = eloService.addElo(profile, 250, "Admin addelo");
        
        // Assert
        assertEquals(2, ranksGained);
        assertEquals(3, profile.getRankLevel());
        assertEquals(50, profile.getElo());
    }

    @Test
    public void testAdminRemoveEloDemotion() {
        // Arrange
        class PermissionHookSpy implements PermissionHook {
            int promotedRanks = 0;
            int demotedRanks = 0;

            @Override
            public void promote(UUID uuid, int ranksGained) {
                promotedRanks += ranksGained;
            }

            @Override
            public void demote(UUID uuid, int ranksLost) {
                demotedRanks += ranksLost;
            }
        }
        
        PermissionHookSpy spy = new PermissionHookSpy();
        PlayerProfile profile = PlayerProfileBuilder.aProfile().rank(5).elo(20).build();
        EloService eloService = new EloService(spy, stubHistory);
        
        // Act
        int ranksGained = eloService.addElo(profile, -150, "Admin removeelo", true);
        
        // Assert
        assertEquals(-2, ranksGained);
        assertEquals(3, profile.getRankLevel());
        assertEquals(70, profile.getElo());
        assertEquals(2, spy.demotedRanks);
        assertEquals(0, spy.promotedRanks);
    }
}
