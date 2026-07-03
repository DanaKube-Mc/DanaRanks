package app.danakube.danaranks;

public class ObjectiveConfig {
    private final String name;
    private final double target;
    private final int baseElo;
    private final int maxSurplusElo;
    private final int failPenalty;

    public ObjectiveConfig(String name, double target, int baseElo, int maxSurplusElo, int failPenalty) {
        this.name = name;
        this.target = target;
        this.baseElo = baseElo;
        this.maxSurplusElo = maxSurplusElo;
        this.failPenalty = failPenalty;
    }

    public String getName() {
        return name;
    }

    public double getTarget() {
        return target;
    }

    public int getBaseElo() {
        return baseElo;
    }

    public int getMaxSurplusElo() {
        return maxSurplusElo;
    }

    public int getFailPenalty() {
        return failPenalty;
    }
}
