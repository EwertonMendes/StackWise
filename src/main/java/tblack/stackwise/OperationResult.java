package tblack.stackwise;

import tblack.stackwise.config.ValidationResult;
import tblack.stackwise.i18n.I18n;
import tblack.stackwise.stack.StackApplyReport;

public record OperationResult(
        boolean success,
        String messageKey,
        Object[] messageArgs,
        ValidationResult validation,
        StackApplyReport report
) {
    public OperationResult {
        messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    public static OperationResult success(String messageKey, ValidationResult validation, StackApplyReport report, Object... args) {
        return new OperationResult(true, messageKey, args, validation, report);
    }

    public static OperationResult failure(String messageKey, ValidationResult validation, StackApplyReport report, Object... args) {
        return new OperationResult(false, messageKey, args, validation, report);
    }

    public String translated(String locale) {
        if (!success && validation != null && !validation.isValid()) {
            return I18n.translate(locale, "messages.validation_failed", validation.firstError(locale));
        }
        String translated = I18n.translate(locale, messageKey, messageArgs);
        if (success && report != null && report.restartRequired > 0) {
            translated += " " + I18n.translate(locale, "messages.restart_required", report.restartRequired);
        }
        return translated;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs.clone();
    }
}
