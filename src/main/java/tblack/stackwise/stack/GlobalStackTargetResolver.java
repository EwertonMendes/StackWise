package tblack.stackwise.stack;

import tblack.stackwise.config.GlobalStackMode;
import tblack.stackwise.config.StackWiseConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GlobalStackTargetResolver {
    public int resolve(StackWiseConfig config, int baseline) {
        if (config.globalStackMode != GlobalStackMode.MULTIPLIER) {
            return config.globalStackLimit;
        }

        BigDecimal multiplied = BigDecimal.valueOf(baseline)
                .multiply(BigDecimal.valueOf(config.globalStackMultiplier))
                .setScale(0, RoundingMode.FLOOR);
        return multiplied
                .min(BigDecimal.valueOf(config.globalStackCap))
                .intValueExact();
    }
}
