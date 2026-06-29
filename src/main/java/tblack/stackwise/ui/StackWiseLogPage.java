package tblack.stackwise.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.stackwise.StackWisePlugin;
import tblack.stackwise.diagnostics.LogSeverity;
import tblack.stackwise.diagnostics.OperationLogEntry;
import tblack.stackwise.i18n.I18n;

import javax.annotation.Nonnull;
import java.util.List;

public final class StackWiseLogPage extends InteractiveCustomUIPage<StackWiseLogPage.LogEventData> {
    private static final String LAYOUT = "StackWise/Log.ui";
    private static final int PAGE_SIZE = 10;

    private final StackWisePlugin plugin;
    private final PlayerRef viewerRef;
    private final String locale;
    private final int returnPage;
    private final String returnSearch;
    private final String returnTab;
    private int page;

    public StackWiseLogPage(
            @Nonnull PlayerRef playerRef,
            StackWisePlugin plugin,
            int returnPage,
            String returnSearch,
            String returnTab
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, LogEventData.CODEC);
        this.plugin = plugin;
        this.viewerRef = playerRef;
        this.locale = I18n.localeFromPlayerRef(playerRef);
        this.returnPage = Math.max(0, returnPage);
        this.returnSearch = returnSearch == null ? "" : returnSearch;
        this.returnTab = returnTab == null ? "rules" : returnTab;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        commands.append(LAYOUT);
        bindEvents(events);
        render(commands);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            LogEventData data
    ) {
        super.handleDataEvent(ref, store, data);
        if (!hasPermission()) {
            close();
            return;
        }
        if (data == null || data.action == null || data.action.isBlank()) {
            refresh();
            return;
        }
        switch (data.action) {
            case "close" -> close();
            case "back" -> openAdmin(ref, store);
            case "previous" -> {
                page = Math.max(0, page - 1);
                refresh();
            }
            case "next" -> {
                page++;
                clampPage();
                refresh();
            }
            default -> refresh();
        }
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClosePageButton", EventData.of("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PreviousButton", EventData.of("Action", "previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "next"), false);
    }

    private void refresh() {
        UICommandBuilder commands = new UICommandBuilder();
        render(commands);
        sendUpdate(commands, false);
    }

    private void render(UICommandBuilder commands) {
        List<OperationLogEntry> entries = plugin.getOperationLogService().entries();
        clampPage();
        renderStaticText(commands);
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        commands.set("#PageLabel.TextSpans", Message.raw(translate("ui.log.page", page + 1, totalPages, entries.size())));
        commands.set("#PreviousButton.Disabled", page == 0);
        commands.set("#NextButton.Disabled", page + 1 >= totalPages);
        commands.set("#EmptyLabel.Visible", entries.isEmpty());

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            boolean visible = index < entries.size();
            commands.set("#LogRow" + slot + ".Visible", visible);
            if (!visible) continue;
            OperationLogEntry entry = entries.get(index);
            setSeverityLabels(commands, slot, entry.severity(), entry.translated(locale));
        }
    }

    private void setSeverityLabels(UICommandBuilder commands, int slot, LogSeverity severity, String text) {
        for (LogSeverity value : LogSeverity.values()) {
            String id = "#Log" + suffix(value) + slot;
            commands.set(id + ".Visible", value == severity);
            if (value == severity) commands.set(id + ".TextSpans", Message.raw(prefix(value) + " " + text));
        }
    }

    private String prefix(LogSeverity severity) {
        return "[" + translate("ui.log.level." + severity.name().toLowerCase()) + "]";
    }

    private String suffix(LogSeverity severity) {
        return switch (severity) {
            case INFO -> "Info";
            case SUCCESS -> "Success";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }

    private void renderStaticText(UICommandBuilder commands) {
        setText(commands, "#LogTitle", translate("ui.log.title"));
        setText(commands, "#LogSubtitle", translate("ui.log.subtitle"));
        setText(commands, "#LegendInfo", translate("ui.log.level.info"));
        setText(commands, "#LegendSuccess", translate("ui.log.level.success"));
        setText(commands, "#LegendWarning", translate("ui.log.level.warning"));
        setText(commands, "#LegendError", translate("ui.log.level.error"));
        setText(commands, "#EmptyLabel", translate("ui.log.empty"));
        setText(commands, "#PreviousButton", translate("ui.common.previous"));
        setText(commands, "#NextButton", translate("ui.common.next"));
        setText(commands, "#BackButton", translate("ui.common.back"));
        setText(commands, "#ClosePageButton", translate("ui.common.close"));
    }

    private void setText(UICommandBuilder commands, String selector, String value) {
        commands.set(selector + ".TextSpans", Message.raw(value));
    }

    private void clampPage() {
        int size = plugin.getOperationLogService().size();
        int totalPages = Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));
    }

    private void openAdmin(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        player.getPageManager().openCustomPage(
                ref,
                store,
                new StackWiseAdminPage(viewerRef, plugin, returnPage, returnSearch, "", returnTab)
        );
    }

    private boolean hasPermission() {
        String permission = plugin.getConfig().commands.adminPermission;
        if (viewerRef.hasPermission(permission)
                || viewerRef.hasPermission("stackwise.admin")
                || viewerRef.hasPermission("*")) {
            return true;
        }
        return plugin.getPermissionService().hasPermission(viewerRef.getUuid(), permission);
    }

    private String translate(String key, Object... args) {
        return I18n.translate(locale, key, args);
    }

    public static final class LogEventData {
        public static final BuilderCodec<LogEventData> CODEC = BuilderCodec.builder(LogEventData.class, LogEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .build();

        public String action = "";
    }
}
