package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.core.profile.PlayerProfileBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RushEloCalculatorTest {

    @Test
    public void testGroupProfilesByTier() {
        // Arrange
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        PlayerProfile profFer = PlayerProfileBuilder.aProfile().uuid(p1).name("Fer").rank(5).build();
        PlayerProfile profBronze = PlayerProfileBuilder.aProfile().uuid(p2).name("Bronze").rank(15).build();
        PlayerProfile profArgent = PlayerProfileBuilder.aProfile().uuid(p3).name("Argent").rank(25).build();

        List<PlayerProfile> profiles = Arrays.asList(profFer, profBronze, profArgent);

        // Act
        Map<String, List<PlayerProfile>> grouped = RushEloCalculator.groupProfilesByTier(profiles);

        // Assert
        assertEquals(1, grouped.get("fer").size());
        assertEquals(1, grouped.get("bronze").size());
        assertEquals(1, grouped.get("argent").size());
        assertEquals(0, grouped.get("or").size());
        assertEquals(0, grouped.get("platine").size());
    }

    @Test
    public void testCalculateIntraRankEloChangesZeroSumAndTies() {
        // Arrange
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("P1").rank(5).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("P2").rank(5).build();
        PlayerProfile prof3 = PlayerProfileBuilder.aProfile().uuid(p3).name("P3").rank(5).build();

        List<PlayerProfile> players = Arrays.asList(prof1, prof2, prof3);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 100.0);
        scores.put(p2, 100.0);
        scores.put(p3, 100.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();

        // Act
        RushEloCalculator.calculateIntraRankEloChanges(players, scores, 30.0, eloChanges);

        // Assert
        assertEquals(0, eloChanges.get(p1));
        assertEquals(0, eloChanges.get(p2));
        assertEquals(0, eloChanges.get(p3));
    }

    @Test
    public void testCalculateIntraRankEloChangesMaxScoreNul() {
        // Arrange
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("P1").rank(5).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("P2").rank(5).build();

        List<PlayerProfile> players = Arrays.asList(prof1, prof2);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 0.0);
        scores.put(p2, 0.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();

        // Act
        RushEloCalculator.calculateIntraRankEloChanges(players, scores, 30.0, eloChanges);

        // Assert
        assertEquals(0, eloChanges.get(p1));
        assertEquals(0, eloChanges.get(p2));
    }

    @Test
    public void testCalculateIntraRankEloChangesNormalDistribution() {
        // Arrange
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("P1").rank(5).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("P2").rank(5).build();
        PlayerProfile prof3 = PlayerProfileBuilder.aProfile().uuid(p3).name("P3").rank(5).build();

        List<PlayerProfile> players = Arrays.asList(prof1, prof2, prof3);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 1000.0);
        scores.put(p2, 500.0);
        scores.put(p3, 0.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();

        // Act
        RushEloCalculator.calculateIntraRankEloChanges(players, scores, 30.0, eloChanges);

        // Assert
        assertEquals(15, eloChanges.get(p1));
        assertEquals(0, eloChanges.get(p2));
        assertEquals(-15, eloChanges.get(p3));
    }

    @Test
    public void testCalculateOrphanEloChanges() {
        // Arrange
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("P1").rank(5).build();
        PlayerProfile prof2 = PlayerProfileBuilder.aProfile().uuid(p2).name("P2").rank(15).build();

        List<PlayerProfile> orphans = Arrays.asList(prof1, prof2);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 500.0);
        scores.put(p2, 1000.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();
        Map<String, RushEloCalculator.TierSettings> settingsMap = new HashMap<>();
        settingsMap.put("fer", new RushEloCalculator.TierSettings(30.0, 1.5, 0.5));
        settingsMap.put("bronze", new RushEloCalculator.TierSettings(30.0, 1.0, 1.0));

        // Act
        RushEloCalculator.calculateOrphanEloChanges(orphans, scores, eloChanges, settingsMap);

        // Assert
        assertEquals(-4, eloChanges.get(p1));
        assertEquals(8, eloChanges.get(p2));
    }

    @Test
    public void testCalculateOrphanSinglePlayer() {
        // Arrange
        UUID p1 = UUID.randomUUID();
        PlayerProfile prof1 = PlayerProfileBuilder.aProfile().uuid(p1).name("P1").rank(5).build();

        List<PlayerProfile> orphans = Collections.singletonList(prof1);
        Map<UUID, Double> scores = new HashMap<>();
        scores.put(p1, 500.0);

        Map<UUID, Integer> eloChanges = new HashMap<>();

        // Act
        RushEloCalculator.calculateOrphanEloChanges(orphans, scores, eloChanges, Collections.emptyMap());

        // Assert
        assertEquals(0, eloChanges.get(p1));
    }
}
