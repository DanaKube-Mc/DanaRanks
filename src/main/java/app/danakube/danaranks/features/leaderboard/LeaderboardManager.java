package app.danakube.danaranks.features.leaderboard;

import app.danakube.danaranks.database.ProfileRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LeaderboardManager {
    private final ProfileRepository profileRepository;
    private final List<LeaderboardEntry> cachedLeaderboard = new CopyOnWriteArrayList<>();

    public LeaderboardManager(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public void updateLeaderboard() {
        if (profileRepository == null) return;
        profileRepository.getLeaderboard(50).thenAccept(list -> {
            cachedLeaderboard.clear();
            cachedLeaderboard.addAll(list);
        }).exceptionally(ex -> {
            // Log or ignore
            return null;
        });
    }

    public List<LeaderboardEntry> getCachedLeaderboard() {
        return Collections.unmodifiableList(cachedLeaderboard);
    }
}
