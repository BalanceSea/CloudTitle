package github.balncesea.cloudTitle.util;

import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.Map;

/**
 * 兼容不同 Spigot 版本的原版药水效果名称。
 *
 * <p>Spigot 1.20.1 仍保留 FAST_DIGGING 等旧 Bukkit 名称，而较新的版本
 * 使用 HASTE 等 Minecraft 注册表名称。配置和跨服状态统一使用现代名称，
 * 解析时再根据当前服务端 API 回退到旧名称。</p>
 */
public final class PotionEffectResolver {
    private static final Map<String, String> LEGACY_NAMES = Map.ofEntries(
            Map.entry("HASTE", "FAST_DIGGING"),
            Map.entry("MINING_FATIGUE", "SLOW_DIGGING"),
            Map.entry("STRENGTH", "INCREASE_DAMAGE"),
            Map.entry("INSTANT_HEALTH", "HEAL"),
            Map.entry("INSTANT_DAMAGE", "HARM"),
            Map.entry("JUMP_BOOST", "JUMP"),
            Map.entry("NAUSEA", "CONFUSION"),
            Map.entry("RESISTANCE", "DAMAGE_RESISTANCE")
    );

    private static final Map<String, String> MODERN_KEYS = Map.ofEntries(
            Map.entry("fast_digging", "haste"),
            Map.entry("slow_digging", "mining_fatigue"),
            Map.entry("increase_damage", "strength"),
            Map.entry("heal", "instant_health"),
            Map.entry("harm", "instant_damage"),
            Map.entry("jump", "jump_boost"),
            Map.entry("confusion", "nausea"),
            Map.entry("damage_resistance", "resistance")
    );

    private PotionEffectResolver() {
    }

    /** 按现代名称或旧 Bukkit 名称解析当前服务端的药水效果。 */
    public static PotionEffectType resolve(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return null;

        PotionEffectType direct = PotionEffectType.getByName(normalized);
        if (direct != null) return direct;

        // 1.20.1 的 FAST_DIGGING 包装器注册键仍然是 minecraft:haste。
        PotionEffectType byKey = PotionEffectType.getByKey(
                NamespacedKey.minecraft(normalized.toLowerCase(Locale.ROOT)));
        if (byKey != null) return byKey;

        String legacyName = LEGACY_NAMES.get(normalized);
        return legacyName == null ? null : PotionEffectType.getByName(legacyName);
    }

    /** 返回用于配置、语言键和跨服状态的现代效果键。 */
    public static String key(PotionEffectType type) {
        if (type == null || type.getKey() == null) return "";
        String key = type.getKey().getKey().toLowerCase(Locale.ROOT);
        return MODERN_KEYS.getOrDefault(key, key);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf(':');
        if (separator >= 0) normalized = normalized.substring(separator + 1);
        return normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
