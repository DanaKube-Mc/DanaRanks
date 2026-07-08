package app.danakube.danaranks.features.quota;

public record RankBracket(
        int minRank,
        int maxRank,
        int baseElo,
        int maxSurplusElo,
        int failPenalty,
        int maxObjectives
) {
    public boolean contains(int rank) {
        return rank >= minRank && rank <= maxRank;
    }
}
