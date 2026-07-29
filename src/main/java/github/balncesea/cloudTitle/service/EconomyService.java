package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.TitleDefinition.CostType;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.lang.reflect.Method;
import java.util.UUID;

public final class EconomyService {
    public enum Result { SUCCESS, UNAVAILABLE, INSUFFICIENT, FAILED }
    private final CloudTitle plugin;
    private Economy economy;
    private Object pointsApi;
    private Method pointsLook, pointsTake, pointsGive;

    public EconomyService(CloudTitle plugin) { this.plugin = plugin; setup(); }
    public void setup() {
        economy = null; pointsApi = null;
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) economy = registration.getProvider();
        }
        try {
            var pointsPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pointsPlugin != null && pointsPlugin.isEnabled()) {
                pointsApi = pointsPlugin.getClass().getMethod("getAPI").invoke(pointsPlugin);
                Class<?> api = pointsApi.getClass();
                pointsLook = api.getMethod("look", UUID.class);
                pointsTake = api.getMethod("take", UUID.class, int.class);
                pointsGive = api.getMethod("give", UUID.class, int.class);
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("PlayerPoints API 接入失败: " + e.getMessage()); pointsApi = null;
        }
    }

    public Result charge(Player player, CostType type, double amount) {
        if (amount <= 0 || type == CostType.FREE || type == CostType.PERMISSION) return Result.SUCCESS;
        if (type == CostType.ITEM) return Result.FAILED;
        if (type == CostType.PAPI) return Result.SUCCESS;
        if (type == CostType.MONEY) {
            if (economy == null) return Result.UNAVAILABLE;
            if (!economy.has(player, amount)) return Result.INSUFFICIENT;
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response.transactionSuccess() ? Result.SUCCESS : Result.FAILED;
        }
        if (pointsApi == null) return Result.UNAVAILABLE;
        int cost = (int) Math.ceil(amount);
        try {
            int balance = ((Number) pointsLook.invoke(pointsApi, player.getUniqueId())).intValue();
            if (balance < cost) return Result.INSUFFICIENT;
            Object result = pointsTake.invoke(pointsApi, player.getUniqueId(), cost);
            return result instanceof Boolean success && success ? Result.SUCCESS : Result.FAILED;
        } catch (ReflectiveOperationException e) { return Result.FAILED; }
    }

    public void refund(Player player, CostType type, double amount) {
        if (amount <= 0) return;
        if (type == CostType.MONEY && economy != null) economy.depositPlayer(player, amount);
        if (type == CostType.POINTS && pointsApi != null) try { pointsGive.invoke(pointsApi, player.getUniqueId(), (int) Math.ceil(amount)); } catch (ReflectiveOperationException ignored) {}
    }

    public boolean moneyAvailable() { return economy != null; }
    public boolean pointsAvailable() { return pointsApi != null; }
    public String format(CostType type, double amount, String permission) {
        return switch (type) {
            case MONEY -> (economy == null ? String.format("%.2f 金币", amount) : economy.format(amount));
            case POINTS -> ((int) Math.ceil(amount)) + " 点券";
            case PERMISSION -> "权限 " + permission;
            case ITEM -> "物品提交";
            case PAPI -> "变量条件";
            case FREE -> "免费";
        };
    }
}
