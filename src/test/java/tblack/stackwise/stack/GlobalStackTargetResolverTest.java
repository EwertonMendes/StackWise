package tblack.stackwise.stack;

import org.junit.jupiter.api.Test;
import tblack.stackwise.config.GlobalStackMode;
import tblack.stackwise.config.StackWiseConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalStackTargetResolverTest {
    private final GlobalStackTargetResolver resolver = new GlobalStackTargetResolver();

    @Test
    void fixedModeReturnsTheConfiguredLimit() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMode = GlobalStackMode.FIXED;
        config.globalStackLimit = 1000;

        assertEquals(1000, resolver.resolve(config, 10));
    }

    @Test
    void multiplierUsesTheBaselineAndRoundsDown() {
        StackWiseConfig config = multiplier(1.5D, 999);

        assertEquals(198, resolver.resolve(multiplier(2.0D, 999), 99));
        assertEquals(20, resolver.resolve(multiplier(2.0D, 999), 10));
        assertEquals(37, resolver.resolve(config, 25));
    }

    @Test
    void multiplierNeverExceedsItsCap() {
        assertEquals(999, resolver.resolve(multiplier(20.0D, 999), 100));
    }

    private StackWiseConfig multiplier(double multiplier, int cap) {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMode = GlobalStackMode.MULTIPLIER;
        config.globalStackMultiplier = multiplier;
        config.globalStackCap = cap;
        return config;
    }
}
