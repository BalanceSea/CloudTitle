package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.hook.AttributeManager;
import github.balncesea.cloudTitle.model.PlayerTitleData;
import github.balncesea.cloudTitle.model.TitleDefinition;
import github.balncesea.cloudTitle.storage.TitleRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** 称号业务服务：协调玩家缓存、存储契约、获取流程、Buff 和第三方属性效果。 */
public final class TitleService {
    private final CloudTitle plugin;
    private final TitleRepository repository;
    private final TitleCatalogService catalog;
    private final MessageService messages;
    private final EconomyService economy;
    private final ItemExchangeService itemExchange;
    private final PlaceholderConditionService placeholderConditions;
    private final AttributeManager attributes;
    private final Map<UUID, PlayerTitleData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PotionEffectType, Integer>> activeBuffs = new ConcurrentHashMap<>();
    private final Set<String> itemSubmissions = ConcurrentHashMap.newKeySet();
    private final String serverId;

    public TitleService(CloudTitle plugin, TitleRepository repository, TitleCatalogService catalog,
                        MessageService messages,
                        EconomyService economy, ItemExchangeService itemExchange,
                        PlaceholderConditionService placeholderConditions, AttributeManager attributes) {
        this.plugin = plugin; this.repository = repository; this.catalog = catalog; this.messages = messages; this.economy = economy;
        this.itemExchange = itemExchange;
        this.placeholderConditions = placeholderConditions;
        this.attributes = attributes;
        this.serverId = plugin.getConfig().getString("server-id", "default");
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        repository.load(uuid).whenComplete((data, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) { plugin.getLogger().severe("加载 " + player.getName() + " 的称号失败: " + root(error).getMessage()); messages.send(player, "storage-error"); return; }
            if (plugin.getConfig().getBoolean("buffs.cross-server-cleanup", true)) clearTokens(player, decode(data.appliedBuffs()));
            cache.put(uuid, data);
            itemExchange.restorePending(player);
            applySelected(player, data);
        }));
    }

    public void unload(Player player) {
        UUID uuid = player.getUniqueId();
        clearActive(player);
        cache.remove(uuid);
        repository.setAppliedBuffs(uuid, "", serverId, true).exceptionally(error -> { log("清除退出 Buff 记录失败", error); return null; });
    }

    public boolean ready(UUID uuid) { return cache.containsKey(uuid); }
    public PlayerTitleData data(UUID uuid) { return cache.get(uuid); }
    public TitleDefinition definition(PlayerTitleData data, String id) {
        return catalog.find(data, id);
    }

    public TitleDefinition displayedDefinition(PlayerTitleData data) {
        return catalog.displayed(data);
    }

    public Collection<TitleDefinition> owned(UUID uuid) {
        return catalog.owned(uuid, cache);
    }

    public Collection<TitleDefinition> shop(UUID uuid) {
        return catalog.shop(uuid, cache);
    }

    public void select(Player player, String id) {
        PlayerTitleData data = cache.get(player.getUniqueId());
        if (data == null) { messages.send(player, "loading"); return; }
        TitleDefinition title = definition(data, id);
        if (title == null) { messages.send(player, "title-not-found", Map.of("id", MessageService.escape(id))); return; }
        if (!data.owned().contains(id)) { messages.send(player, "title-not-owned"); return; }
        repository.setSelected(player.getUniqueId(), id).whenComplete((unused, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) { messages.send(player, "storage-error"); log("保存佩戴称号失败", error); return; }
            data.selected(id); applySelected(player, data);
            messages.send(player, "title-equipped", Map.of("title", title.name()));
        }));
    }

    public void clear(Player player) {
        PlayerTitleData data = cache.get(player.getUniqueId()); if (data == null) { messages.send(player, "loading"); return; }
        repository.setSelected(player.getUniqueId(), null).whenComplete((unused, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) { messages.send(player, "storage-error"); return; }
            data.selected(null); clearActive(player); claim(player, ""); messages.send(player, "title-cleared");
        }));
    }

    public void purchase(Player player, String id, Runnable successUi) {
        PlayerTitleData data = cache.get(player.getUniqueId()); if (data == null) { messages.send(player, "loading"); return; }
        TitleDefinition title = plugin.configs().definitions().get(id);
        if (title == null || !title.shop().enabled()) { messages.send(player, "title-not-found", Map.of("id", MessageService.escape(id))); return; }
        if (data.owned().contains(id)) { messages.send(player, "title-owned"); return; }
        var shop = title.shop();
        boolean bypass = !shop.bypassPermission().isBlank() && player.hasPermission(shop.bypassPermission());
        if (shop.type() == TitleDefinition.CostType.PERMISSION && !bypass && (shop.permission().isBlank() || !player.hasPermission(shop.permission()))) {
            messages.send(player, "requirement-missing"); return;
        }
        if (shop.type() == TitleDefinition.CostType.ITEM && !bypass) {
            submitItems(player, title, data, successUi);
            return;
        }
        if (shop.type() == TitleDefinition.CostType.PAPI && !bypass) {
            if (shop.papiConditions().isEmpty()) {
                messages.send(player, "papi-config-error");
                return;
            }
            PlaceholderConditionService.Evaluation evaluation = placeholderConditions.evaluate(
                    player, shop.papiConditions());
            if (!evaluation.available()) {
                messages.send(player, "papi-unavailable");
                return;
            }
            if (!evaluation.passed()) {
                messages.send(player, "papi-requirement-missing");
                return;
            }
        }
        EconomyService.Result charged = bypass ? EconomyService.Result.SUCCESS : economy.charge(player, shop.type(), shop.price());
        if (charged != EconomyService.Result.SUCCESS) {
            String key = charged == EconomyService.Result.UNAVAILABLE ? "economy-unavailable" : charged == EconomyService.Result.INSUFFICIENT
                    ? (shop.type() == TitleDefinition.CostType.POINTS ? "insufficient-points" : "insufficient-money") : "purchase-failed";
            messages.send(player, key, Map.of("amount", String.valueOf(shop.price()))); return;
        }
        repository.grant(player.getUniqueId(), id).whenComplete((unused, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) { if (!bypass) economy.refund(player, shop.type(), shop.price()); messages.send(player, "storage-error"); log("购买称号失败", error); return; }
            data.grant(id); messages.send(player, "title-obtained", Map.of("title", title.name())); successUi.run();
        }));
    }

    private void submitItems(Player player, TitleDefinition title, PlayerTitleData data, Runnable successUi) {
        if (title.shop().items().isEmpty()) {
            messages.send(player, "item-config-error");
            return;
        }
        String lockKey = player.getUniqueId() + ":" + title.id();
        if (!itemSubmissions.add(lockKey)) {
            messages.send(player, "item-submit-processing");
            return;
        }

        ItemExchangeService.Removal removal = itemExchange.removeAvailable(
                player,
                title.shop().items(),
                data.itemProgress(title.id())
        );
        if (removal.craftEngineUnavailable()) {
            itemSubmissions.remove(lockKey);
            messages.send(player, "craftengine-unavailable");
            return;
        }
        if (removal.empty()) {
            itemSubmissions.remove(lockKey);
            messages.send(player, "item-submit-empty", Map.of(
                    "progress", itemProgress(title, data.itemProgress(title.id()))
            ));
            return;
        }

        repository.submitItems(
                player.getUniqueId(),
                title.id(),
                title.shop().items(),
                removal.offered()
        ).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            itemSubmissions.remove(lockKey);
            if (error != null) {
                itemExchange.refund(player, removal, Map.of());
                messages.send(player, "storage-error");
                log("保存物品提交进度失败", error);
                return;
            }
            itemExchange.refund(player, removal, result.accepted());
            int submitted = result.accepted().values().stream().mapToInt(Integer::intValue).sum();
            if (result.completed()) {
                data.grant(title.id());
                data.clearItemProgress(title.id());
                messages.send(player, "title-obtained", Map.of("title", title.name()));
            } else {
                data.itemProgress(title.id(), result.progress());
                messages.send(player, "item-submit-progress", Map.of(
                        "submitted", String.valueOf(submitted),
                        "progress", itemProgress(title, result.progress())
                ));
            }
            if (player.isOnline()) successUi.run();
        }));
    }

    private static String itemProgress(TitleDefinition title, Map<String, Integer> progress) {
        return title.shop().items().stream()
                .map(requirement -> requirement.display() + " <white>"
                        + Math.min(requirement.amount(), progress.getOrDefault(requirement.key(), 0))
                        + "/" + requirement.amount() + "</white>")
                .reduce((left, right) -> left + "<gray>、</gray>" + right)
                .orElse("<gray>无要求</gray>");
    }

    /**
     * 创建自定义称号。名称中的颜色代码会在写入前统一转换为 MiniMessage，
     * 因此数据库中的旧称号和新称号可以使用同一套显示流程。
     */
    public void createCustom(Player player, String name, String description, Consumer<Boolean> completion) {
        PlayerTitleData data = cache.get(player.getUniqueId());
        if (data == null) {
            messages.send(player, "loading");
            completion.accept(false);
            return;
        }
        var config = plugin.getConfig();
        TitleDefinition.CostType type = TitleDefinition.CostType.parse(config.getString("custom-title.currency", "money"));
        double price = config.getDouble("custom-title.price", 0);
        String bypassPermission = config.getString("custom-title.bypass-permission", "cloudtitle.custom.bypass");
        boolean bypass = !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
        EconomyService.Result charged = bypass ? EconomyService.Result.SUCCESS : economy.charge(player, type, price);
        if (charged != EconomyService.Result.SUCCESS) {
            String key = charged == EconomyService.Result.UNAVAILABLE ? "economy-unavailable" : charged == EconomyService.Result.INSUFFICIENT
                    ? (type == TitleDefinition.CostType.POINTS ? "insufficient-points" : "insufficient-money") : "purchase-failed";
            messages.send(player, key, Map.of("amount", String.valueOf(price))); completion.accept(false); return;
        }
        String id = "custom_" + player.getUniqueId().toString().substring(0, 8) + "_" + UUID.randomUUID().toString().substring(0, 8);
        boolean allowMiniMessage = config.getBoolean("custom-title.allow-minimessage", false);
        String titleName = buildCustomName(MessageService.colorizeName(name, allowMiniMessage));
        TitleDefinition title = new TitleDefinition(id, titleName, List.of(description), org.bukkit.Material.NAME_TAG,
                List.of(), TitleDefinition.Attributes.empty(), TitleDefinition.Shop.hidden(), true);
        repository.createCustom(player.getUniqueId(), title).whenComplete((unused, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) { if (!bypass) economy.refund(player, type, price); messages.send(player, "custom-failed"); log("创建自定义称号失败", error); completion.accept(false); return; }
            data.addCustom(title); messages.send(player, "custom-created"); completion.accept(true);
        }));
    }

    private String buildCustomName(String name) {
        String prefix = plugin.getConfig().getString("custom-title.name-prefix", "");
        String suffix = plugin.getConfig().getString("custom-title.name-suffix", "");
        return prefix + name + "<reset>" + suffix;
    }

    public void grant(UUID uuid, String id, Consumer<Boolean> callback) {
        if (!plugin.configs().definitions().containsKey(id)) { callback.accept(false); return; }
        repository.grant(uuid, id).whenComplete((unused, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error == null && cache.containsKey(uuid)) cache.get(uuid).grant(id); callback.accept(error == null);
        }));
    }
    public void revoke(UUID uuid, String id, Consumer<Boolean> callback) {
        repository.revoke(uuid, id).whenComplete((unused, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerTitleData data = cache.get(uuid);
            if (error == null && data != null) { data.revoke(id); if (id.equals(data.selected())) { data.selected(null); Player p = Bukkit.getPlayer(uuid); if (p != null) { clearActive(p); claim(p, ""); } } }
            callback.accept(error == null);
        }));
    }

    public void reapply(Player player) {
        PlayerTitleData data = cache.get(player.getUniqueId());
        if (data != null) applySelected(player, data);
    }
    public void reapplyAll() { for (Player player : Bukkit.getOnlinePlayers()) reapply(player); }
    public void refreshBuffs() {
        for (Player player : Bukkit.getOnlinePlayers()) { PlayerTitleData data = cache.get(player.getUniqueId()); if (data != null) applyEffects(player, definition(data, data.selected())); }
    }
    /** 佩戴切换时先清理旧来源，再应用新称号，避免跨服或切换叠加。 */
    private void applySelected(Player player, PlayerTitleData data) {
        clearActive(player);
        TitleDefinition title = definition(data, data.selected());
        if (title != null) attributes.apply(player, title.attributes());
        applyEffects(player, title);
        claim(player, encode(activeBuffs.get(player.getUniqueId())));
    }
    private void applyEffects(Player player, TitleDefinition title) {
        if (title == null || title.buffs().isEmpty()) return;
        int duration = Math.max(80, plugin.getConfig().getInt("buffs.refresh-ticks", 100) + 40);
        Map<PotionEffectType, Integer> tracked = activeBuffs.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        for (TitleDefinition.Buff buff : title.buffs()) {
            PotionEffect current = player.getPotionEffect(buff.type());
            if (current == null || current.getAmplifier() <= buff.amplifier()) player.addPotionEffect(new PotionEffect(buff.type(), duration, buff.amplifier(), true, buff.particles(), buff.icon()));
            tracked.put(buff.type(), buff.amplifier());
        }
    }
    private void clearActive(Player player) {
        Map<PotionEffectType, Integer> tokens = activeBuffs.remove(player.getUniqueId());
        if (tokens != null) clearTokens(player, tokens);
        attributes.remove(player);
    }
    private void clearTokens(Player player, Map<PotionEffectType, Integer> tokens) {
        for (var entry : tokens.entrySet()) { PotionEffect current = player.getPotionEffect(entry.getKey()); if (current != null && current.getAmplifier() == entry.getValue()) player.removePotionEffect(entry.getKey()); }
    }
    private void claim(Player player, String encoded) { repository.setAppliedBuffs(player.getUniqueId(), encoded, serverId, false).exceptionally(error -> { log("保存 Buff 状态失败", error); return null; }); }
    private static String encode(Map<PotionEffectType, Integer> buffs) { if (buffs == null || buffs.isEmpty()) return ""; return buffs.entrySet().stream().map(e -> e.getKey().getKey().getKey() + ":" + e.getValue()).reduce((a,b) -> a + "," + b).orElse(""); }
    private static Map<PotionEffectType, Integer> decode(String encoded) {
        Map<PotionEffectType, Integer> result = new HashMap<>(); if (encoded == null || encoded.isBlank()) return result;
        for (String token : encoded.split(",")) { String[] pair = token.split(":", 2); PotionEffectType type = PotionEffectType.getByName(pair[0].toUpperCase(Locale.ROOT)); try { if (type != null && pair.length == 2) result.put(type, Integer.parseInt(pair[1])); } catch (NumberFormatException ignored) {} }
        return result;
    }
    private void log(String context, Throwable error) { plugin.getLogger().severe(context + ": " + root(error).getMessage()); }
    private static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }
}
