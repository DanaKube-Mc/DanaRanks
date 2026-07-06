package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.core.profile.PlayerProfile;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class RushEloCalculatorTest {

    @Test
    public void testGroupProfilesByTier() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        PlayerProfile profFer = new PlayerProfile(p1, "Fer", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile profBronze = new PlayerProfile(p2, "Bronze", 15, 0, Instant.now(), new HashMap<>());
        PlayerProfile profArgent = new PlayerProfile(p3, "Argent", 25, 0, Instant.now(), new HashMap<>());

        List<PlayerProfile> profiles = Arrays.asList(profFer, profBronze, profArgent);
        Map<String, List<PlayerProfile>> grouped = RushEloCalculator.groupProfilesByTier(profiles);

        assertEquals(1, grouped.get("fer").size());
        assertEquals(1, grouped.get("bronze").size());
        assertEquals(1, grouped.get("argent").size());
        assertEquals(0, grouped.get("or").size());
        assertEquals(0, grouped.get("platine").size());
    }

    @Test
    public void testCalculateIntraRankEloChangesZeroSumAndTies() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "P1", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "P2", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile prof3 = new PlayerProfile(p3, "P3", 5, 0, Instant.now(), new HashMap<>());

        List<PlayerProfile> players = Arrays.asList(prof1, prof2, prof3);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 100.0);
        scores.put(p2, 100.0);
        scores.put(p3, 100.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();
        RushEloCalculator.calculateIntraRankEloChanges(players, scores, 30.0, eloChanges);

        assertEquals(0, eloChanges.get(p1));
        assertEquals(0, eloChanges.get(p2));
        assertEquals(0, eloChanges.get(p3));
    }

    @Test
    public void testCalculateIntraRankEloChangesMaxScoreNul() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "P1", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "P2", 5, 0, Instant.now(), new HashMap<>());

        List<PlayerProfile> players = Arrays.asList(prof1, prof2);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 0.0);
        scores.put(p2, 0.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();
        RushEloCalculator.calculateIntraRankEloChanges(players, scores, 30.0, eloChanges);

        assertEquals(0, eloChanges.get(p1));
        assertEquals(0, eloChanges.get(p2));
    }

    @Test
    public void testCalculateIntraRankEloChangesNormalDistribution() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "P1", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "P2", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile prof3 = new PlayerProfile(p3, "P3", 5, 0, Instant.now(), new HashMap<>());

        List<PlayerProfile> players = Arrays.asList(prof1, prof2, prof3);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 1000.0);
        scores.put(p2, 500.0);
        scores.put(p3, 0.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();
        RushEloCalculator.calculateIntraRankEloChanges(players, scores, 30.0, eloChanges);

        assertEquals(15, eloChanges.get(p1));
        assertEquals(0, eloChanges.get(p2));
        assertEquals(-15, eloChanges.get(p3));
    }

    @Test
    public void testCalculateOrphanEloChanges() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = new PlayerProfile(p1, "P1", 5, 0, Instant.now(), new HashMap<>());
        PlayerProfile prof2 = new PlayerProfile(p2, "P2", 15, 0, Instant.now(), new HashMap<>());

        List<PlayerProfile> orphans = Arrays.asList(prof1, prof2);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 500.0);
        scores.put(p2, 1000.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();
        Map<String, RushEloCalculator.TierSettings> settingsMap = new HashMap<>();
        settingsMap.put("fer", new RushEloCalculator.TierSettings(30.0, 1.5, 0.5));
        settingsMap.put("bronze", new RushEloCalculator.TierSettings(30.0, 1.0, 1.0));

        RushEloCalculator.calculateOrphanEloChanges(orphans, scores, eloChanges, settingsMap);

        assertEquals(-4, eloChanges.get(p1));
        assertEquals(8, eloChanges.get(p2));
    }

    @Test
    public void testCalculateOrphanSinglePlayer() {
        UUID p1 = UUID.randomUUID();
        PlayerProfile prof1 = new PlayerProfile(p1, "P1", 5, 0, Instant.now(), new HashMap<>());

        List<PlayerProfile> orphans = Collections.singletonList(prof1);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 500.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();
        RushEloCalculator.calculateOrphanEloChanges(orphans, scores, eloChanges, Collections.emptyMap());

        assertEquals(0, eloChanges.get(p1));
    }
}
