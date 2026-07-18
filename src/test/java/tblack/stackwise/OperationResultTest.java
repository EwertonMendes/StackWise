package tblack.stackwise;

import org.junit.jupiter.api.Test;
import tblack.stackwise.stack.StackApplyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationResultTest {
    @Test
    void successfulRuntimeApplyWarnsWhenReductionsNeedRestart() {
        StackApplyReport report = new StackApplyReport();
        report.restartRequired = 7;

        OperationResult result = OperationResult.success(
                "messages.save_success",
                null,
                report,
                10,
                10,
                3,
                0
        );

        String translated = result.translated("en-US");
        assertTrue(translated.contains("Configuration saved"));
        assertTrue(translated.contains("7 stack limits"));
        assertTrue(translated.contains("server restarts"));
    }

    @Test
    void successfulRuntimeApplyDoesNotAddRestartWarningWhenEverythingApplied() {
        StackApplyReport report = new StackApplyReport();

        OperationResult result = OperationResult.success(
                "messages.save_success",
                null,
                report,
                10,
                10,
                10,
                0
        );

        assertFalse(result.translated("en-US").contains("server restarts"));
    }
}
