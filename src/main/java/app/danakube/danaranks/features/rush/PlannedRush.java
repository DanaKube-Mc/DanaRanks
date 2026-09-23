package app.danakube.danaranks.features.rush;

import java.time.LocalDateTime;

public class PlannedRush {
    private final String sessionName;
    private final String resource;
    private final LocalDateTime startTime;
    private final int durationMinutes;
    private boolean completed;

    public PlannedRush(String sessionName, String resource, LocalDateTime startTime, int durationMinutes) {
        this.sessionName = sessionName;
        this.resource = resource;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.completed = false;
    }

    public String getSessionName() {
        return sessionName;
    }

    public String getResource() {
        return resource;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
