package app.danakube.danaranks.core.profile;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerProfileTest {

    @Test
    public void testEloProgression() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", 1, 0, Instant.now(), new HashMap<>());
        EloService eloService = new EloService(null, null);
        int ranksGained = eloService.addElo(profile, 120, "TEST");
        assertEquals(1, ranksGained);
        assertEquals(2, profile.getRankLevel());
        assertEquals(20, profile.getElo());
    }

    @Test
    public void testEloMinLimit() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", 1, 10, Instant.now(), new HashMap<>());
        EloService eloService = new EloService(null, null);
        int ranksGained = eloService.addElo(profile, -20, "TEST");
        assertEquals(0, ranksGained);
        assertEquals(1, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }

    @Test
    public void testMultiPromotion() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer", 1, 0, Instant.now(), new HashMap<>());
        EloService eloService = new EloService(null, null);
        int ranksGained = eloService.addElo(profile, 250, "TEST");
        assertEquals(2, ranksGained);
        assertEquals(3, profile.getRankLevel());
        assertEquals(50, profile.getElo());
    }

    @Test
    public void testConcurrentEloAccess() throws Exception {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "ConcurrentPlayer", 1, 0, Instant.now(), new HashMap<>());
        EloService eloService = new EloService(null, null);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                eloService.addElo(profile, 10, "TEST");
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
