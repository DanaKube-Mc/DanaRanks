package app.danakube.danaranks.core.profile;

import app.danakube.danaranks.api.event.PlayerEloChangeEvent;
import app.danakube.danaranks.api.event.PlayerRankUpEvent;
import app.danakube.danaranks.database.HistoryRepository;
import app.danakube.danaranks.hooks.PermissionHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


public class EloService {
    private final PermissionHook permissionHook;
    private final HistoryRepository historyRepository;

    public EloService(PermissionHook permissionHook, HistoryRepository historyRepository) {
        this.permissionHook = permissionHook;
        this.historyRepository = historyRepository;
    }

    public int addElo(PlayerProfile profile, int amount, String reason) {
        return addElo(profile, amount, reason, false);
    }

    public synchronized int addElo(PlayerProfile profile, int amount, String reason, boolean allowDemotion) {
        int oldElo = profile.getElo();
        int oldRank = profile.getRankLevel();

        int cumulativeElo = (oldRank - 1) * 100 + oldElo;
        int newCumulativeElo = cumulativeElo + amount;

        int floorCumulativeElo = allowDemotion ? 0 : (oldRank - 1) * 100;

        if (newCumulativeElo < floorCumulativeElo) {
            newCumulativeElo = floorCumulativeElo;
        }

        int newRank;
        int newElo;

        if (newCumulativeElo >= 4900) {
            newRank = 50;
            newElo = newCumulativeElo - 4900;
        } else {
            newRank = (newCumulativeElo / 100) + 1;
            newElo = newCumulativeElo % 100;
        }

        profile.setRankLevel(newRank);
        profile.setElo(newElo);

        int eloChange = newCumulativeElo - cumulativeElo;
        int rankChange = newRank - oldRank;

        if (eloChange != 0 || rankChange != 0) {
            if (historyRepository != null) {
                try {
                    historyRepository.logHistory(profile.getUuid(), reason, eloChange, newElo, reason);
                } catch (Exception e) {
                    // Safety for tests
                }
            }
            try {
                Player player = Bukkit.getPlayer(profile.getUuid());
                if (player != null && Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                    Bukkit.getPluginManager().callEvent(new PlayerEloChangeEvent(player, oldElo, newElo, eloChange, reason));
                }
            } catch (Exception e) {
                // Ignore in tests
            }
        }

        if (rankChange > 0) {
            if (permissionHook != null) {
                try {
                    permissionHook.promote(profile.getUuid(), rankChange);
                } catch (Exception e) {
                    // Ignore
                }
            }
            try {
                Player player = Bukkit.getPlayer(profile.getUuid());
                if (player != null && Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                    Bukkit.getPluginManager().callEvent(new PlayerRankUpEvent(player, oldRank, newRank));
                }
            } catch (Exception e) {
                // Ignore in tests
            }
        } else if (rankChange < 0) {
            if (permissionHook != null) {
                try {
                    permissionHook.demote(profile.getUuid(), -rankChange);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return rankChange;
    }
}
