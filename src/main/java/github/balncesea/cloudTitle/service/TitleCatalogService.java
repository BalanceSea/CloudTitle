package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.PlayerTitleData;
import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 称号目录查询服务。
 *
 * <p>配置称号、自定义称号、默认显示称号以及商城筛选都集中在这里，
 * 获取流程和效果服务只处理用例，不再直接拼装目录规则。</p>
 */
public final class TitleCatalogService {
    private static final TitleDefinition FALLBACK_DEFAULT = new TitleDefinition(
            "resident",
            "<gradient:#CBD5E1:#94A3B8><bold>云世界居民</bold></gradient>",
            List.of("<white>生活在云世界中的普通居民。</white>"),
            Material.COMPASS,
            List.of(),
            TitleDefinition.Attributes.empty(),
            TitleDefinition.Shop.hidden(),
            false
    );

    private final CloudTitle plugin;

    public TitleCatalogService(CloudTitle plugin) {
        this.plugin = plugin;
    }

    /** 先查玩家自定义称号，再查 titles.yml 中的静态定义。 */
    public TitleDefinition find(PlayerTitleData data, String id) {
        if (id == null || id.isBlank()) return null;
        TitleDefinition custom = data == null ? null : data.customTitles().get(id);
        return custom != null ? custom : plugin.configs().definitions().get(id);
    }

    /** 返回实际展示的称号；默认称号只用于显示，不授予、不佩戴，也不应用效果。 */
    public TitleDefinition displayed(PlayerTitleData data) {
        TitleDefinition selected = find(data, data == null ? null : data.selected());
        if (selected != null) return selected;
        if (!plugin.getConfig().getBoolean("default-title.enabled", true)) return null;

        String defaultId = plugin.getConfig().getString("default-title.id", "resident");
        TitleDefinition configured = plugin.configs().definitions().get(defaultId);
        if (configured != null) return configured;
        return "resident".equals(defaultId) ? FALLBACK_DEFAULT : null;
    }

    public Collection<TitleDefinition> owned(UUID uuid, Map<UUID, PlayerTitleData> cache) {
        PlayerTitleData data = cache.get(uuid);
        if (data == null) return List.of();

        List<TitleDefinition> result = new ArrayList<>();
        for (String id : data.owned()) {
            TitleDefinition title = find(data, id);
            if (title != null) result.add(title);
        }
        result.sort(Comparator.comparing(TitleDefinition::id));
        return List.copyOf(result);
    }

    public Collection<TitleDefinition> shop(UUID uuid, Map<UUID, PlayerTitleData> cache) {
        PlayerTitleData data = cache.get(uuid);
        if (data == null) return List.of();
        return plugin.configs().definitions().values().stream()
                .filter(title -> title.shop().enabled() && title.shop().displayed())
                .filter(title -> !data.owned().contains(title.id()))
                .toList();
    }
}
