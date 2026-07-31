package github.balncesea.cloudTitle.gui;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.PlayerTitleData;
import github.balncesea.cloudTitle.model.TitleDefinition;
import github.balncesea.cloudTitle.service.EconomyService;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.PlaceholderConditionService;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuManager implements Listener {
    private static final Map<String, String> DEFAULT_POTION_NAMES = Map.ofEntries(
            Map.entry("speed", "速度"),
            Map.entry("slowness", "缓慢"),
            Map.entry("haste", "急迫"),
            Map.entry("mining_fatigue", "挖掘疲劳"),
            Map.entry("strength", "力量"),
            Map.entry("instant_health", "瞬间治疗"),
            Map.entry("instant_damage", "瞬间伤害"),
            Map.entry("jump_boost", "跳跃提升"),
            Map.entry("nausea", "反胃"),
            Map.entry("regeneration", "生命恢复"),
            Map.entry("resistance", "抗性提升"),
            Map.entry("fire_resistance", "抗火"),
            Map.entry("water_breathing", "水下呼吸"),
            Map.entry("invisibility", "隐身"),
            Map.entry("blindness", "失明"),
            Map.entry("night_vision", "夜视"),
            Map.entry("hunger", "饥饿"),
            Map.entry("weakness", "虚弱"),
            Map.entry("poison", "中毒"),
            Map.entry("wither", "凋零"),
            Map.entry("health_boost", "生命提升"),
            Map.entry("absorption", "伤害吸收"),
            Map.entry("saturation", "饱和"),
            Map.entry("glowing", "发光"),
            Map.entry("levitation", "飘浮"),
            Map.entry("luck", "幸运"),
            Map.entry("unluck", "霉运"),
            Map.entry("slow_falling", "缓降"),
            Map.entry("conduit_power", "潮涌能量"),
            Map.entry("dolphins_grace", "海豚的恩惠"),
            Map.entry("bad_omen", "不祥之兆"),
            Map.entry("hero_of_the_village", "村庄英雄"),
            Map.entry("darkness", "黑暗"),
            Map.entry("trial_omen", "试炼之兆"),
            Map.entry("raid_omen", "袭击之兆"),
            Map.entry("wind_charged", "蓄风"),
            Map.entry("weaving", "盘丝"),
            Map.entry("oozing", "渗浆"),
            Map.entry("infested", "寄生"),
            Map.entry("breath_of_the_nautilus", "鹦鹉螺之息")
    );
    private static final String[] ROMAN_LEVELS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private enum Type { WAREHOUSE, SHOP, CUSTOM }
    private enum Input { NAME, DESCRIPTION }

    private record Draft(String name, String description) {
        Draft withName(String value) { return new Draft(value, description); }
        Draft withDescription(String value) { return new Draft(name, value); }
    }

    private record BoundActions(Map<String, List<String>> actions, String titleId, Map<String, String> variables) {
        List<String> forClick(ClickType click) {
            String key;
            if (click.isShiftClick() && click.isLeftClick()) key = "shift-left";
            else if (click.isShiftClick() && click.isRightClick()) key = "shift-right";
            else if (click.isLeftClick()) key = "left";
            else if (click.isRightClick()) key = "right";
            else key = "all";
            return actions.getOrDefault(key, actions.getOrDefault("all", List.of()));
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Type type;
        private final int page;
        private final long clickCooldownNanos;
        private final Map<Integer, BoundActions> actions = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(Type type, int page, long clickCooldownNanos) {
            this.type = type;
            this.page = page;
            this.clickCooldownNanos = clickCooldownNanos;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CloudTitle plugin;
    private final TitleService titles;
    private final MessageService messages;
    private final EconomyService economy;
    private final PlaceholderConditionService placeholderConditions;
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, Input> pending = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> capturedInputs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClicks = new ConcurrentHashMap<>();

    public MenuManager(CloudTitle plugin, TitleService titles, MessageService messages,
                       EconomyService economy, PlaceholderConditionService placeholderConditions) {
        this.plugin = plugin;
        this.titles = titles;
        this.messages = messages;
        this.economy = economy;
        this.placeholderConditions = placeholderConditions;
    }

    public void openWarehouse(Player player, int page) {
        if (!ensureReady(player)) return;
        renderPaged(player, Type.WAREHOUSE, new ArrayList<>(titles.owned(player.getUniqueId())), page);
    }

    public void openShop(Player player, int page) {
        if (!ensureReady(player)) return;
        renderPaged(player, Type.SHOP, new ArrayList<>(titles.shop(player.getUniqueId())), page);
    }

    public void openCustom(Player player) {
        if (!ensureReady(player)) return;
        if (!plugin.getConfig().getBoolean("custom-title.enabled", true)) {
            messages.send(player, "custom-disabled");
            return;
        }
        String permission = plugin.getConfig().getString("custom-title.permission", "cloudtitle.custom");
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            messages.send(player, "no-permission");
            return;
        }

        Draft draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft("", ""));
        Map<String, String> variables = commonVariables(player, 0, 0);
        variables.put("custom_name", draft.name().isBlank() ? "<dark_gray>尚未设置" : draft.name());
        variables.put("custom_description", draft.description().isBlank() ? "<dark_gray>尚未设置" : draft.description());
        variables.put("custom_cost", customCost(player));
        renderStatic(player, Type.CUSTOM, plugin.configs().gui("custom"), variables);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                player.closeInventory();
            }
        }
        pending.clear();
        capturedInputs.clear();
        drafts.clear();
        lastClicks.clear();
    }

    private void renderPaged(Player player, Type type, List<TitleDefinition> entries, int requestedPage) {
        String configName = type == Type.WAREHOUSE ? "warehouse" : "shop";
        YamlConfiguration config = plugin.configs().gui(configName);
        List<String> layout = layout(config, configName);
        ConfigurationSection icons = config.getConfigurationSection("Icons");
        if (icons == null) {
            plugin.getLogger().warning("GUI " + configName + " 缺少 Icons 节点");
            return;
        }

        List<Integer> dynamicSlots = new ArrayList<>();
        for (int slot = 0; slot < layout.size() * 9; slot++) {
            ConfigurationSection icon = icon(icons, characterAt(layout, slot));
            if (icon != null && icon.getString("Type", "static").equalsIgnoreCase("title")) {
                dynamicSlots.add(slot);
            }
        }
        if (dynamicSlots.isEmpty()) {
            plugin.getLogger().warning("GUI " + configName + " 没有 Type: title 的动态字符");
            return;
        }

        int maxPage = Math.max(0, (entries.size() - 1) / dynamicSlots.size());
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> common = commonVariables(player, page, maxPage);
        MenuHolder holder = createInventory(player, type, page, config, layout, common);
        Inventory inventory = holder.inventory;

        renderStaticIcons(inventory, holder, config, layout, common, page, maxPage);
        PlayerTitleData data = titles.data(player.getUniqueId());
        for (int index = 0; index < dynamicSlots.size(); index++) {
            int entryIndex = page * dynamicSlots.size() + index;
            if (entryIndex >= entries.size()) break;
            int slot = dynamicSlots.get(index);
            TitleDefinition title = entries.get(entryIndex);
            ConfigurationSection icon = icon(icons, characterAt(layout, slot));
            boolean selected = type == Type.WAREHOUSE && title.id().equals(data.selected());
            Map<String, String> variables = new HashMap<>(common);
            variables.putAll(titleVariables(player, title, selected, data));

            ConfigurationSection display = section(icon, "Display", "display");
            ItemStack item = configuredItem(display, variables, title.icon());
            if (selected) applySelected(item, section(icon, "Selected", "selected"), variables);
            inventory.setItem(slot, item);
            bind(holder, slot, icon, title.id(), variables);
        }
        player.openInventory(inventory);
    }

    private void renderStatic(Player player, Type type, YamlConfiguration config, Map<String, String> variables) {
        List<String> layout = layout(config, type.name().toLowerCase(Locale.ROOT));
        MenuHolder holder = createInventory(player, type, 0, config, layout, variables);
        renderStaticIcons(holder.inventory, holder, config, layout, variables, 0, 0);
        player.openInventory(holder.inventory);
    }

    private MenuHolder createInventory(Player player, Type type, int page, YamlConfiguration config,
                                       List<String> layout, Map<String, String> variables) {
        long cooldownMillis = Math.max(0, Math.min(5000,
                config.getLong("Options.Click-Cooldown-Millis", 300)));
        MenuHolder holder = new MenuHolder(type, page, cooldownMillis * 1_000_000L);
        String title = replace(config.getString("Title", "云称号"), variables);
        holder.inventory = Bukkit.createInventory(holder, layout.size() * 9, guiComponent(title));
        return holder;
    }

    private void renderStaticIcons(Inventory inventory, MenuHolder holder, YamlConfiguration config,
                                   List<String> layout, Map<String, String> variables, int page, int maxPage) {
        ConfigurationSection icons = config.getConfigurationSection("Icons");
        if (icons == null) return;
        for (int slot = 0; slot < layout.size() * 9; slot++) {
            ConfigurationSection icon = icon(icons, characterAt(layout, slot));
            if (icon == null || icon.getString("Type", "static").equalsIgnoreCase("title")) continue;
            Map<String, List<String>> actions = readActions(icon);
            if (!shouldRender(actions, page, maxPage)) continue;
            ConfigurationSection display = section(icon, "Display", "display");
            if (display == null) continue;
            inventory.setItem(slot, configuredItem(display, variables, Material.PAPER));
            bind(holder, slot, icon, null, variables);
        }
    }

    private void bind(MenuHolder holder, int slot, ConfigurationSection icon, String titleId,
                      Map<String, String> variables) {
        Map<String, List<String>> actions = readActions(icon);
        if (!actions.isEmpty()) {
            holder.actions.put(slot, new BoundActions(actions, titleId, Map.copyOf(variables)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        BoundActions bound = holder.actions.get(event.getRawSlot());
        if (bound == null) return;
        UUID uuid = player.getUniqueId();
        long now = System.nanoTime();
        Long previous = lastClicks.get(uuid);
        if (previous != null && now - previous < holder.clickCooldownNanos) return;
        lastClicks.put(uuid, now);
        for (String configuredAction : bound.forClick(event.getClick())) {
            executeAction(player, holder, bound, configuredAction);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        capturedInputs.remove(uuid);
        drafts.remove(uuid);
        lastClicks.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void legacyChat(AsyncPlayerChatEvent event) {
        Input input = pending.get(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        event.getRecipients().clear();
        captureInput(event.getPlayer(), input, event.getMessage().trim());
    }

    private void captureInput(Player player, Input input, String text) {
        UUID uuid = player.getUniqueId();
        if (!capturedInputs.add(uuid)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Input current = pending.remove(uuid);
                if (current != null && player.isOnline()) {
                    acceptInput(player, current, text);
                }
            } finally {
                capturedInputs.remove(uuid);
            }
        });
    }

    private void executeAction(Player player, MenuHolder holder, BoundActions bound, String configuredAction) {
        String action = replace(configuredAction, bound.variables()).trim();
        String normalized = action.toLowerCase(Locale.ROOT);
        if (normalized.equals("close")) {
            player.closeInventory();
        } else if (normalized.equals("menu: previous")) {
            if (holder.type == Type.WAREHOUSE) openWarehouse(player, holder.page - 1);
            else if (holder.type == Type.SHOP) openShop(player, holder.page - 1);
        } else if (normalized.equals("menu: next")) {
            if (holder.type == Type.WAREHOUSE) openWarehouse(player, holder.page + 1);
            else if (holder.type == Type.SHOP) openShop(player, holder.page + 1);
        } else if (normalized.equals("menu: warehouse")) {
            openWarehouse(player, 0);
        } else if (normalized.equals("menu: shop")) {
            openShop(player, 0);
        } else if (normalized.equals("menu: custom")) {
            openCustom(player);
        } else if (normalized.equals("title: clear")) {
            titles.clear(player);
            player.closeInventory();
        } else if (normalized.equals("title: select") && bound.titleId() != null) {
            titles.select(player, bound.titleId());
            player.closeInventory();
        } else if (normalized.equals("title: buy") && bound.titleId() != null) {
            titles.purchase(player, bound.titleId(), () -> openShop(player, holder.page));
        } else if (normalized.equals("custom: edit-name")) {
            beginInput(player, Input.NAME);
        } else if (normalized.equals("custom: edit-description")) {
            beginInput(player, Input.DESCRIPTION);
        } else if (normalized.equals("custom: create")) {
            confirm(player);
        } else if (normalized.startsWith("message: ")) {
            player.sendMessage(messages.legacy(action.substring(9).trim()));
        } else if (normalized.startsWith("player: ")) {
            player.performCommand(action.substring(8).trim().replaceFirst("^/", ""));
        } else if (normalized.startsWith("console: ")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(9).trim().replaceFirst("^/", ""));
        } else if (normalized.startsWith("sound: ")) {
            playSound(player, action.substring(7).trim());
        } else {
            plugin.getLogger().warning("未知 GUI 动作: " + configuredAction);
        }
    }

    private void beginInput(Player player, Input input) {
        UUID uuid = player.getUniqueId();
        capturedInputs.remove(uuid);
        pending.put(uuid, input);
        player.closeInventory();
        messages.send(player, input == Input.NAME ? "custom-input-name" : "custom-input-description");
    }

    private void acceptInput(Player player, Input input, String text) {
        if (text.equalsIgnoreCase("cancel")) {
            messages.send(player, "custom-input-cancelled");
            openCustom(player);
            return;
        }
        int max = plugin.getConfig().getInt(
                input == Input.NAME ? "custom-title.max-name-length" : "custom-title.max-description-length",
                input == Input.NAME ? 16 : 64
        );
        if (text.isBlank() || text.codePointCount(0, text.length()) > max) {
            messages.send(player, "custom-invalid-length", Map.of("max", String.valueOf(max)));
            openCustom(player);
            return;
        }
        boolean allowMiniMessage = plugin.getConfig().getBoolean("custom-title.allow-minimessage", false);
        String safe = allowMiniMessage ? text : MessageService.escape(text);
        Draft draft = drafts.getOrDefault(player.getUniqueId(), new Draft("", ""));
        drafts.put(player.getUniqueId(), input == Input.NAME ? draft.withName(safe) : draft.withDescription(safe));
        openCustom(player);
    }

    private void confirm(Player player) {
        Draft draft = drafts.getOrDefault(player.getUniqueId(), new Draft("", ""));
        if (draft.name().isBlank()) {
            messages.send(player, "custom-name-required");
            return;
        }
        player.closeInventory();
        titles.createCustom(player, draft.name(), draft.description(), success -> {
            if (success) {
                drafts.remove(player.getUniqueId());
                openWarehouse(player, 0);
            } else {
                openCustom(player);
            }
        });
    }

    private ItemStack configuredItem(ConfigurationSection display, Map<String, String> variables, Material fallback) {
        if (display == null) return new ItemStack(fallback);
        String materialName = replace(display.getString("Material", fallback.name()), variables);
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) material = fallback;
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, display.getInt("Amount", 1))));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(guiComponent(replace(display.getString("Name", " "), variables)));
        meta.setLore(expandLore(display.getStringList("Lore"), variables));
        if (display.contains("Custom-Model-Data")) {
            meta.setCustomModelData(display.getInt("Custom-Model-Data"));
        }
        if (display.getBoolean("Glow", false)) {
            applyGlow(meta);
        }
        for (String flag : display.getStringList("Item-Flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("未知物品标记: " + flag);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private void applySelected(ItemStack item, ConfigurationSection selected, Map<String, String> variables) {
        if (selected == null) return;
        ItemMeta meta = item.getItemMeta();
        if (selected.getBoolean("Glow", true)) applyGlow(meta);
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.addAll(expandLore(selected.getStringList("Lore-Append"), variables));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private List<String> expandLore(List<String> configuredLore, Map<String, String> variables) {
        List<String> result = new ArrayList<>();
        for (String line : configuredLore) {
            String multilineVariable = line.contains("%title_description%")
                    ? "title_description"
                    : line.contains("%title_attributes%") ? "title_attributes" : null;
            if (multilineVariable != null) {
                String value = variables.getOrDefault(multilineVariable, "");
                String[] lines = value.split("\\n", -1);
                for (String expandedLine : lines) {
                    Map<String, String> expanded = new HashMap<>(variables);
                    expanded.put(multilineVariable, expandedLine);
                    result.add(guiComponent(replace(line, expanded)));
                }
            } else {
                result.add(guiComponent(replace(line, variables)));
            }
        }
        return result;
    }

    private Map<String, String> commonVariables(Player player, int page, int maxPage) {
        Map<String, String> variables = new HashMap<>();
        PlayerTitleData data = titles.data(player.getUniqueId());
        variables.put("player", MessageService.escape(player.getName()));
        variables.put("page", String.valueOf(page + 1));
        variables.put("max_page", String.valueOf(maxPage + 1));
        variables.put("owned_count", String.valueOf(data == null ? 0 : data.owned().size()));
        return variables;
    }

    private Map<String, String> titleVariables(
            Player player,
            TitleDefinition title,
            boolean selected,
            PlayerTitleData data) {
        Map<String, String> variables = new HashMap<>();
        variables.put("title_id", title.id());
        variables.put("title_name", title.name());
        variables.put("title_material", title.icon().name());
        variables.put("title_description", title.description().isEmpty()
                ? "<dark_gray>暂无描述"
                : String.join("\n", title.description()));
        variables.put("title_buffs", buffDisplay(title));
        variables.put("title_attributes", attributeDisplay(title));
        String requirement = requirementDisplay(player, title, data);
        variables.put("title_cost", requirement);
        variables.put("title_requirement", requirement);
        variables.put("title_status", selected ? "<green>佩戴中" : "<gray>未佩戴");
        return variables;
    }

    private String buffDisplay(TitleDefinition title) {
        if (title.buffs().isEmpty()) {
            return plugin.configs().language().getString("buff-display.none", "<gray>无增益</gray>");
        }
        String format = plugin.configs().language().getString(
                "buff-display.format",
                "<aqua>%effect%</aqua> <white>%level%</white>"
        );
        String separator = plugin.configs().language().getString(
                "buff-display.separator",
                "<dark_gray>、</dark_gray> "
        );
        return title.buffs().stream()
                .map(buff -> format
                        .replace("%effect%", localizedPotionName(buff.type().getKey().getKey()))
                        .replace("%level%", buffLevel(buff.amplifier() + 1)))
                .reduce((left, right) -> left + separator + right)
                .orElse(plugin.configs().language().getString("buff-display.none", "<gray>无增益</gray>"));
    }

    private String attributeDisplay(TitleDefinition title) {
        TitleDefinition.Attributes attributes = title.attributes();
        if (attributes == null || attributes.isEmpty()) {
            return plugin.configs().language().getString("attribute-display.none", "<gray>无属性加成</gray>");
        }
        List<String> providers = new ArrayList<>();
        addAttributeProvider(providers, "attribute-plus", "<gold>AP</gold>", attributes.attributePlus());
        addAttributeProvider(providers, "sx-attribute", "<aqua>SX</aqua>", attributes.sxAttribute());
        String separator = plugin.configs().language().getString(
                "attribute-display.provider-separator", "<dark_gray>；</dark_gray> ");
        return String.join(separator, providers);
    }

    private void addAttributeProvider(List<String> providers, String key, String fallbackName, List<String> lines) {
        if (lines.isEmpty()) return;
        String entryFormat = plugin.configs().language().getString(
                "attribute-display.entry-format", "<white>%attribute%</white>");
        String entrySeparator = plugin.configs().language().getString(
                "attribute-display.entry-separator", "<dark_gray>、</dark_gray> ");
        String entries = lines.stream()
                .map(line -> entryFormat.replace("%attribute%", line))
                .reduce((left, right) -> left + entrySeparator + right)
                .orElse("");
        String providerName = plugin.configs().language().getString(
                "attribute-display." + key + "-name", fallbackName);
        String providerFormat = plugin.configs().language().getString(
                "attribute-display.provider-format", "%provider% <dark_gray>│</dark_gray> %attributes%");
        providers.add(providerFormat
                .replace("%provider%", providerName)
                .replace("%attributes%", entries));
    }

    private String localizedPotionName(String key) {
        String fallback = DEFAULT_POTION_NAMES.getOrDefault(key, key);
        return plugin.configs().language().getString("potion-effects." + key, fallback);
    }

    private static String buffLevel(int level) {
        return level > 0 && level <= ROMAN_LEVELS.length ? ROMAN_LEVELS[level - 1] : level + " 级";
    }

    private String requirementDisplay(Player player, TitleDefinition title, PlayerTitleData data) {
        TitleDefinition.Shop shop = title.shop();
        Map<String, Integer> progress = data == null ? Map.of() : data.itemProgress(title.id());
        String itemProgress = shop.items().stream()
                .map(requirement -> requirement.display() + " <white>"
                        + Math.min(requirement.amount(), progress.getOrDefault(requirement.key(), 0))
                        + "/" + requirement.amount() + "</white>")
                .reduce((left, right) -> left + "<dark_gray>、</dark_gray> " + right)
                .orElse("<gray>未配置物品</gray>");
        String papiConditions = papiConditionDisplay(player, shop);
        if (!shop.requirementDisplay().isBlank()) {
            return shop.requirementDisplay()
                    .replace("%progress%", itemProgress)
                    .replace("%items%", itemProgress)
                    .replace("%papi_conditions%", papiConditions);
        }
        String path;
        String fallback;
        switch (shop.type()) {
            case MONEY -> {
                path = "shop-requirements.money";
                fallback = "<gold><bold>金币购买</bold></gold> <dark_gray>·</dark_gray> <white>%amount% 金币</white>";
            }
            case POINTS -> {
                path = "shop-requirements.points";
                fallback = "<aqua><bold>点券兑换</bold></aqua> <dark_gray>·</dark_gray> <white>%amount% 点券</white>";
            }
            case PERMISSION -> {
                path = "shop-requirements.permission";
                fallback = "<light_purple><bold>权限解锁</bold></light_purple> <dark_gray>·</dark_gray> <white>满足指定权限</white>";
            }
            case ITEM -> {
                path = "shop-requirements.item";
                fallback = "<yellow><bold>物品提交</bold></yellow> <dark_gray>·</dark_gray> %items%";
            }
            case PAPI -> {
                path = "shop-requirements.papi";
                fallback = "<blue><bold>变量条件</bold></blue> <dark_gray>·</dark_gray> %papi_conditions%";
            }
            case FREE -> {
                path = "shop-requirements.free";
                fallback = "<green><bold>免费领取</bold></green>";
            }
            default -> throw new IllegalStateException("Unexpected shop type: " + shop.type());
        }
        return plugin.configs().language().getString(path, fallback)
                .replace("%amount%", formatAmount(shop.price()))
                .replace("%permission%", MessageService.escape(shop.permission()))
                .replace("%items%", itemProgress)
                .replace("%progress%", itemProgress)
                .replace("%papi_conditions%", papiConditions);
    }

    private String papiConditionDisplay(Player player, TitleDefinition.Shop shop) {
        if (shop.papiConditions().isEmpty()) return "<red>未配置变量条件</red>";
        PlaceholderConditionService.Evaluation evaluation = placeholderConditions.evaluate(
                player, shop.papiConditions());
        if (!evaluation.available()) return "<red>PlaceholderAPI 不可用</red>";
        return evaluation.results().stream().map(result -> {
            String status = result.passed() ? "<green>✔</green>" : "<red>✘</red>";
            String actual = result.actualValue() == null
                    ? "<red>非数值: " + MessageService.escape(result.rawValue()) + "</red>"
                    : "<white>" + result.actualValue().stripTrailingZeros().toPlainString() + "</white>";
            String expected = result.condition().expected().stripTrailingZeros().toPlainString();
            return status + " " + result.condition().display()
                    + " <dark_gray>(</dark_gray>" + actual + " <gray>"
                    + result.condition().operator().symbol() + " " + expected + "</gray><dark_gray>)</dark_gray>";
        }).reduce((left, right) -> left + "<dark_gray>、</dark_gray> " + right)
                .orElse("<red>未配置变量条件</red>");
    }

    private static String formatAmount(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private String customCost(Player player) {
        var config = plugin.getConfig();
        String bypass = config.getString("custom-title.bypass-permission", "");
        if (!bypass.isBlank() && player.hasPermission(bypass)) return "<green>免费（权限豁免）";
        return economy.format(
                TitleDefinition.CostType.parse(config.getString("custom-title.currency", "money")),
                config.getDouble("custom-title.price", 0),
                ""
        );
    }

    private List<String> layout(YamlConfiguration config, String name) {
        List<String> configured = config.getStringList("Layout");
        if (configured.isEmpty()) configured = List.of("         ");
        int rows = Math.max(1, Math.min(6, configured.size()));
        List<String> normalized = new ArrayList<>(rows);
        for (int row = 0; row < rows; row++) {
            String line = configured.get(row);
            if (line.length() != 9) {
                plugin.getLogger().warning("GUI " + name + " 第 " + (row + 1) + " 行长度不是 9，已自动补齐或截断");
            }
            normalized.add((line + "         ").substring(0, 9));
        }
        return normalized;
    }

    private Map<String, List<String>> readActions(ConfigurationSection icon) {
        ConfigurationSection section = section(icon, "Actions", "actions");
        if (section == null) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            List<String> actions = values(value);
            if (!actions.isEmpty()) result.put(key.toLowerCase(Locale.ROOT), actions);
        }
        return result;
    }

    private static List<String> values(Object value) {
        if (value instanceof String string) return List.of(string);
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf).toList();
        return List.of();
    }

    private static boolean shouldRender(Map<String, List<String>> actions, int page, int maxPage) {
        List<String> flat = actions.values().stream().flatMap(Collection::stream).map(String::toLowerCase).toList();
        if (flat.stream().anyMatch(value -> value.trim().equals("menu: previous")) && page <= 0) return false;
        return flat.stream().noneMatch(value -> value.trim().equals("menu: next")) || page < maxPage;
    }

    private void playSound(Player player, String specification) {
        String[] parts = specification.split("\\s+");
        try {
            String soundName = parts[0].toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(soundName.contains(":") ? soundName : "minecraft:" + soundName);
            Sound sound = key == null ? null : Registry.SOUNDS.get(key);
            if (sound == null) throw new IllegalArgumentException("Unknown sound");
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("无效 GUI 音效: " + specification);
        }
    }

    private boolean ensureReady(Player player) {
        if (titles.ready(player.getUniqueId())) return true;
        messages.send(player, "loading");
        return false;
    }

    private String guiComponent(String value) {
        return messages.legacy("<!italic>" + value);
    }

    private static void applyGlow(ItemMeta meta) {
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    private static ConfigurationSection icon(ConfigurationSection icons, char character) {
        return icons.getConfigurationSection(String.valueOf(character));
    }

    private static ConfigurationSection section(ConfigurationSection parent, String primary, String fallback) {
        if (parent == null) return null;
        ConfigurationSection section = parent.getConfigurationSection(primary);
        return section != null ? section : parent.getConfigurationSection(fallback);
    }

    private static char characterAt(List<String> layout, int slot) {
        return layout.get(slot / 9).charAt(slot % 9);
    }

    private static String replace(String source, Map<String, String> variables) {
        String value = source == null ? "" : source;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }
}
