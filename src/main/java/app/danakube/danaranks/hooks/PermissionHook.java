package app.danakube.danaranks.hooks;

import java.util.UUID;

public interface PermissionHook {
    void promote(UUID uuid, int ranksGained);
    void demote(UUID uuid, int ranksLost);
}
