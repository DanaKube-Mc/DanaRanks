package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.core.profile.PlayerProfileBuilder;
import app.danakube.danaranks.core.profile.EloService;
import app.danakube.danaranks.hooks.PermissionHook;
import app.danakube.danaranks.database.HistoryRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class QuotaServiceTest {

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
    public void testImmediateBaseEloReward() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.max-surplus-elo", 10);
        qm.loadConfig(config, null);

        PlayerProfile profile = PlayerProfileBuilder.aProfile().name("Player1").rank(1).elo(0).build();
        progressTracker.resetQuotaProgress(profile, 1);

        // Act & Assert 1
        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 999);
        assertEquals(0, profile.getElo());
        assertFalse(progressTracker.isBaseRewarded(profile, "lumens_gained"));

        // Act & Assert 2
        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 1);
        assertEquals(5, profile.getElo());
        assertTrue(progressTracker.isBaseRewarded(profile, "lumens_gained"));

        // Act & Assert 3
        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 500);
        assertEquals(5, profile.getElo());
    }

    @Test
    public void testSurplusCalculationAtReset() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.max-surplus-elo", 10);
        config.set("quotas-settings.surplus-multiplier", 10.0);
        qm.loadConfig(config, null);

        PlayerProfile profile = PlayerProfileBuilder.aProfile().name("Player2").rank(1).elo(0).build();
        progressTracker.resetQuotaProgress(profile, 1);

        // Act
        progressTracker.incrementProgress(profile, qm.getQuotaConfig(), "lumens_gained", 5500);
        assertEquals(5, profile.getElo());
        
        qm.processGlobalReset(profile, Instant.now());

        // Assert
        assertEquals(8, profile.getElo());
    }

    @Test
    public void testNoQuotaReloadOnRankUp() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.scaling.multiplier-per-rank", 1.15);
        config.set("quotas-settings.base-rank-1.objectives.lumens-gained.target", 1000);
        qm.loadConfig(config, null);

        PlayerProfile profile = PlayerProfileBuilder.aProfile().name("Player3").rank(4).elo(90).build();
        progressTracker.resetQuotaProgress(profile, 4);

        // Act & Assert 1
        double targetRank4 = QuotaConfigLoader.getObjectiveConfig(qm.getQuotaConfig(), 4, "lumens_gained").target();
        assertEquals(1521.0, targetRank4);

        // Act & Assert 2
        eloService.addElo(profile, 20, "TEST");
        assertEquals(5, profile.getRankLevel());

        // Act & Assert 3
        int activeRank = progressTracker.getActiveQuotaRank(profile);
        assertEquals(4, activeRank);
        assertEquals(1521.0, QuotaConfigLoader.getObjectiveConfig(qm.getQuotaConfig(), activeRank, "lumens_gained").target());
    }

    @Test
    public void testProportionalLoss() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("quotas-settings.base-rank-1.objectives.job-xp.target", 1000);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.base-elo", 5);
        config.set("quotas-settings.base-rank-1.objectives.job-xp.fail-penalty", 10);
        qm.loadConfig(config, null);

        PlayerProfile profile1 = PlayerProfileBuilder.aProfile().name("Player4").rank(21).elo(50).build();
        progressTracker.resetQuotaProgress(profile1, 1);
        progressTracker.setActiveQuotaRank(profile1, 1);

        PlayerProfile profile2 = PlayerProfileBuilder.aProfile().name("Player4_2").rank(21).elo(50).build();
        progressTracker.resetQuotaProgress(profile2, 1);
        progressTracker.setActiveQuotaRank(profile2, 1);

        // Act
        progressTracker.incrementProgress(profile1, qm.getQuotaConfig(), "job_xp", 650);
        qm.processGlobalReset(profile1, Instant.now());

        progressTracker.incrementProgress(profile2, qm.getQuotaConfig(), "job_xp", 660);
        qm.processGlobalReset(profile2, Instant.now());

        // Assert
        assertEquals(46, profile1.getElo());
        assertEquals(47, profile2.getElo());
    }

    @Test
    public void testConfigValidation() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        QuotaService qm = new QuotaService(eloService, progressTracker);
        
        YamlConfiguration config = new YamlConfiguration();
        config.set("reset.hour", 25);
        config.set("reset.reference-date", "invalid-date-format");
        config.set("quotas-settings.surplus-multiplier", -2.5);
        config.set("quotas-settings.scaling.multiplier-per-rank", 0.8);

        // Act
        qm.loadConfig(config, null);

        // Assert
        assertEquals(4, qm.getQuotaConfig().resetHour());
        assertEquals("2026-07-03", qm.getQuotaConfig().refDateStr());
        assertEquals(10.0, qm.getQuotaConfig().surplusMultiplier());
        assertEquals(1.15, qm.getQuotaConfig().scalingMultiplierPerRank());
    }

    @Test
    public void testOfflineCatchUpMultipleCycles() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
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

        PlayerProfile profile = PlayerProfileBuilder.aProfile().name("PlayerOffline").rank(21).elo(25).build();
        
        Instant ref = LocalDateTime.of(2026, 7, 3, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        profile.setLastReset(ref);
        progressTracker.resetQuotaProgress(profile, 1);

        Instant now = ref.plusSeconds(15L * 86400);

        // Act
        qm.handleOfflineCatchUp(profile, now);

        // Assert
        assertEquals(21, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }

    @Test
    public void testRank50PrestigeAndDecay() {
        // Arrange
        EloService eloService = new EloService(stubPerms, stubHistory);
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

        PlayerProfile profile = PlayerProfileBuilder.aProfile().name("PrestigePlayer").rank(50).elo(90).build();
        eloService.addElo(profile, 150, "TEST");
        assertEquals(50, profile.getRankLevel());
        assertEquals(240, profile.getElo());

        Instant ref = LocalDateTime.of(2026, 7, 3, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        profile.setLastReset(ref);
        progressTracker.resetQuotaProgress(profile, 1);

        Instant now = ref.plusSeconds(100L * 86400);

        // Act & Assert 1
        qm.handleOfflineCatchUp(profile, now);
        assertEquals(50, profile.getRankLevel());
        assertEquals(40, profile.getElo());
        
        // Act & Assert 2
        profile.setElo(50);
        profile.setLastReset(ref);
        progressTracker.resetQuotaProgress(profile, 1);
        
        qm.handleOfflineCatchUp(profile, now);
        assertEquals(50, profile.getRankLevel());
        assertEquals(0, profile.getElo());
    }
}
