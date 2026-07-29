package github.balncesea.cloudTitle.config;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {
    private final CloudTitle plugin;
    private YamlConfiguration storage;
    private YamlConfiguration titles;
    private YamlConfiguration warehouseGui;
    private YamlConfiguration shopGui;
    private YamlConfiguration customGui;
    private YamlConfiguration language;
    private Map<String, TitleDefinition> definitions = Map.of();

    public ConfigManager(CloudTitle plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        save("storage.yml");
        save("titles.yml");
        save("gui/warehouse.yml");
        save("gui/shop.yml");
        save("gui/custom.yml");
        save("lang/zh_CN.yml");

        storage = loadFile("storage.yml");
        titles = loadFile("titles.yml");
        warehouseGui = loadFile("gui/warehouse.yml");
        shopGui = loadFile("gui/shop.yml");
        customGui = loadFile("gui/custom.yml");
        migrateDefaultGuiTitle("gui/warehouse.yml", warehouseGui,
                "<gradient:#67E8F9:#38BDF8><bold>云世界</bold></gradient> <dark_gray>·</dark_gray> <white>称号仓库",
                "<gradient:#67E8F9:#38BDF8><bold>云称号</bold></gradient> <dark_gray>·</dark_gray> <white>仓库");
        migrateDefaultGuiTitle("gui/shop.yml", shopGui,
                "<gradient:#FDE68A:#F59E0B><bold>云世界</bold></gradient> <dark_gray>·</dark_gray> <white>称号商城",
                "<gradient:#FDE68A:#F59E0B><bold>云称号</bold></gradient> <dark_gray>·</dark_gray> <white>商城");
        migrateDefaultGuiTitle("gui/custom.yml", customGui,
                "<gradient:#C4B5FD:#F0ABFC><bold>云世界</bold></gradient> <dark_gray>·</dark_gray> <white>称号工坊",
                "<gradient:#C4B5FD:#F0ABFC><bold>云称号</bold></gradient> <dark_gray>·</dark_gray> <white>工坊");
        validateGui("warehouse", warehouseGui, true);
        validateGui("shop", shopGui, true);
        validateGui("custom", customGui, false);

        String lang = plugin.getConfig().getString("default-language", "zh_CN");
        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        }
        language = YamlConfiguration.loadConfiguration(langFile);
        try (InputStream stream = plugin.getResource("lang/zh_CN.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                language.setDefaults(defaults);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("加载默认语言回退失败: " + exception.getMessage());
        }
        migrateDefaultLanguagePrefix(langFile);
        definitions = Collections.unmodifiableMap(parseTitles());
        if (plugin.getConfig().getBoolean("default-title.enabled", true)) {
            String defaultTitleId = plugin.getConfig().getString("default-title.id", "resident");
            if (!definitions.containsKey(defaultTitleId) && !defaultTitleId.equals("resident")) {
                plugin.getLogger().warning("默认称号不存在: " + defaultTitleId);
            }
        }
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    private void save(String path) {
        if (!new File(plugin.getDataFolder(), path).exists()) {
            plugin.saveResource(path, false);
        }
    }

    private YamlConfiguration loadFile(String path) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path));
    }

    private void migrateDefaultLanguagePrefix(File languageFile) {
        String oldPrefix = "<dark_gray>[<aqua>云世界称号</aqua>]</dark_gray> ";
        if (!oldPrefix.equals(language.getString("prefix"))) return;
        language.set("prefix", "<dark_gray>[<aqua>云称号</aqua>]</dark_gray> ");
        saveChangedConfiguration(language, languageFile, "语言前缀");
    }

    private void migrateDefaultGuiTitle(String path, YamlConfiguration gui, String oldTitle, String newTitle) {
        if (!oldTitle.equals(gui.getString("Title"))) return;
        gui.set("Title", newTitle);
        saveChangedConfiguration(gui, new File(plugin.getDataFolder(), path), "GUI 标题");
    }

    private void saveChangedConfiguration(YamlConfiguration configuration, File file, String description) {
        try {
            configuration.save(file);
        } catch (Exception exception) {
            plugin.getLogger().warning("迁移" + description + "失败: " + exception.getMessage());
        }
    }


    private void validateGui(String name, YamlConfiguration gui, boolean requireDynamicTitles) {
        List<String> layout = gui.getStringList("Layout");
        if (layout.isEmpty() || layout.size() > 6) {
            plugin.getLogger().warning("GUI " + name + " 的 Layout 必须包含 1 至 6 行");
            return;
        }
        ConfigurationSection icons = gui.getConfigurationSection("Icons");
        if (icons == null) {
            plugin.getLogger().warning("GUI " + name + " 缺少 Icons 节点");
            return;
        }

        boolean hasDynamicTitle = false;
        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            if (line.length() != 9) {
                plugin.getLogger().warning("GUI " + name + " 第 " + (row + 1) + " 行必须正好包含 9 个字符");
            }
            for (char character : line.toCharArray()) {
                if (character == ' ') continue;
                ConfigurationSection icon = icons.getConfigurationSection(String.valueOf(character));
                if (icon == null) {
                    plugin.getLogger().warning("GUI " + name + " 的字符 '" + character + "' 未在 Icons 中定义");
                    continue;
                }
                if (icon.getString("Type", "static").equalsIgnoreCase("title")) {
                    hasDynamicTitle = true;
                }
                ConfigurationSection display = icon.getConfigurationSection("Display");
                if (display != null) {
                    String material = display.getString("Material", "PAPER");
                    if (!material.contains("%") && Material.matchMaterial(material) == null) {
                        plugin.getLogger().warning("GUI " + name + " 的字符 '" + character + "' 使用了无效材质: " + material);
                    }
                }
            }
        }
        if (requireDynamicTitles && !hasDynamicTitle) {
            plugin.getLogger().warning("GUI " + name + " 至少需要一个 Type: title 的图标字符");
        }
    }
    private Map<String, TitleDefinition> parseTitles() {
        Map<String, TitleDefinition> result = new LinkedHashMap<>();
        ConfigurationSection root = titles.getConfigurationSection("titles");
        if (root == null) {
            return result;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            Material icon = Material.matchMaterial(section.getString("icon", "NAME_TAG"));
            if (icon == null || icon.isAir()) {
                icon = Material.NAME_TAG;
            }

            List<TitleDefinition.Buff> buffs = new ArrayList<>();
            ConfigurationSection buffSection = section.getConfigurationSection("buffs");
            if (buffSection != null) {
                for (String key : buffSection.getKeys(false)) {
                    PotionEffectType type = PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
                    if (type == null) {
                        plugin.getLogger().warning("未知药水效果: " + key + " (称号 " + id + ")");
                        continue;
                    }
                    buffs.add(new TitleDefinition.Buff(
                            type,
                            Math.max(0, buffSection.getInt(key + ".amplifier", 0)),
                            buffSection.getBoolean(key + ".particles", false),
                            buffSection.getBoolean(key + ".icon", true)
                    ));
                }
            }

            ConfigurationSection shop = section.getConfigurationSection("shop");
            boolean shopEnabled = shop != null && shop.getBoolean("enabled", false);
            List<TitleDefinition.ItemRequirement> itemRequirements = parseItemRequirements(shop, id);
            List<TitleDefinition.PapiCondition> papiConditions = parsePapiConditions(shop, id);
            TitleDefinition.CostType costType = TitleDefinition.CostType.parse(
                    shop == null ? "free" : shop.getString("type", "free"));
            if (shopEnabled && costType == TitleDefinition.CostType.ITEM && itemRequirements.isEmpty()) {
                plugin.getLogger().warning("称号 " + id + " 使用 item 兑换类型，但没有有效的 items 配置");
            }
            if (shopEnabled && costType == TitleDefinition.CostType.PAPI && papiConditions.isEmpty()) {
                plugin.getLogger().warning("称号 " + id + " 使用 papi 获取类型，但没有有效的 papi-conditions 配置");
            }
            TitleDefinition.Shop shopInfo = new TitleDefinition.Shop(
                    shopEnabled,
                    shop != null && shop.getBoolean("display", shopEnabled),
                    costType,
                    shop == null ? 0 : Math.max(0, shop.getDouble("price", 0)),
                    shop == null ? "" : shop.getString("permission", ""),
                    shop == null ? "" : shop.getString("bypass-permission", ""),
                    shop == null ? "" : shop.getString("requirement-display", ""),
                    itemRequirements,
                    papiConditions
            );

            result.put(id, new TitleDefinition(
                    id,
                    section.getString("name", id),
                    section.getStringList("description"),
                    icon,
                    List.copyOf(buffs),
                    shopInfo,
                    false
            ));
        }
        return result;
    }

    private List<TitleDefinition.ItemRequirement> parseItemRequirements(
            ConfigurationSection shop,
            String titleId) {
        if (shop == null) return List.of();
        Map<String, TitleDefinition.ItemRequirement> result = new LinkedHashMap<>();
        for (Map<?, ?> entry : shop.getMapList("items")) {
            TitleDefinition.ItemSource source = TitleDefinition.ItemSource.parse(text(entry, "source", "vanilla"));
            String itemId = text(entry, "id", "").trim();
            int amount = integer(entry.get("amount"), 0);
            String display = text(entry, "display", itemId);
            if (itemId.isBlank() || amount <= 0) {
                plugin.getLogger().warning("称号 " + titleId + " 的物品兑换配置缺少有效 id 或 amount");
                continue;
            }
            if (source == TitleDefinition.ItemSource.VANILLA) {
                Material material = Material.matchMaterial(itemId);
                if (material == null || !material.isItem() || material.isAir()) {
                    plugin.getLogger().warning("称号 " + titleId + " 使用了无效原版物品: " + itemId);
                    continue;
                }
                itemId = material.name();
            } else if (!itemId.contains(":")) {
                plugin.getLogger().warning("称号 " + titleId + " 的 CraftEngine 物品必须使用 namespace:id: " + itemId);
                continue;
            }
            TitleDefinition.ItemRequirement requirement = new TitleDefinition.ItemRequirement(
                    source,
                    itemId,
                    amount,
                    display
            );
            if (result.putIfAbsent(requirement.key(), requirement) != null) {
                plugin.getLogger().warning("称号 " + titleId + " 重复配置了物品: " + itemId);
            }
        }
        return List.copyOf(result.values());
    }

    private static String text(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private List<TitleDefinition.PapiCondition> parsePapiConditions(
            ConfigurationSection shop,
            String titleId) {
        if (shop == null) return List.of();
        List<TitleDefinition.PapiCondition> result = new ArrayList<>();
        for (Map<?, ?> entry : shop.getMapList("papi-conditions")) {
            String placeholder = text(entry, "placeholder", "").trim();
            TitleDefinition.NumericOperator operator = TitleDefinition.NumericOperator.parse(
                    text(entry, "operator", ">="));
            String expectedText = text(entry, "value", "").trim();
            String display = text(entry, "display", placeholder);
            if (placeholder.isBlank() || !placeholder.contains("%")) {
                plugin.getLogger().warning("称号 " + titleId + " 的 PAPI 条件缺少有效 placeholder: " + placeholder);
                continue;
            }
            if (operator == null) {
                plugin.getLogger().warning("称号 " + titleId + " 使用了无效 PAPI 数值运算符");
                continue;
            }
            try {
                result.add(new TitleDefinition.PapiCondition(
                        placeholder,
                        operator,
                        new BigDecimal(expectedText),
                        display
                ));
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("称号 " + titleId + " 的 PAPI 条件 value 不是有效数值: " + expectedText);
            }
        }
        return List.copyOf(result);
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public YamlConfiguration storage() {
        return storage;
    }

    public YamlConfiguration gui(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "warehouse" -> warehouseGui;
            case "shop" -> shopGui;
            case "custom" -> customGui;
            default -> throw new IllegalArgumentException("Unknown GUI: " + name);
        };
    }

    public YamlConfiguration language() {
        return language;
    }

    public Map<String, TitleDefinition> definitions() {
        return definitions;
    }
}
