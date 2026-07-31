package github.balncesea.cloudTitle.hook;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AttributeManager {
    private static final String SOURCE = "CloudTitle:equipped-title";

    private final CloudTitle plugin;
    private final Map<UUID, Set<String>> activeHooks = new ConcurrentHashMap<>();
    private List<AttributeHook> hooks = List.of();

    public AttributeManager(CloudTitle plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        clearAll();
        List<AttributeHook> detected = new ArrayList<>();
        detect(detected, "AttributePlus", "integrations.attribute-plus.enabled", AttributePlusHook::new);
        detect(detected, "SX-Attribute", "integrations.sx-attribute.enabled", SxAttributeHook::new);
        hooks = List.copyOf(detected);
    }

    public void apply(Player player, TitleDefinition.Attributes attributes) {
        remove(player);
        if (attributes == null || attributes.isEmpty()) return;
        for (AttributeHook hook : hooks) {
            List<String> lines = hook.lines(attributes);
            if (lines.isEmpty()) continue;
            try {
                hook.apply(player, SOURCE, lines);
                activeHooks.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(hook.name());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("为 " + player.getName() + " 应用 " + hook.name()
                        + " 称号属性失败: " + message(exception));
                tryRemove(hook, player);
            }
        }
    }

    public void remove(Player player) {
        Set<String> active = activeHooks.remove(player.getUniqueId());
        if (active == null || active.isEmpty()) return;
        for (AttributeHook hook : hooks) {
            if (active.contains(hook.name())) tryRemove(hook, player);
        }
    }

    public void close() {
        clearAll();
        hooks = List.of();
    }

    private void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) remove(player);
        activeHooks.clear();
    }

    private void tryRemove(AttributeHook hook, Player player) {
        try {
            hook.remove(player, SOURCE);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("清理 " + player.getName() + " 的 " + hook.name()
                    + " 称号属性失败: " + message(exception));
        }
    }

    private void detect(List<AttributeHook> detected, String pluginName, String enabledPath,
                        HookFactory factory) {
        if (!plugin.getConfig().getBoolean(enabledPath, true)) return;
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        if (dependency == null || !dependency.isEnabled()) return;
        try {
            AttributeHook hook = factory.create(dependency);
            detected.add(hook);
            plugin.getLogger().info("已接入 " + hook.name() + " 称号属性");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning(pluginName + " 已安装，但 API 版本不受支持: " + message(exception));
        }
    }

    private static String message(Throwable throwable) {
        while (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    @FunctionalInterface
    private interface HookFactory {
        AttributeHook create(Plugin plugin) throws ReflectiveOperationException;
    }
}
