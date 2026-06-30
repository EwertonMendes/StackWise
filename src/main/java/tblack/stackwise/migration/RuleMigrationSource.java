package tblack.stackwise.migration;

import tblack.stackwise.config.StackWiseConfig;

import java.nio.file.Path;

public interface RuleMigrationSource {
    String id();

    RuleMigrationResult migrate(Path stackWiseDirectory, StackWiseConfig currentConfig);
}
