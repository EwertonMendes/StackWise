package tblack.stackwise;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import tblack.stackwise.commands.StackWiseCommand;
import tblack.stackwise.config.ConfigManager;
import tblack.stackwise.config.ConfigOperationResult;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.config.ValidationResult;
import tblack.stackwise.diagnostics.OperationLogService;
import tblack.stackwise.icon.HytaleItemIconCatalog;
import tblack.stackwise.icon.ItemIconCatalog;
import tblack.stackwise.migration.OverstackedRuleMigration;
import tblack.stackwise.migration.RuleMigrationResult;
import tblack.stackwise.migration.RuleMigrationService;
import tblack.stackwise.permissions.PermissionService;
import tblack.stackwise.stack.ReflectionItemStackLimitAdapter;
import tblack.stackwise.stack.RuntimeItemAssetSync;
import tblack.stackwise.stack.StackApplyReport;
import tblack.stackwise.stack.StackApplyService;

import javax.annotation.Nonnull;
import java.util.List;

public final class StackWisePlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static StackWisePlugin instance;

    private final ConfigManager configManager;
    private final RuleMigrationService migrationService;
    private final PermissionService permissionService = new PermissionService();
    private final OperationLogService operationLogService = new OperationLogService();
    private final ItemIconCatalog itemIconCatalog = new HytaleItemIconCatalog();
    private final RuntimeItemAssetSync runtimeItemAssetSync = new RuntimeItemAssetSync();
    private StackWiseConfig config;
    private StackApplyService applyService;

    public StackWisePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        configManager = new ConfigManager(init.getDataDirectory());
        migrationService = new RuleMigrationService(
                init.getDataDirectory(),
                List.of(new OverstackedRuleMigration())
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setup() {
        super.setup();
        instance = this;

        ConfigOperationResult load = configManager.loadInitial();
        config = load.config();
        permissionService.register(config.commands.adminPermission);
        permissionService.register("stackwise.admin");
        applyService = new StackApplyService(new ReflectionItemStackLimitAdapter(Item.class));

        getEventRegistry().register(LoadedAssetsEvent.class, Item.class, this::onItemAssetsLoaded);
        getCommandRegistry().registerCommand(new StackWiseCommand(this, config));

        if (!load.success()) LOGGER.atWarning().log("Configuration load failed: %s", load.message());
        LOGGER.atInfo().log("Loaded StackWise %s", getManifest().getVersion());
    }

    public static StackWisePlugin getInstance() {
        return instance;
    }

    public synchronized StackWiseConfig getConfig() {
        return configManager.get();
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public StackApplyService getApplyService() {
        return applyService;
    }

    public OperationLogService getOperationLogService() {
        return operationLogService;
    }

    public ItemIconCatalog getItemIconCatalog() {
        return itemIconCatalog;
    }

    public synchronized OperationResult reloadFromDisk() {
        ConfigOperationResult result = configManager.reload();
        OperationResult operation;
        if (!result.success()) {
            LOGGER.atWarning().log("Configuration reload failed: %s", result.message());
            operation = OperationResult.failure(
                    "messages.operation_failed",
                    result.validation(),
                    null,
                    result.message()
            );
        } else {
            config = result.config();
            refreshPermissions();
            StackApplyReport report = applyRuntimeAndSync(config);
            operation = OperationResult.success(
                    "messages.reload_success",
                    result.validation(),
                    report,
                    report.scanned,
                    report.matched,
                    report.changed,
                    report.failures
            );
        }
        operationLogService.record(operation);
        return operation;
    }

    public synchronized OperationResult saveAndApply(StackWiseConfig candidate) {
        ConfigOperationResult result = configManager.save(candidate);
        OperationResult operation;
        if (!result.success()) {
            LOGGER.atWarning().log("Configuration save failed: %s", result.message());
            operation = OperationResult.failure(
                    "messages.operation_failed",
                    result.validation(),
                    null,
                    result.message()
            );
        } else {
            config = result.config();
            refreshPermissions();
            StackApplyReport report = applyRuntimeAndSync(config);
            operation = OperationResult.success(
                    "messages.save_success",
                    result.validation(),
                    report,
                    report.scanned,
                    report.matched,
                    report.changed,
                    report.failures
            );
        }
        operationLogService.record(operation);
        return operation;
    }

    public RuleMigrationResult prepareRuleImport(String sourceId) {
        return migrationService.migrate(sourceId, getConfig());
    }

    public synchronized OperationResult applyRuleImport(String sourceId, RuleMigrationResult migration) {
        if (migration == null) return failRuleImport(sourceId, new IllegalStateException("Migration returned no result"));
        if (!migration.success()) {
            if (migration.cause() != null) {
                LOGGER.atWarning().withCause(migration.cause()).log("Rule import failed for source %s", sourceId);
            }
            OperationResult operation = OperationResult.failure(
                    migration.messageKey(),
                    validateConfig(),
                    null,
                    migration.messageArgs()
            );
            operationLogService.record(operation);
            return operation;
        }

        StackWiseConfig candidate = configManager.get();
        candidate.rules = migration.config().rules;
        candidate.globalLimitEnabled = migration.config().globalLimitEnabled;
        ConfigOperationResult saved = configManager.saveWithBackup(
                candidate,
                "config.before-import-" + sourceId
        );
        if (!saved.success()) {
            LOGGER.atWarning().log("Imported rules could not be saved: %s", saved.message());
            OperationResult operation = OperationResult.failure(
                    "messages.operation_failed",
                    saved.validation(),
                    null,
                    saved.message()
            );
            operationLogService.record(operation);
            return operation;
        }

        config = saved.config();
        refreshPermissions();
        StackApplyReport report = applyRuntimeAndSync(config);
        OperationResult operation = OperationResult.success(
                migration.messageKey(),
                saved.validation(),
                report,
                migration.messageArgs()
        );
        operationLogService.record(operation);
        return operation;
    }

    public synchronized OperationResult failRuleImport(String sourceId, Throwable failure) {
        LOGGER.atWarning().withCause(failure).log("Rule import failed for source %s", sourceId);
        OperationResult operation = OperationResult.failure(
                "messages.import_read_failed",
                validateConfig(),
                null,
                "MaxStackSizes.json"
        );
        operationLogService.record(operation);
        return operation;
    }

    public synchronized OperationResult importRules(String sourceId) {
        return applyRuleImport(sourceId, prepareRuleImport(sourceId));
    }

    public synchronized ValidationResult validateConfig() {
        return configManager.validateCurrent();
    }

    private void refreshPermissions() {
        permissionService.clearCache();
        permissionService.register(config.commands.adminPermission);
        permissionService.register("stackwise.admin");
    }

    private StackApplyReport applyRuntimeAndSync(StackWiseConfig activeConfig) {
        StackApplyReport report = applyService.applyRuntime(activeConfig);
        if (report.changedItemIds().isEmpty()) return report;
        try {
            report.clientSynced = runtimeItemAssetSync.synchronize(report.changedItemIds());
            if (report.clientSynced > 0) {
                LOGGER.atInfo().log(
                        "Synchronized %s runtime item stack limit updates with connected clients using %s",
                        report.clientSynced,
                        runtimeItemAssetSync.description()
                );
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            report.clientSyncFailures++;
            report.failures++;
            LOGGER.atSevere().withCause(exception).log(
                    "StackWise changed server item limits but could not synchronize them with connected clients"
            );
        }
        return report;
    }

    private void onItemAssetsLoaded(@Nonnull LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
        itemIconCatalog.invalidate();
        StackApplyReport report = applyService.onAssetsLoaded(event.getAssetMap().getAssetMap(), config);
        operationLogService.recordReport(
                "messages.assets_applied",
                report,
                report.scanned,
                report.matched,
                report.changed,
                report.failures
        );
        LOGGER.atInfo().log("%s using %s", report.summary(), report.adapterDescription);
    }
}
