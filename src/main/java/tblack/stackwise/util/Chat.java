package tblack.stackwise.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import tblack.stackwise.i18n.I18n;

public final class Chat {
    private Chat() {
    }

    public static void send(CommandContext context, Message message) {
        context.sendMessage(message);
    }

    public static Message info(CommandContext context, String key, Object... args) {
        return Message.raw("[StackWise] " + I18n.translate(context, key, args)).color("#CFCFE6");
    }

    public static Message success(CommandContext context, String key, Object... args) {
        return Message.raw("[StackWise] " + I18n.translate(context, key, args)).color("#55FF9C");
    }

    public static Message error(CommandContext context, String key, Object... args) {
        return Message.raw("[StackWise] " + I18n.translate(context, key, args)).color("#FF6B81");
    }

    public static Message rawInfo(String message) {
        return Message.raw("[StackWise] " + message).color("#CFCFE6");
    }

    public static Message rawSuccess(String message) {
        return Message.raw("[StackWise] " + message).color("#55FF9C");
    }

    public static Message rawError(String message) {
        return Message.raw("[StackWise] " + message).color("#FF6B81");
    }
}
