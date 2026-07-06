package app.danakube.danaranks.features.quota;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public class QuotaScheduler {
    private final String refDateStr;
    private final int resetHour;

    public QuotaScheduler(String refDateStr, int resetHour) {
        this.refDateStr = refDateStr;
        this.resetHour = resetHour;
    }

    public int getPeriodDays(int level) {
        switch (level) {
            case 1: return 1;
            case 2: return 2;
            case 3: return 3;
            case 4: return 4;
            case 5: return 5;
            default: return 1;
        }
    }

    public Instant getNextResetInstant(int periodDays, Instant now) {
        LocalDateTime refDateTime = LocalDateTime.of(LocalDate.parse(refDateStr), LocalTime.of(resetHour, 0));
        Instant refInstant = refDateTime.atZone(ZoneId.systemDefault()).toInstant();

        long secondsBetween = now.getEpochSecond() - refInstant.getEpochSecond();
        long joursEcoules;
        if (secondsBetween < 0) {
            joursEcoules = (long) Math.floor((double) secondsBetween / 86400.0);
        } else {
            joursEcoules = secondsBetween / 86400;
        }

        long prochainResetJours;
        if (joursEcoules < 0) {
            prochainResetJours = ((joursEcoules / periodDays)) * periodDays;
        } else {
            prochainResetJours = ((joursEcoules / periodDays) + 1) * periodDays;
        }

        return refInstant.plusSeconds(prochainResetJours * 86400);
    }

    public Instant getLastResetEffectiveInstant(int level, Instant now) {
        int periodDays = getPeriodDays(level);
        LocalDateTime refDateTime = LocalDateTime.of(LocalDate.parse(refDateStr), LocalTime.of(resetHour, 0));
        Instant refInstant = refDateTime.atZone(ZoneId.systemDefault()).toInstant();

        long secondsBetween = now.getEpochSecond() - refInstant.getEpochSecond();
        long joursEcoules;
        if (secondsBetween < 0) {
            joursEcoules = (long) Math.floor((double) secondsBetween / 86400.0);
        } else {
            joursEcoules = secondsBetween / 86400;
        }

        long dernierResetJours;
        if (joursEcoules < 0) {
            dernierResetJours = ((joursEcoules / periodDays) - 1) * periodDays;
        } else {
            dernierResetJours = (joursEcoules / periodDays) * periodDays;
        }

        return refInstant.plusSeconds(dernierResetJours * 86400);
    }

    public int getMissedCycles(int level, Instant lastReset, Instant now) {
        int periodDays = getPeriodDays(level);
        LocalDateTime refDateTime = LocalDateTime.of(LocalDate.parse(refDateStr), LocalTime.of(resetHour, 0));
        Instant refInstant = refDateTime.atZone(ZoneId.systemDefault()).toInstant();

        long lastResetSeconds = lastReset.getEpochSecond() - refInstant.getEpochSecond();
        long nowSeconds = now.getEpochSecond() - refInstant.getEpochSecond();

        long lastResetDays = lastResetSeconds < 0 ? (long) Math.floor((double) lastResetSeconds / 86400.0) : lastResetSeconds / 86400;
        long nowDays = nowSeconds < 0 ? (long) Math.floor((double) nowSeconds / 86400.0) : nowSeconds / 86400;

        long kLast = lastResetDays / periodDays;
        long kNow = nowDays / periodDays;

        if (lastResetDays < 0) {
            kLast = (long) Math.floor((double) lastResetDays / periodDays);
        }
        if (nowDays < 0) {
            kNow = (long) Math.floor((double) nowDays / periodDays);
        }

        return (int) Math.max(0, kNow - kLast);
    }
}
