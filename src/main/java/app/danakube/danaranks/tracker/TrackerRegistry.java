package app.danakube.danaranks.tracker;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TrackerRegistry {
    private final JavaPlugin plugin;
    private final Map<String, ResourceTracker> trackers = new HashMap<>();

    public TrackerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerTracker(ResourceTracker tracker) {
        trackers.put(tracker.getResourceName(), tracker);
        Bukkit.getPluginManager().registerEvents(tracker, plugin);
    }

    public ResourceTracker getTracker(String resourceName) {
        return trackers.get(resourceName);
    }

    public Collection<ResourceTracker> getTrackers() {
        return trackers.values();
    }
}
