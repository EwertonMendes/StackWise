package tblack.stackwise.diagnostics;

import tblack.stackwise.i18n.I18n;
import tblack.stackwise.stack.StackChange;

public record ChangeLogEntry(LogSeverity severity, StackChange change) implements OperationLogEntry {
    @Override
    public String translated(String locale) {
        String outcome = change.outcome() == null ? "" : change.outcome();
        if (outcome.startsWith("failure")) {
            return I18n.translate(locale, "ui.log.item_failure", change.itemId());
        }
        if (outcome.equals("verification-failed")) {
            return I18n.translate(
                    locale,
                    "ui.log.verification_failed",
                    change.itemId(),
                    change.requested(),
                    change.applied()
            );
        }
        if (outcome.startsWith("unsafe-blocked:")) {
            return I18n.translate(locale, "ui.log.unsafe_blocked", change.itemId());
        }
        if (outcome.equals("below-original-blocked")) {
            return I18n.translate(
                    locale,
                    "ui.log.below_original_blocked",
                    change.itemId(),
                    change.requested(),
                    change.original()
            );
        }
        if (outcome.equals("restart-required")) {
            return I18n.translate(
                    locale,
                    "ui.log.restart_required",
                    change.itemId(),
                    change.previous(),
                    change.requested()
            );
        }
        if (outcome.equals("external-conflict")) {
            return I18n.translate(locale, "ui.log.external_conflict", change.itemId());
        }
        if (outcome.equals("changed")) {
            return I18n.translate(
                    locale,
                    "ui.log.item_changed",
                    change.itemId(),
                    change.previous(),
                    change.applied(),
                    change.ruleId() == null ? "-" : change.ruleId()
            );
        }
        return I18n.translate(locale, "ui.log.item_outcome", change.itemId(), outcome);
    }
}
