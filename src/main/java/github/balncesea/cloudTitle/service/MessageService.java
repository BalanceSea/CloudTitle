package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;

/**
 * 统一处理 MiniMessage、传统颜色代码和纯文本输出。
 *
 * <p>GUI 和 PAPI 都使用 MiniMessage 原文，只有真正发送到聊天栏时才转换成
 * Spigot 传统颜色格式，避免在业务层重复处理颜色。</p>
 */
public final class MessageService {
    private final ConfigManager config;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public MessageService(ConfigManager config) {
        this.config = config;
    }

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

    /**
     * 将自定义称号名称中的传统颜色代码转换为 MiniMessage。
     * 名称输入默认只开放颜色和重置代码；开启 allowMiniMessage 后才保留其他标签。
     */
    public static String colorizeName(String input, boolean allowMiniMessage) {
        String value = input == null ? "" : input;
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length();) {
            LegacyToken token = legacyToken(value, index);
            if (token != null) {
                result.append(token.tag());
                index += token.length();
                continue;
            }

            char character = value.charAt(index++);
            if (!allowMiniMessage && character == '<') result.append("\\<");
            else if (!allowMiniMessage && character == '\\') result.append("\\\\");
            else result.append(character);
        }
        return result.toString();
    }

    private record LegacyToken(String tag, int length) {}

    private static LegacyToken legacyToken(String value, int index) {
        if (index + 1 >= value.length()) return null;
        char prefix = value.charAt(index);
        if (prefix != '&' && prefix != '§') return null;

        char code = Character.toLowerCase(value.charAt(index + 1));
        if (code == '#') {
            if (index + 8 > value.length()) return null;
            String hex = value.substring(index + 2, index + 8);
            if (hex.chars().allMatch(character -> isHex((char) character))) {
                return new LegacyToken("<#" + hex.toLowerCase(Locale.ROOT) + ">", 8);
            }
            return null;
        }

        // Minecraft 的 &x&F&F&A&A... 格式，兼容服务端常用的十六进制写法。
        if (code == 'x') {
            StringBuilder hex = new StringBuilder(6);
            int cursor = index + 2;
            for (int digit = 0; digit < 6; digit++) {
                if (cursor + 1 >= value.length()) return null;
                char separator = value.charAt(cursor);
                char hexDigit = value.charAt(cursor + 1);
                if ((separator != '&' && separator != '§') || !isHex(hexDigit)) return null;
                hex.append(hexDigit);
                cursor += 2;
            }
            return new LegacyToken("<#" + hex + ">", cursor - index);
        }

        ChatColor color = ChatColor.getByChar(code);
        String tag = color == null ? null : legacyTag(color);
        return tag == null ? null : new LegacyToken(tag, 2);
    }

    private static boolean isHex(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private static String legacyTag(ChatColor color) {
        if (color.isColor()) return miniTag(color.name());
        return switch (color) {
            case RESET -> "<reset>";
            case BOLD -> "<bold>";
            case ITALIC -> "<italic>";
            case UNDERLINE -> "<underlined>";
            case STRIKETHROUGH -> "<strikethrough>";
            case MAGIC -> "<obfuscated>";
            default -> null;
        };
    }

    private static String miniTag(String colorName) {
        return "<" + colorName.toLowerCase(Locale.ROOT) + ">";
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
