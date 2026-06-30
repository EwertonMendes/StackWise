package tblack.stackwise.migration;

import tblack.stackwise.config.StackWiseConfig;

public record RuleMigrationResult(
        boolean success,
        StackWiseConfig config,
        String messageKey,
        Object[] messageArgs,
        Throwable cause
) {
    public RuleMigrationResult {
        messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    public static RuleMigrationResult success(StackWiseConfig config, String messageKey, Object... args) {
        return new RuleMigrationResult(true, config, messageKey, args, null);
    }

    public static RuleMigrationResult failure(String messageKey, Throwable cause, Object... args) {
        return new RuleMigrationResult(false, null, messageKey, args, cause);
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs.clone();
    }
}
