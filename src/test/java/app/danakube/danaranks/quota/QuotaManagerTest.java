package app.danakube.danaranks.quota;

import app.danakube.danaranks.profile.PlayerProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QuotaManagerTest {

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
