package app.danakube.danaranks.features.rush;

public record RushSessionConfig(
        String name,
        int minStartHour,
        int maxStartHour,
        int minDurationMinutes,
        int maxDurationMinutes
) {}
