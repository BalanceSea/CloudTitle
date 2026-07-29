package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemExchangeService {
    private final CloudTitle plugin;
    private final Map<UUID, List<ItemStack>> pendingRefunds = new ConcurrentHashMap<>();
    private Method craftEngineItemId;

    public ItemExchangeService(CloudTitle plugin) {
        this.plugin = plugin;
        setupCraftEngine();
    }

    private void setupCraftEngine() {
        Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null || !craftEngine.isEnabled()) return;
        try {
            Class<?> api = Class.forName(
                    "net.momirealms.craftengine.bukkit.api.CraftEngineItems",
                    true,
                    craftEngine.getClass().getClassLoader()
            );
            craftEngineItemId = api.getMethod("getCustomItemId", ItemStack.class);
            plugin.getLogger().info("已接入 CraftEngine 物品识别 API");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("CraftEngine 物品 API 接入失败: " + exception.getMessage());
        }
    }

    public Removal removeAvailable(
            Player player,
            List<TitleDefinition.ItemRequirement> requirements,
            Map<String, Integer> progress) {
        if (requirements.stream().anyMatch(requirement ->
                requirement.source() == TitleDefinition.ItemSource.CRAFTENGINE) && craftEngineItemId == null) {
            return Removal.unavailable();
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        Map<String, List<ItemStack>> removed = new HashMap<>();
        Map<String, Integer> offered = new HashMap<>();

        for (TitleDefinition.ItemRequirement requirement : requirements) {
            int remaining = Math.max(0, requirement.amount() - progress.getOrDefault(requirement.key(), 0));
            if (remaining == 0) continue;
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                ItemStack stack = contents[slot];
                if (!matches(stack, requirement)) continue;
                int taken = Math.min(remaining, stack.getAmount());
                ItemStack removedStack = stack.clone();
                removedStack.setAmount(taken);
                removed.computeIfAbsent(requirement.key(), ignored -> new ArrayList<>()).add(removedStack);
                offered.merge(requirement.key(), taken, Integer::sum);
                remaining -= taken;
                if (taken == stack.getAmount()) {
                    contents[slot] = null;
                } else {
                    stack.setAmount(stack.getAmount() - taken);
                }
            }
        }
        inventory.setStorageContents(contents);
        return new Removal(removed, offered, false);
    }

    public void refund(Player player, Removal removal, Map<String, Integer> accepted) {
        List<ItemStack> refunds = new ArrayList<>();
        for (Map.Entry<String, List<ItemStack>> entry : removal.removed().entrySet()) {
            int refundAmount = removal.offered().getOrDefault(entry.getKey(), 0)
                    - accepted.getOrDefault(entry.getKey(), 0);
            for (ItemStack removedStack : entry.getValue()) {
                if (refundAmount <= 0) break;
                int amount = Math.min(refundAmount, removedStack.getAmount());
                ItemStack refund = removedStack.clone();
                refund.setAmount(amount);
                refunds.add(refund);
                refundAmount -= amount;
            }
        }
        if (refunds.isEmpty()) return;
        if (!player.isOnline()) {
            pendingRefunds.compute(player.getUniqueId(), (uuid, queued) -> {
                List<ItemStack> result = queued == null ? new ArrayList<>() : new ArrayList<>(queued);
                result.addAll(refunds);
                return result;
            });
            return;
        }
        give(player, refunds);
    }

    public void restorePending(Player player) {
        List<ItemStack> refunds = pendingRefunds.remove(player.getUniqueId());
        if (refunds != null && !refunds.isEmpty()) give(player, refunds);
    }

    private void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private boolean matches(ItemStack stack, TitleDefinition.ItemRequirement requirement) {
        if (stack == null || stack.getType().isAir()) return false;
        String craftEngineId = craftEngineId(stack);
        if (requirement.source() == TitleDefinition.ItemSource.CRAFTENGINE) {
            return requirement.id().equalsIgnoreCase(craftEngineId);
        }
        Material material = Material.matchMaterial(requirement.id());
        return craftEngineId == null && material != null && stack.getType() == material;
    }

    private String craftEngineId(ItemStack stack) {
        if (craftEngineItemId == null) return null;
        try {
            Object key = craftEngineItemId.invoke(null, stack);
            return key == null ? null : key.toString();
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().warning("读取 CraftEngine 物品 ID 失败: " + exception.getMessage());
            return null;
        }
    }

    public record Removal(
            Map<String, List<ItemStack>> removed,
            Map<String, Integer> offered,
            boolean craftEngineUnavailable) {
        public static Removal unavailable() {
            return new Removal(Map.of(), Map.of(), true);
        }

        public boolean empty() {
            return offered.isEmpty();
        }
    }
}
