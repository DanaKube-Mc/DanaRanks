package app.danakube.danaranks.core.profile;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileCache {
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public Optional<PlayerProfile> getProfile(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public void putProfile(PlayerProfile profile) {
        if (profile != null && profile.getUuid() != null) {
            cache.put(profile.getUuid(), profile);
        }
    }

    public Optional<PlayerProfile> removeProfile(UUID uuid) {
        return Optional.ofNullable(cache.remove(uuid));
    }

    public void clear() {
        cache.clear();
    }

    public Map<UUID, PlayerProfile> getBackingMap() {
        return cache;
    }
}
