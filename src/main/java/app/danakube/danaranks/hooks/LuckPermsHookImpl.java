package app.danakube.danaranks.hooks;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.track.Track;

import java.util.UUID;

public class LuckPermsHookImpl implements PermissionHook {
    private final String trackName;
    private LuckPerms api;

    public LuckPermsHookImpl(String trackName) {
        this.trackName = trackName != null ? trackName : "danaranks";
        try {
            this.api = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            this.api = null;
        }
    }

    public LuckPermsHookImpl(String trackName, LuckPerms api) {
        this.trackName = trackName != null ? trackName : "danaranks";
        this.api = api;
    }

    @Override
    public void promote(UUID uuid, int ranksGained) {
        if (api == null) {
            return;
        }

        if (ranksGained <= 0) {
            return;
        }

        api.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            Track track = api.getTrackManager().getTrack(trackName);
            if (track != null && user != null) {
                for (int i = 0; i < ranksGained; i++) {
                    track.promote(user, ImmutableContextSet.empty());
                }
                api.getUserManager().saveUser(user).join();
            }
        });
    }

    @Override
    public void demote(UUID uuid, int ranksLost) {
        if (api == null) {
            return;
        }

        if (ranksLost <= 0) {
            return;
        }

        api.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            Track track = api.getTrackManager().getTrack(trackName);
            if (track != null && user != null) {
                for (int i = 0; i < ranksLost; i++) {
                    track.demote(user, ImmutableContextSet.empty());
                }
                api.getUserManager().saveUser(user).join();
            }
        });
    }

    public LuckPerms getApi() {
        return api;
    }
}
