package app.danakube.danaranks.features.rush;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class RushScheduler {

    public static void planDailyRushes(
        RushEventState state,
        List<String> eligibleResources,
        List<RushSessionConfig> sessions,
        LocalDateTime now
    ) {
        if (eligibleResources.isEmpty() || sessions == null || sessions.isEmpty()) return;

        Random rand = new Random();
        List<String> availableResources = new ArrayList<>(eligibleResources);
        Collections.shuffle(availableResources, rand);

        List<PlannedRush> plannedList = new ArrayList<>();
        int resourceIndex = 0;

        for (RushSessionConfig session : sessions) {
            String resource;
            if (resourceIndex < availableResources.size()) {
                resource = availableResources.get(resourceIndex++);
            } else {
                resource = eligibleResources.get(rand.nextInt(eligibleResources.size()));
            }

            int minHour = Math.min(session.minStartHour(), session.maxStartHour());
            int maxHour = Math.max(session.minStartHour(), session.maxStartHour());
            int hour = minHour + rand.nextInt(Math.max(1, maxHour - minHour + 1));
            int minute = rand.nextInt(60);
            LocalDateTime startTime = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

            int minDur = Math.min(session.minDurationMinutes(), session.maxDurationMinutes());
            int maxDur = Math.max(session.minDurationMinutes(), session.maxDurationMinutes());
            int duration = minDur + rand.nextInt(Math.max(1, maxDur - minDur + 1));

            plannedList.add(new PlannedRush(session.name(), resource, startTime, duration));
        }

        plannedList.sort(Comparator.comparing(PlannedRush::getStartTime));

        state.clear();
        state.setDailyRushes(plannedList);
        state.setDailyPlanned(true);
        state.setLastPlannedDate(now.toLocalDate());

        if (!plannedList.isEmpty()) {
            PlannedRush first = plannedList.getFirst();
            state.setCurrentRush(first);
            state.setRegistrationOpen(true);
        }
    }

    public static void planNextRush(
        RushEventState state,
        List<String> eligibleResources,
        int minStartHour,
        int maxStartHour,
        int minDurationMinutes,
        int maxDurationMinutes,
        LocalDateTime now
    ) {
        RushSessionConfig defaultSession = new RushSessionConfig(
            "Default",
            minStartHour,
            maxStartHour,
            minDurationMinutes,
            maxDurationMinutes
        );
        planDailyRushes(state, eligibleResources, List.of(defaultSession), now);
    }
}
