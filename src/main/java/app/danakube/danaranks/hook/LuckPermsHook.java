package app.danakube.danaranks.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.track.Track;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsHook {
    private final String trackName;
    private LuckPerms api;

    public LuckPermsHook(String trackName) {
        this.trackName = trackName != null ? trackName : "danaranks";
        try {
            this.api = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            this.api = null;
        }
    }

    public LuckPermsHook(String trackName, LuckPerms api) {
        this.trackName = trackName != null ? trackName : "danaranks";
        this.api = api;
    }

    public CompletableFuture<Void> promote(UUID uuid, int ranksGained) {
        if (api == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (ranksGained <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        return api.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            Track track = api.getTrackManager().getTrack(trackName);
            if (track != null && user != null) {
                for (int i = 0; i < ranksGained; i++) {
                    track.promote(user, ImmutableContextSet.empty());
                }
                api.getUserManager().saveUser(user).join();
            }
        });
    }

    public LuckPerms getApi() {
        return api;
    }
}
