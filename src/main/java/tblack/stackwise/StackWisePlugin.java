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
import tblack.stackwise.permissions.PermissionService;
import tblack.stackwise.stack.ReflectionItemStackLimitAdapter;
import tblack.stackwise.stack.StackApplyReport;
import tblack.stackwise.stack.StackApplyService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class StackWisePlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static StackWisePlugin instance;

    private final ConfigManager configManager;
    private final PermissionService permissionService = new PermissionService();
    private final OperationLogService operationLogService = new OperationLogService();
    private StackWiseConfig config;
    private StackApplyService applyService;

    public StackWisePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        configManager = new ConfigManager(init.getDataDirectory());
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
        applyService = new StackApplyService(new ReflectionItemStackLimitAdapter(Item.class), config);

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

    public synchronized OperationResult reloadFromDisk() {
        ConfigOperationResult result = configManager.reload();
        OperationResult operation;
        if (!result.success()) {
            operation = OperationResult.failure(
                    "messages.operation_failed",
                    result.validation(),
                    applyService.lastReport(),
                    result.message()
            );
        } else {
            config = result.config();
            permissionService.clearCache();
            permissionService.register(config.commands.adminPermission);
            StackApplyReport report = applyService.applyRuntime(config);
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
            operation = OperationResult.failure(
                    "messages.operation_failed",
                    result.validation(),
                    applyService.lastReport(),
                    result.message()
            );
        } else {
            config = result.config();
            permissionService.clearCache();
            permissionService.register(config.commands.adminPermission);
            StackApplyReport report = applyService.applyRuntime(config);
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

    public synchronized ValidationResult validateConfig() {
        return configManager.validateCurrent();
    }

    public synchronized OperationResult exportCatalog() {
        OperationResult operation;
        try {
            Files.createDirectories(configManager.directory());
            Files.writeString(
                    configManager.catalogFile(),
                    configManager.gson().toJson(applyService.catalogExport()),
                    StandardCharsets.UTF_8
            );
            operation = OperationResult.success(
                    "messages.export_success",
                    validateConfig(),
                    applyService.lastReport(),
                    applyService.assetCount(),
                    configManager.catalogFile()
            );
        } catch (IOException exception) {
            operation = OperationResult.failure(
                    "messages.operation_failed",
                    validateConfig(),
                    applyService.lastReport(),
                    exception.getMessage()
            );
        }
        operationLogService.record(operation);
        return operation;
    }

    private void onItemAssetsLoaded(@Nonnull LoadedAssetsEvent<String, Item, DefaultAssetMap<String, Item>> event) {
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
