package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import java.util.Map;

public final class MessageService {
    private final ConfigManager config;
    private final MiniMessage mini = MiniMessage.miniMessage();
    public MessageService(ConfigManager config) { this.config = config; }
    public Component component(String text) { return mini.deserialize(text == null ? "" : text); }
    public Component raw(String key, Map<String, String> replacements) {
        String value = config.language().getString(key, "<red>Missing message: " + key);
        for (var entry : replacements.entrySet()) value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        return component(value);
    }
    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String prefix = config.language().getString("prefix", "");
        sender.sendMessage(component(prefix).append(raw(key, replacements)));
    }
    public void help(CommandSender sender) {
        for (String line : config.language().getStringList("command-help")) sender.sendMessage(component(line));
    }
    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }
}