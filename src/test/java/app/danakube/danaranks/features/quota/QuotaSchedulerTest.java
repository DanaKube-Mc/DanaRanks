package app.danakube.danaranks.features.quota;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class QuotaSchedulerTest {

    @Test
    public void testResetCalculation() {
        QuotaScheduler scheduler = new QuotaScheduler("2026-07-03", 4);

        Instant now = Instant.parse("2026-07-04T12:00:00Z");
        Instant nextReset = scheduler.getNextResetInstant(3, now);

        Instant expected = LocalDateTime.of(2026, 7, 6, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        assertEquals(expected, nextReset);
    }
    
    @Test
    public void testPeriodDaysByLevel() {
        QuotaScheduler scheduler = new QuotaScheduler("2026-07-03", 4);
        assertEquals(1, scheduler.getPeriodDays(1));
        assertEquals(2, scheduler.getPeriodDays(2));
        assertEquals(3, scheduler.getPeriodDays(3));
        assertEquals(4, scheduler.getPeriodDays(4));
        assertEquals(5, scheduler.getPeriodDays(5));
        assertEquals(1, scheduler.getPeriodDays(6)); // Default fallback
    }
}
