package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.core.profile.EloService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QuotaServiceTest {

    @Test
    public void testImmediateBaseEloReward() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.max-surplus-elo", 10);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player1", 1, 0, Instant.now(), new HashMap<>());
        progressTracker.resetQuotaProgress(profile, 1);

        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 999);
        assertEquals(0, profile.getElo());
        assertFalse(progressTracker.isBaseRewarded(profile, "lumens_gained"));

        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 1);
        assertEquals(5, profile.getElo());
        assertTrue(progressTracker.isBaseRewarded(profile, "lumens_gained"));

        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 500);
        assertEquals(5, profile.getElo());
    }

    @Test
    public void testSurplusCalculationAtReset() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.max-surplus-elo", 10);
        config.set("quotas-settings.surplus-multiplier", 10.0);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player2", 1, 0, Instant.now(), new HashMap<>());
        progressTracker.resetQuotaProgress(profile, 1);

        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 5500);
        assertEquals(5, profile.getElo());
        
        qm.processGlobalReset(profile, Instant.now());
        assertEquals(8, profile.getElo());
    }

    @Test
    public void testNoQuotaReloadOnRankUp() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.scaling.multiplier-per-rank", 1.15);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player3", 4, 90, Instant.now(), new HashMap<>());
        progressTracker.resetQuotaProgress(profile, 4);

        double targetRank4 = QuotaConfigLoader.getObjectiveConfig(qm.getQuotaConfig(), 4, "lumens_gained").target();
        assertEquals(1521.0, targetRank4);

        eloService.addElo(profile, 20, "TEST");
        assertEquals(5, profile.getRankLevel());

        int activeRank = progressTracker.getActiveQuotaRank(profile);
        assertEquals(4, activeRank);
        assertEquals(1521.0, QuotaConfigLoader.getObjectiveConfig(qm.getQuotaConfig(), activeRank, "lumens_gained").target());
    }

    @Test
    public void testProportionalLoss() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.job-xp.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.fail-penalty", 10);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player4", 21, 50, Instant.now(), new HashMap<>());
        
        progressTracker.resetQuotaProgress(profile, 1);
        progressTracker.setActiveQuotaRank(profile, 1);
        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "job_xp", 650);
        qm.processGlobalReset(profile, Instant.now());
        assertEquals(46, profile.getElo());

        PlayerProfile profile2 = new PlayerProfile(UUID.randomUUID(), "Player4_2", 21, 50, Instant.now(), new HashMap<>());
        progressTracker.resetQuotaProgress(profile2, 1);
        progressTracker.setActiveQuotaRank(profile2, 1);
        progressTracker.incrementProgress(profile2, qm.getQuotaConfig(), "job_xp", 660);
        qm.processGlobalReset(profile2, Instant.now());
        assertEquals(47, profile2.getElo());
    }

    @Test
    public void testConfigValidation() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.hour", 25);
        config.set("reset.reference-date", "invalid-date-format");
        config.set("quotas-settings.surplus-multiplier", -2.5);
        config.set("quotas-settings.scaling.multiplier-per-rank", 0.8);

        qm.loadConfig(config, null);

        assertEquals(4, qm.getQuotaConfig().resetHour());
        assertEquals("2026-07-03", qm.getQuotaConfig().refDateStr());
        assertEquals(10.0, qm.getQuotaConfig().surplusMultiplier());
        assertEquals(1.15, qm.getQuotaConfig().scalingMultiplierPerRank());
    }

    @Test
    public void testOfflineCatchUpMultipleCycles() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
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
        progressTracker.resetQuotaProgress(profile, 1);

        Instant now = ref.plusSeconds(15L * 86400);

        qm.handleOfflineCatchUp(profile, now);
        assertEquals(21, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }

    @Test
    public void testRank50PrestigeAndDecay() {
        EloService eloService = new EloService(null, null);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.reference-date", "2026-07-03");
        config.set("reset.hour", 4);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.fail-penalty", 5);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.target", 500);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.fail-penalty", 5);
        qm.loadConfig(config, null);

        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "PrestigePlayer", 50, 90, Instant.now(), new HashMap<>());
        eloService.addElo(profile, 150, "TEST");
        assertEquals(50, profile.getRankLevel());
        assertEquals(240, profile.getElo());

        Instant ref = LocalDateTime.of(2026, 7, 3, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        profile.setLastReset(ref);
        progressTracker.resetQuotaProgress(profile, 1);

        Instant now = ref.plusSeconds(100L * 86400);

        qm.handleOfflineCatchUp(profile, now);
        assertEquals(50, profile.getRankLevel());
        assertEquals(40, profile.getElo());
        
        profile.setElo(50);
        profile.setLastReset(ref);
        progressTracker.resetQuotaProgress(profile, 1);
        
        qm.handleOfflineCatchUp(profile, now);
        assertEquals(50, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }
}
