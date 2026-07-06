package app.danakube.danaranks.features.rush;

import java.util.UUID;

public class RushScoreTracker {
    private final RushEventState state;

    public RushScoreTracker(RushEventState state) {
        this.state = state;
    }

    public void registerPlayer(UUID uuid) {
        state.getRegisteredScores().put(uuid, 0.0);
    }

    public boolean isRegistered(UUID uuid) {
        return state.getRegisteredScores().containsKey(uuid);
    }

    public double getScore(UUID uuid) {
        return state.getRegisteredScores().getOrDefault(uuid, 0.0);
    }

    public void incrementScore(UUID uuid, double amount) {
        state.getRegisteredScores().computeIfPresent(uuid, (k, current) -> current + amount);
    }

    public void clear() {
        state.getRegisteredScores().clear();
    }
}
