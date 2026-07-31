package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import java.util.Map;

public final class MessageService {
    private final ConfigManager config;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
    public MessageService(ConfigManager config) { this.config = config; }
    public Component component(String text) { return mini.deserialize(text == null ? "" : text); }
    public String legacy(String text) { return legacy.serialize(component(text)); }
    public String legacy(Component component) { return legacy.serialize(component); }
    public String plain(String text) { return plain.serialize(component(text)); }
    public Component raw(String key, Map<String, String> replacements) {
        String value = config.language().getString(key, "<red>Missing message: " + key);
        for (var entry : replacements.entrySet()) value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        return component(value);
    }
    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String prefix = config.language().getString("prefix", "");
        sender.sendMessage(legacy(component(prefix).append(raw(key, replacements))));
    }
    public void help(CommandSender sender) {
        for (String line : config.language().getStringList("command-help")) sender.sendMessage(legacy(line));
    }
    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
