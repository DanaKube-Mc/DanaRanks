package app.danakube.danaranks.features.rush;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

public class RushScheduler {

    public static void planNextRush(
        RushEventState state,
        List<String> eligibleResources,
        int minStartHour,
        int maxStartHour,
        int minDurationMinutes,
        int maxDurationMinutes,
        LocalDateTime now
    ) {
        if (eligibleResources.isEmpty()) return;

        Random rand = new Random();
        String resource = eligibleResources.get(rand.nextInt(eligibleResources.size()));

        int hour = minStartHour + rand.nextInt(Math.max(1, maxStartHour - minStartHour + 1));
        int minute = rand.nextInt(60);
        LocalDateTime startTime = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        int duration = minDurationMinutes + rand.nextInt(Math.max(1, maxDurationMinutes - minDurationMinutes + 1));

        state.clear();
        state.setDailyResource(resource);
        state.setStartTime(startTime);
        state.setDurationMinutes(duration);
        state.setDailyPlanned(true);
        state.setLastPlannedDate(now.toLocalDate());
        state.setRegistrationOpen(true);
    }
}
