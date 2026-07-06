package app.danakube.danaranks.features.rush;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RushEventState {
    private boolean dailyPlanned = false;
    private LocalDate lastPlannedDate = null;
    private String dailyResource = null;
    private LocalDateTime startTime = null;
    private int durationMinutes = 0;
    private boolean registrationOpen = false;
    private boolean rushActive = false;
    private boolean discordAnnounced = false;
    private final Map<UUID, Double> registeredScores = new ConcurrentHashMap<>();

    public boolean isDailyPlanned() {
        return dailyPlanned;
    }

    public void setDailyPlanned(boolean dailyPlanned) {
        this.dailyPlanned = dailyPlanned;
    }

    public LocalDate getLastPlannedDate() {
        return lastPlannedDate;
    }

    public void setLastPlannedDate(LocalDate lastPlannedDate) {
        this.lastPlannedDate = lastPlannedDate;
    }

    public String getDailyResource() {
        return dailyResource;
    }

    public void setDailyResource(String dailyResource) {
        this.dailyResource = dailyResource;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    public void setRegistrationOpen(boolean registrationOpen) {
        this.registrationOpen = registrationOpen;
    }

    public boolean isRushActive() {
        return rushActive;
    }

    public void setRushActive(boolean rushActive) {
        this.rushActive = rushActive;
    }

    public boolean isDiscordAnnounced() {
        return discordAnnounced;
    }

    public void setDiscordAnnounced(boolean discordAnnounced) {
        this.discordAnnounced = discordAnnounced;
    }

    public Map<UUID, Double> getRegisteredScores() {
        return registeredScores;
    }

    public void clear() {
        dailyPlanned = false;
        dailyResource = null;
        startTime = null;
        durationMinutes = 0;
        registrationOpen = false;
        rushActive = false;
        discordAnnounced = false;
        registeredScores.clear();
    }
}
