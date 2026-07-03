package app.danakube.danaranks.quota;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class ResetCalculatorTest {

    @Test
    public void testResetCalculation() {
        ResetCalculator calculator = new ResetCalculator("2026-07-03", 4);

        Instant now = Instant.parse("2026-07-04T12:00:00Z");
        Instant nextReset = calculator.getNextResetInstant(3, now);

        Instant expected = LocalDateTime.of(2026, 7, 6, 4, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
        assertEquals(expected, nextReset);
    }
    
    @Test
    public void testPeriodDaysByLevel() {
        ResetCalculator calculator = new ResetCalculator("2026-07-03", 4);
        assertEquals(1, calculator.getPeriodDays(1));
        assertEquals(2, calculator.getPeriodDays(2));
        assertEquals(3, calculator.getPeriodDays(3));
        assertEquals(4, calculator.getPeriodDays(4));
        assertEquals(5, calculator.getPeriodDays(5));
        assertEquals(1, calculator.getPeriodDays(6)); // Default fallback
    }
}
