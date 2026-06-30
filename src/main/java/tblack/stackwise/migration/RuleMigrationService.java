package tblack.stackwise.migration;

import tblack.stackwise.config.StackWiseConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuleMigrationService {
    private final Path stackWiseDirectory;
    private final Map<String, RuleMigrationSource> sources;

    public RuleMigrationService(Path stackWiseDirectory, List<RuleMigrationSource> sources) {
        this.stackWiseDirectory = stackWiseDirectory.toAbsolutePath().normalize();
        Map<String, RuleMigrationSource> registered = new LinkedHashMap<>();
        for (RuleMigrationSource source : sources) {
            if (source == null || source.id() == null || source.id().isBlank()) continue;
            registered.put(source.id().trim().toLowerCase(Locale.ROOT), source);
        }
        this.sources = Map.copyOf(registered);
    }

    public RuleMigrationResult migrate(String sourceId, StackWiseConfig currentConfig) {
        String normalized = sourceId == null ? "" : sourceId.trim().toLowerCase(Locale.ROOT);
        RuleMigrationSource source = sources.get(normalized);
        if (source == null) return RuleMigrationResult.failure("messages.import_source_unknown", null, normalized);
        return source.migrate(stackWiseDirectory, currentConfig);
    }
}
