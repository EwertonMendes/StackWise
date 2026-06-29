package tblack.stackwise.config;

public record ConfigOperationResult(boolean success, StackWiseConfig config, ValidationResult validation, String message) {
    public static ConfigOperationResult success(StackWiseConfig config, ValidationResult validation, String message) {
        return new ConfigOperationResult(true, config, validation, message);
    }

    public static ConfigOperationResult failure(StackWiseConfig config, ValidationResult validation, String message) {
        return new ConfigOperationResult(false, config, validation, message);
    }
}
