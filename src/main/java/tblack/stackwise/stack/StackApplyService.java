package tblack.stackwise.stack;

import tblack.stackwise.StackWisePlugin;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.rule.CompiledRuleSet;
import tblack.stackwise.rule.RuleCompiler;
import tblack.stackwise.rule.RuleResolution;
import tblack.stackwise.rule.StackRule;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StackApplyService {
    private static final String GLOBAL_SOURCE = "@global";

    private final ItemStackLimitAdapter adapter;
    private final RuleCompiler compiler = new RuleCompiler();
    private final ItemSafetyClassifier safetyClassifier = new ItemSafetyClassifier();
    private final Map<String, ItemState> states = new LinkedHashMap<>();
    private Map<String, Object> currentAssets = Map.of();
    private Map<String, Integer> lastMatchCounts = Map.of();
    private StackApplyReport lastReport = new StackApplyReport();

    public StackApplyService(ItemStackLimitAdapter adapter) {
        this.adapter = adapter;
    }

    public synchronized StackApplyReport onAssetsLoaded(Map<String, ?> assets, StackWiseConfig config) {
        currentAssets = assets == null ? Map.of() : new LinkedHashMap<>(assets);
        states.keySet().retainAll(currentAssets.keySet());
        for (Map.Entry<String, Object> entry : currentAssets.entrySet()) {
            ItemState state = states.get(entry.getKey());
            if (state != null && state.asset == entry.getValue()) continue;
            states.remove(entry.getKey());
        }
        lastReport = apply(config, false);
        return lastReport;
    }

    public synchronized StackApplyReport applyRuntime(StackWiseConfig config) {
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

    public synchronized int assetCount() {
        return currentAssets.size();
    }

    private StackApplyReport apply(StackWiseConfig config, boolean runtime) {
        StackApplyReport report = new StackApplyReport();
        report.adapterAvailable = adapter.isAvailable();
        report.adapterDescription = adapter.description();
        if (!report.adapterAvailable) {
            lastMatchCounts = Map.of();
            StackWisePlugin.LOGGER.atSevere().log("StackWise could not access the Hytale item stack limit field: %s", report.adapterDescription);
            return report;
        }
        if (currentAssets.isEmpty()) {
            lastMatchCounts = Map.of();
            return report;
        }

        CompiledRuleSet rules = compiler.compile(config);
        currentAssets.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> applyItem(entry.getKey(), entry.getValue(), config, rules, runtime, report));
        lastMatchCounts = new LinkedHashMap<>(rules.matchCounts());
        return report;
    }

    private void applyItem(
            String itemId,
            Object item,
            StackWiseConfig config,
            CompiledRuleSet rules,
            boolean runtime,
            StackApplyReport report
    ) {
        report.scanned++;
        try {
            int current = adapter.read(item);
            ItemState state = stateFor(itemId, item, current);
            if (state.externalControl && config.respectExternalChanges) {
                state.baseline = current;
                report.unchanged++;
                return;
            }
            if (state.externalControl) {
                state.externalControl = false;
                state.baseline = current;
            }
            if (hasExternalChange(state, current)) {
                if (config.respectExternalChanges) {
                    releaseToExternalControl(itemId, state, current, report);
                    return;
                }
            }
            if (!config.enabled) {
                restoreOwnedValue(itemId, item, state, current, config, runtime, true, null, report);
                return;
            }

            RuleResolution resolution = rules.resolve(itemId);
            if (resolution.excluded()) {
                report.excluded++;
                restoreOwnedValue(itemId, item, state, current, config, runtime, true, resolution.rule(), report);
                return;
            }
            if (resolution.matched()) {
                StackRule rule = resolution.rule();
                applyRequestedValue(
                        itemId,
                        item,
                        state,
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
            if (config.globalLimitEnabled && state.baseline > 1) {
                applyRequestedValue(
                        itemId,
                        item,
                        state,
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

            boolean globalWasDisabled = GLOBAL_SOURCE.equals(state.source) && !config.globalLimitEnabled;
            restoreOwnedValue(itemId, item, state, current, config, runtime, globalWasDisabled, null, report);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            report.failures++;
            report.addChange(new StackChange(itemId, 0, 0, 0, 0, null, "failure"));
            StackWisePlugin.LOGGER.atSevere().withCause(exception).log("StackWise failed to process item %s", itemId);
        }
    }

    private ItemState stateFor(String itemId, Object item, int current) {
        ItemState state = states.get(itemId);
        if (state != null && state.asset == item) return state;
        ItemState created = new ItemState(item, current);
        states.put(itemId, created);
        return created;
    }

    private boolean hasExternalChange(ItemState state, int current) {
        return state.lastApplied != null && current != state.lastApplied;
    }

    private void releaseToExternalControl(String itemId, ItemState state, int current, StackApplyReport report) {
        int original = state.baseline;
        state.baseline = current;
        state.lastApplied = null;
        state.source = null;
        state.externalControl = true;
        report.externalConflict++;
        report.addChange(new StackChange(itemId, original, current, current, current, null, "external-conflict"));
    }

    private void applyRequestedValue(
            String itemId,
            Object item,
            ItemState state,
            int current,
            int requested,
            String source,
            boolean allowUnsafe,
            StackWiseConfig config,
            boolean runtime,
            StackApplyReport report
    ) throws ReflectiveOperationException {
        report.matched++;
        String unsafeReason = safetyClassifier.unsafeReason(item, state.baseline);
        if (config.safeMode && requested > state.baseline && unsafeReason != null && !allowUnsafe) {
            report.unsafeBlocked++;
            report.addChange(new StackChange(
                    itemId,
                    state.baseline,
                    current,
                    requested,
                    current,
                    source,
                    "unsafe-blocked:" + unsafeReason
            ));
            return;
        }
        if (requested < state.baseline && !config.allowDecreases) {
            report.decreaseBlocked++;
            report.addChange(new StackChange(
                    itemId,
                    state.baseline,
                    current,
                    requested,
                    current,
                    source,
                    "below-original-blocked"
            ));
            return;
        }
        if (runtime && requested < current && !config.allowRuntimeDecreases) {
            report.restartRequired++;
            report.addChange(new StackChange(
                    itemId,
                    state.baseline,
                    current,
                    requested,
                    current,
                    source,
                    "restart-required"
            ));
            return;
        }
        applyValue(itemId, item, state, current, requested, source, report);
    }

    private void restoreOwnedValue(
            String itemId,
            Object item,
            ItemState state,
            int current,
            StackWiseConfig config,
            boolean runtime,
            boolean force,
            StackRule rule,
            StackApplyReport report
    ) throws ReflectiveOperationException {
        if (state.lastApplied == null) {
            report.unchanged++;
            return;
        }
        if (!force && !config.restoreUnmatchedItems) {
            report.unchanged++;
            return;
        }
        if (!force && runtime && state.baseline < current && !config.allowRuntimeDecreases) {
            report.restartRequired++;
            report.addChange(new StackChange(
                    itemId,
                    state.baseline,
                    current,
                    state.baseline,
                    current,
                    rule == null ? null : rule.id,
                    "restart-required"
            ));
            return;
        }
        restoreValue(itemId, item, state, current, rule == null ? null : rule.id, report);
    }

    private void restoreValue(
            String itemId,
            Object item,
            ItemState state,
            int current,
            String ruleId,
            StackApplyReport report
    ) throws ReflectiveOperationException {
        if (current == state.baseline) {
            report.unchanged++;
            state.release();
            return;
        }
        adapter.write(item, state.baseline);
        int applied = adapter.read(item);
        if (applied != state.baseline) {
            report.failures++;
            report.addChange(new StackChange(
                    itemId,
                    state.baseline,
                    current,
                    state.baseline,
                    applied,
                    ruleId,
                    "verification-failed"
            ));
            return;
        }
        report.changed++;
        report.addChange(new StackChange(itemId, state.baseline, current, state.baseline, applied, ruleId, "changed"));
        state.release();
    }

    private void applyValue(
            String itemId,
            Object item,
            ItemState state,
            int current,
            int requested,
            String source,
            StackApplyReport report
    ) throws ReflectiveOperationException {
        if (current == requested) {
            report.unchanged++;
            state.claim(requested, source);
            return;
        }
        adapter.write(item, requested);
        int applied = adapter.read(item);
        if (applied != requested) {
            report.failures++;
            report.addChange(new StackChange(
                    itemId,
                    state.baseline,
                    current,
                    requested,
                    applied,
                    source,
                    "verification-failed"
            ));
            return;
        }
        report.changed++;
        state.claim(applied, source);
        report.addChange(new StackChange(itemId, state.baseline, current, requested, applied, source, "changed"));
    }

    private static final class ItemState {
        private final Object asset;
        private int baseline;
        private Integer lastApplied;
        private String source;
        private boolean externalControl;

        private ItemState(Object asset, int baseline) {
            this.asset = asset;
            this.baseline = baseline;
        }

        private void claim(int value, String source) {
            lastApplied = value;
            this.source = source;
            externalControl = false;
        }

        private void release() {
            lastApplied = null;
            source = null;
            externalControl = false;
        }
    }
}
