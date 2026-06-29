package tblack.stackwise.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.stackwise.OperationResult;
import tblack.stackwise.StackWisePlugin;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.config.ValidationResult;
import tblack.stackwise.i18n.I18n;
import tblack.stackwise.stack.StackApplyReport;
import tblack.stackwise.ui.StackWiseAdminPage;
import tblack.stackwise.util.Chat;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class StackWiseCommand extends AbstractPlayerCommand {
    private final StackWisePlugin plugin;

    public StackWiseCommand(StackWisePlugin plugin, StackWiseConfig config) {
        super(config.commands.primary, I18n.commandKey("commands.root.description"));
        this.plugin = plugin;
        if (config.commands.aliases != null && !config.commands.aliases.isEmpty()) {
            addAliases(config.commands.aliases.toArray(String[]::new));
        }
        addSubCommand(new UiCommand(plugin));
        addSubCommand(new ReloadCommand(plugin));
        addSubCommand(new ValidateCommand(plugin));
        addSubCommand(new ExportCommand(plugin));
        addSubCommand(new StatusCommand(plugin));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        openPanel(plugin, context, store, ref, playerRef);
    }

    private static void openPanel(
            StackWisePlugin plugin,
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef
    ) {
        if (!canManage(plugin, context)) {
            Chat.send(context, Chat.error(context, "messages.no_permission"));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Chat.send(context, Chat.error(context, "messages.player_not_found"));
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new StackWiseAdminPage(playerRef, plugin, 0, "", ""));
    }

    private static boolean canManage(StackWisePlugin plugin, CommandContext context) {
        String permission = plugin.getConfig().commands.adminPermission;
        try {
            if (context.sender().hasPermission(permission)
                    || context.sender().hasPermission("stackwise.admin")
                    || context.sender().hasPermission("*")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            UUID uuid = context.sender().getUuid();
            return plugin.getPermissionService().hasPermission(uuid, permission);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class UiCommand extends AbstractPlayerCommand {
        private final StackWisePlugin plugin;

        private UiCommand(StackWisePlugin plugin) {
            super("ui", I18n.commandKey("commands.ui.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world
        ) {
            openPanel(plugin, context, store, ref, playerRef);
        }
    }

    private static final class ReloadCommand extends CommandBase {
        private final StackWisePlugin plugin;

        private ReloadCommand(StackWisePlugin plugin) {
            super("reload", I18n.commandKey("commands.reload.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(plugin, context)) {
                Chat.send(context, Chat.error(context, "messages.no_permission"));
                return;
            }
            OperationResult result = plugin.reloadFromDisk();
            Chat.send(context, result.success()
                    ? Chat.rawSuccess(result.translated(I18n.locale(context)))
                    : Chat.rawError(result.translated(I18n.locale(context))));
        }
    }

    private static final class ValidateCommand extends CommandBase {
        private final StackWisePlugin plugin;

        private ValidateCommand(StackWisePlugin plugin) {
            super("validate", I18n.commandKey("commands.validate.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(plugin, context)) {
                Chat.send(context, Chat.error(context, "messages.no_permission"));
                return;
            }
            ValidationResult validation = plugin.validateConfig();
            if (validation.isValid()) {
                Chat.send(context, Chat.success(context, "messages.validation_success", validation.warnings().size()));
                return;
            }
            Chat.send(context, Chat.error(context, "messages.validation_failed", validation.firstError(I18n.locale(context))));
        }
    }

    private static final class ExportCommand extends CommandBase {
        private final StackWisePlugin plugin;

        private ExportCommand(StackWisePlugin plugin) {
            super("export", I18n.commandKey("commands.export.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(plugin, context)) {
                Chat.send(context, Chat.error(context, "messages.no_permission"));
                return;
            }
            OperationResult result = plugin.exportCatalog();
            Chat.send(context, result.success()
                    ? Chat.rawSuccess(result.translated(I18n.locale(context)))
                    : Chat.rawError(result.translated(I18n.locale(context))));
        }
    }

    private static final class StatusCommand extends CommandBase {
        private final StackWisePlugin plugin;

        private StatusCommand(StackWisePlugin plugin) {
            super("status", I18n.commandKey("commands.status.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(plugin, context)) {
                Chat.send(context, Chat.error(context, "messages.no_permission"));
                return;
            }
            StackApplyReport report = plugin.getApplyService().lastReport();
            Chat.send(context, Chat.info(context, "messages.report", report.scanned, report.matched, report.changed,
                    report.unsafeBlocked + report.decreaseBlocked + report.restartRequired + report.externalConflict,
                    report.failures));
            Chat.send(context, Chat.info(context, "messages.adapter", report.adapterDescription));
        }
    }
}
