package su.nightexpress.excellentjobs.api.grind;

import java.util.EnumMap;
import java.util.Map;

public class GrindReward {
    private final Map<GrindObjectiveProperty, Double> properties = new EnumMap<>(GrindObjectiveProperty.class);

    public double get(GrindObjectiveProperty property) {
        return this.properties.getOrDefault(property, 0D);
    }

    public GrindReward put(GrindObjectiveProperty property, double value) {
        this.properties.put(property, value);
        return this;
    }
}
