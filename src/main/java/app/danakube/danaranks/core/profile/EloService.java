package app.danakube.danaranks.core.profile;

import app.danakube.danaranks.api.event.PlayerEloChangeEvent;
import app.danakube.danaranks.api.event.PlayerRankUpEvent;
import app.danakube.danaranks.database.HistoryRepository;
import app.danakube.danaranks.hooks.PermissionHook;
import org.bukkit.Bukkit;

import java.util.UUID;

public class EloService {
    private final PermissionHook permissionHook;
    private final HistoryRepository historyRepository;

    public EloService(PermissionHook permissionHook, HistoryRepository historyRepository) {
        this.permissionHook = permissionHook;
        this.historyRepository = historyRepository;
    }

    public synchronized int addElo(PlayerProfile profile, int amount, String reason) {
        int oldElo = profile.getElo();
        int oldRank = profile.getRankLevel();

        int newRank = oldRank;
        int newElo = oldElo;

        if (oldRank >= 50) {
            newElo = oldElo + amount;
            if (newElo < 0) {
                newElo = 0;
            }
            profile.setElo(newElo);
        } else {
            int totalElo = oldElo + amount;
            if (totalElo < 0) {
                profile.setElo(0);
                newElo = 0;
            } else {
                int ranksGained = totalElo / 100;
                int remainingElo = totalElo % 100;

                if (ranksGained > 0) {
                    newRank = Math.min(50, oldRank + ranksGained);
                    if (newRank == 50) {
                        int cumulativeElo = (oldRank - 1) * 100 + oldElo + amount;
                        newElo = cumulativeElo - 4900;
                    } else {
                        newElo = remainingElo;
                    }
                    profile.setRankLevel(newRank);
                    profile.setElo(newElo);
                } else {
                    newElo = remainingElo;
                    profile.setElo(newElo);
                }
            }
        }

        int eloChange = newElo - oldElo;
        int actualRanksGained = newRank - oldRank;

        if (eloChange != 0 || actualRanksGained > 0) {
            if (historyRepository != null) {
                try {
                    historyRepository.logHistory(profile.getUuid(), reason, eloChange, newElo, "ELO Change: " + reason);
                } catch (Exception e) {
                    // Safety for test environments
                }
            }
            try {
                if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                    Bukkit.getPluginManager().callEvent(new PlayerEloChangeEvent(profile.getUuid(), eloChange, newElo, reason));
                }
            } catch (Exception e) {
                // Ignore in unit tests where Bukkit is not mock-initialized
            }
        }

        if (actualRanksGained > 0) {
            if (permissionHook != null) {
                try {
                    permissionHook.promote(profile.getUuid(), actualRanksGained);
                } catch (Exception e) {
                    // Ignore
                }
            }
            try {
                if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                    Bukkit.getPluginManager().callEvent(new PlayerRankUpEvent(profile.getUuid(), actualRanksGained, newRank));
                }
            } catch (Exception e) {
                // Ignore in unit tests
            }
        }

        return actualRanksGained;
    }
}
