package tblack.stackwise.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCardLayoutTest {
    @Test
    void shortDescriptionsReserveSpaceForFourLines() {
        RuleCardLayout.Dimensions dimensions = RuleCardLayout.measure("Short rule");
        assertEquals(80, dimensions.descriptionHeight());
        assertEquals(108, dimensions.textHeight());
        assertEquals(116, dimensions.rowHeight());
    }

    @Test
    void longDescriptionsGrowInsteadOfOverflowing() {
        String description = "A very long rule description ".repeat(30);
        RuleCardLayout.Dimensions dimensions = RuleCardLayout.measure(description);
        assertTrue(dimensions.descriptionHeight() > 80);
        assertEquals(dimensions.descriptionHeight() + 28, dimensions.textHeight());
        assertEquals(dimensions.textHeight() + 8, dimensions.rowHeight());
    }

    @Test
    void explicitLineBreaksIncreaseTheMeasuredHeight() {
        RuleCardLayout.Dimensions dimensions = RuleCardLayout.measure("one\ntwo\nthree\nfour\nfive");
        assertEquals(100, dimensions.descriptionHeight());
    }
}
