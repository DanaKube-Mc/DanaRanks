package app.danakube.danaranks.features.quota;

public record ObjectiveConfig(
        String name,
        double target,
        int baseElo,
        int maxSurplusElo,
        int failPenalty,
        String material,
        Integer customModelData
) {
    public ObjectiveConfig(String name, double target, int baseElo, int maxSurplusElo, int failPenalty) {
        this(name, target, baseElo, maxSurplusElo, failPenalty, null, null);
    }
}
