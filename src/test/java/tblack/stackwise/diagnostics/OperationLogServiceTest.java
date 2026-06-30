package tblack.stackwise.diagnostics;

import org.junit.jupiter.api.Test;
import tblack.stackwise.OperationResult;
import tblack.stackwise.config.ValidationResult;
import tblack.stackwise.stack.StackApplyReport;
import tblack.stackwise.stack.StackChange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationLogServiceTest {
    @Test
    void reportFailureMakesTheOperationHeaderAnError() {
        StackApplyReport report = new StackApplyReport();
        report.adapterAvailable = true;
        report.failures = 1;
        report.addChange(new StackChange("Broken_Item", 100, 100, 1000, 100, "broken", "failure", "IllegalStateException"));
        OperationResult result = OperationResult.success(
                "messages.reload_success",
                new ValidationResult(),
                report,
                1,
                1,
                0,
                1
        );
        OperationLogService service = new OperationLogService();

        service.record(result);

        List<OperationLogEntry> entries = service.entries();
        assertEquals(LogSeverity.ERROR, entries.getFirst().severity());
        assertTrue(entries.stream().anyMatch(entry -> entry instanceof ChangeLogEntry && entry.severity() == LogSeverity.ERROR));
    }

    @Test
    void blockedChangesMakeTheOperationHeaderAWarning() {
        StackApplyReport report = new StackApplyReport();
        report.adapterAvailable = true;
        report.restartRequired = 1;
        report.addChange(new StackChange("Ingredient_Stick", 100, 1000, 100, 1000, "sticks", "restart-required"));
        OperationLogService service = new OperationLogService();

        service.recordReport("messages.assets_applied", report, 1, 1, 0, 0);

        assertEquals(LogSeverity.WARNING, service.entries().getFirst().severity());
    }

    @Test
    void cappedReportsRetainLateFailures() {
        StackApplyReport report = new StackApplyReport();
        for (int index = 0; index < 1000; index++) {
            report.addChange(new StackChange("Item_" + index, 100, 100, 100, 100, null, "restart-required"));
        }
        report.addChange(new StackChange("Late_Failure", 0, 0, 0, 0, null, "failure", "boom"));

        assertEquals(1000, report.changes().size());
        assertTrue(report.changes().stream().anyMatch(change -> change.itemId().equals("Late_Failure")));
    }

    @Test
    void reportSummaryIsNotDuplicatedInsideTheDetailedLog() {
        StackApplyReport report = new StackApplyReport();
        report.adapterAvailable = true;
        OperationLogService service = new OperationLogService();

        service.recordReport("messages.assets_applied", report, 10, 5, 2, 0);

        long summaries = service.entries().stream()
                .filter(entry -> entry instanceof MessageLogEntry message && message.messageKey().equals("messages.report"))
                .count();
        assertEquals(0, summaries);
        assertEquals(1, service.entries().size());
    }

    @Test
    void playerFacingFailureDoesNotExposeTechnicalDetails() {
        ChangeLogEntry entry = new ChangeLogEntry(
                LogSeverity.ERROR,
                new StackChange("Broken_Item", 0, 0, 0, 0, null, "failure", "IllegalStateException: secret")
        );

        String translated = entry.translated("en-US");

        assertTrue(translated.contains("Broken_Item"));
        assertTrue(!translated.contains("IllegalStateException"));
        assertTrue(!translated.contains("secret"));
    }
}
