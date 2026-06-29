package tblack.stackwise.stack;

import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.rule.CompiledRuleSet;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.RuleCompiler;
import tblack.stackwise.rule.RuleResolution;
import tblack.stackwise.rule.StackRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StackApplyService {
    private static final String GLOBAL_SOURCE = "@global";
    private final ItemStackLimitAdapter adapter;
    private final RuleCompiler compiler = new RuleCompiler();
    private final ItemSafetyClassifier safetyClassifier = new ItemSafetyClassifier();
    private final Map<String, Integer> originalLimits = new LinkedHashMap<>();
    private final Map<String, Integer> lastApplied = new LinkedHashMap<>();
    private final Map<String, String> lastAppliedSources = new LinkedHashMap<>();
    private final Map<String, Object> originalOwners = new LinkedHashMap<>();
    private Map<String, Object> currentAssets = Map.of();
    private Map<String, Integer> lastMatchCounts = Map.of();
    private StackWiseConfig currentConfig;
    private StackApplyReport lastReport = new StackApplyReport();

    public StackApplyService(ItemStackLimitAdapter adapter, StackWiseConfig initialConfig) {
        this.adapter = adapter;
        this.currentConfig = initialConfig;
    }

    public synchronized StackApplyReport onAssetsLoaded(Map<String, ?> assets, StackWiseConfig config) {
        currentAssets = assets == null ? Map.of() : new LinkedHashMap<>(assets);
        originalLimits.keySet().retainAll(currentAssets.keySet());
        lastApplied.keySet().retainAll(currentAssets.keySet());
        lastAppliedSources.keySet().retainAll(currentAssets.keySet());
        originalOwners.keySet().retainAll(currentAssets.keySet());
        for (Map.Entry<String, Object> entry : currentAssets.entrySet()) {
            Object previous = originalOwners.put(entry.getKey(), entry.getValue());
            if (previous == entry.getValue()) continue;
            originalLimits.remove(entry.getKey());
            lastApplied.remove(entry.getKey());
            lastAppliedSources.remove(entry.getKey());
        }
        currentConfig = config;
        lastReport = apply(config, false);
        return lastReport;
    }

    public synchronized StackApplyReport applyRuntime(StackWiseConfig config) {
        currentConfig = config;
        lastReport = apply(config, true);
        return lastReport;
    }

    public synchronized StackApplyReport lastReport() {
        return lastReport;
    }

    public synchronized int matchCount(String ruleId) {
        if (ruleId == null) return 0;
        return lastMatchCounts.getOrDefault(ruleId, 0);
    }

    public synchronized ItemCatalogExport catalogExport() {
        List<ItemCatalogEntry> entries = catalog();
        return new ItemCatalogExport(1, entries.size(), entries);
    }

    public synchronized List<ItemCatalogEntry> catalog() {
        List<ItemCatalogEntry> entries = new ArrayList<>();
        CompiledRuleSet rules = currentConfig == null ? null : compiler.compile(currentConfig);
        currentAssets.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                String id = entry.getKey();
                Object item = entry.getValue();
                int current = adapter.read(item);
                int original = originalLimits.getOrDefault(id, current);
                RuleResolution resolution = rules == null ? RuleResolution.none() : rules.resolve(id);
                StackRule rule = resolution.rule();
                boolean global = currentConfig != null
                        && currentConfig.enabled
                        && currentConfig.globalLimitEnabled
                        && !resolution.matched()
                        && original > 1;
                String unsafeReason = safetyClassifier.unsafeReason(item, original);
                String status = resolution.excluded()
                        ? "EXCLUDED"
                        : resolution.matched()
                        ? "MATCHED"
                        : global
                        ? "GLOBAL"
                        : "UNMATCHED";
                Integer target = null;
                if (rule != null && rule.action == RuleAction.SET) target = rule.maxStack;
                else if (global) target = currentConfig.globalStackLimit;
                entries.add(new ItemCatalogEntry(
                        id,
                        original,
                        current,
                        target,
                        rule == null ? null : rule.id,
                        rule == null ? global ? "GLOBAL" : null : rule.action.name(),
                        rule == null ? null : rule.matchType.name(),
                        rule == null ? null : rule.value,
                        status,
                        unsafeReason
                ));
            } catch (ReflectiveOperationException ignored) {
            }
        });
        return entries;
    }

    public synchronized int assetCount() {
        return currentAssets.size();
    }

    private StackApplyReport apply(StackWiseConfig config, boolean runtime) {
        StackApplyReport report = new StackApplyReport();
        report.adapterAvailable = adapter.isAvailable();
        report.adapterDescription = adapter.description();
        if (!report.adapterAvailable || currentAssets.isEmpty()) {
            lastMatchCounts = Map.of();
            return report;
        }

        CompiledRuleSet rules = compiler.compile(config);
        List<Map.Entry<String, Object>> assets = currentAssets.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();

        for (Map.Entry<String, Object> entry : assets) {
            applyItem(entry.getKey(), entry.getValue(), config, rules, runtime, report);
        }
        lastMatchCounts = new LinkedHashMap<>(rules.matchCounts());
        return report;
    }

    private void applyItem(String itemId, Object item, StackWiseConfig config, CompiledRuleSet rules, boolean runtime, StackApplyReport report) {
        report.scanned++;
        try {
            int current = adapter.read(item);
            int original = originalLimits.computeIfAbsent(itemId, ignored -> current);
            Integer previousApplied = lastApplied.get(itemId);

            if (previousApplied != null && current != previousApplied && config.respectExternalChanges) {
                report.externalConflict++;
                report.addChange(new StackChange(itemId, original, current, current, current, null, "external-conflict"));
                return;
            }

            RuleResolution resolution = config.enabled ? rules.resolve(itemId) : RuleResolution.none();
            if (resolution.excluded()) {
                report.excluded++;
                restoreIfNeeded(itemId, item, original, current, previousApplied, config, runtime, report, resolution.rule());
                return;
            }

            if (resolution.matched()) {
                StackRule rule = resolution.rule();
                applyRequestedValue(
                        itemId,
                        item,
                        original,
                        current,
                        rule.maxStack,
                        rule.id,
                        rule.allowUnsafe,
                        config,
                        runtime,
                        report
                );
                return;
            }

            if (config.enabled && config.globalLimitEnabled && original > 1) {
                applyRequestedValue(
                        itemId,
                        item,
                        original,
                        current,
                        config.globalStackLimit,
                        GLOBAL_SOURCE,
                        false,
                        config,
                        runtime,
                        report
                );
                return;
            }

            restoreIfNeeded(itemId, item, original, current, previousApplied, config, runtime, report, null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            report.failures++;
            String message = exception.getMessage();
            String detail = exception.getClass().getSimpleName()
                    + (message == null || message.isBlank() ? "" : ": " + message);
            report.addChange(new StackChange(itemId, 0, 0, 0, 0, null, "failure", detail));
        }
    }

    private void applyRequestedValue(
            String itemId,
            Object item,
            int original,
            int current,
            int requested,
            String ruleId,
            boolean allowUnsafe,
            StackWiseConfig config,
            boolean runtime,
            StackApplyReport report
    ) throws ReflectiveOperationException {
        report.matched++;
        String unsafeReason = safetyClassifier.unsafeReason(item, original);
        if (config.safeMode && requested > original && unsafeReason != null && !allowUnsafe) {
            report.unsafeBlocked++;
            report.addChange(new StackChange(itemId, original, current, requested, current, ruleId, "unsafe-blocked:" + unsafeReason));
            return;
        }
        if (requested < original && !config.allowDecreases) {
            report.decreaseBlocked++;
            report.addChange(new StackChange(itemId, original, current, requested, current, ruleId, "below-original-blocked"));
            return;
        }
        if (runtime && requested < current && !config.allowRuntimeDecreases) {
            report.restartRequired++;
            report.addChange(new StackChange(itemId, original, current, requested, current, ruleId, "restart-required"));
            return;
        }
        applyValue(itemId, item, original, current, requested, ruleId, report);
    }

    private void restoreIfNeeded(
            String itemId,
            Object item,
            int original,
            int current,
            Integer previousApplied,
            StackWiseConfig config,
            boolean runtime,
            StackApplyReport report,
            StackRule rule
    ) throws ReflectiveOperationException {
        boolean forceRestore = !config.enabled
                || (GLOBAL_SOURCE.equals(lastAppliedSources.get(itemId)) && !config.globalLimitEnabled);
        if ((!config.restoreUnmatchedItems && !forceRestore) || previousApplied == null) {
            report.unchanged++;
            return;
        }
        if (runtime && original < current && !config.allowRuntimeDecreases) {
            report.restartRequired++;
            report.addChange(new StackChange(itemId, original, current, original, current, rule == null ? null : rule.id, "restart-required"));
            return;
        }
        applyValue(itemId, item, original, current, original, rule == null ? null : rule.id, report);
        lastApplied.remove(itemId);
        lastAppliedSources.remove(itemId);
    }

    private void applyValue(String itemId, Object item, int original, int current, int requested, String ruleId, StackApplyReport report) throws ReflectiveOperationException {
        if (current == requested) {
            report.unchanged++;
            lastApplied.put(itemId, requested);
            if (ruleId != null) lastAppliedSources.put(itemId, ruleId);
            else lastAppliedSources.remove(itemId);
            return;
        }
        adapter.write(item, requested);
        int applied = adapter.read(item);
        if (applied != requested) {
            report.failures++;
            report.addChange(new StackChange(itemId, original, current, requested, applied, ruleId, "verification-failed"));
            return;
        }
        report.changed++;
        lastApplied.put(itemId, applied);
        if (ruleId != null) lastAppliedSources.put(itemId, ruleId);
        else lastAppliedSources.remove(itemId);
        report.addChange(new StackChange(itemId, original, current, requested, applied, ruleId, "changed"));
    }
}
