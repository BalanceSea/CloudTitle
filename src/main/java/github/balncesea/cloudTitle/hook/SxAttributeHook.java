package github.balncesea.cloudTitle.hook;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

final class SxAttributeHook implements AttributeHook {
    private final Object api;
    private final Method addNamedSource;
    private final Method removeNamedSource;
    private final Method loadListData;
    private final Method setLegacyData;
    private final Method removeLegacyData;
    private final Method update;

    SxAttributeHook(Plugin dependency) throws ReflectiveOperationException {
        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> main = Class.forName("github.saukiya.sxattribute.SXAttribute", true, loader);
        api = main.getMethod("getApi").invoke(null);
        Class<?> apiType = api.getClass();
        Method namedAdd = find(apiType, "addSourceAttribute",
                LivingEntity.class, String.class, List.class, boolean.class);
        Method namedRemove = find(apiType, "takeSourceAttribute",
                LivingEntity.class, String.class, boolean.class);
        addNamedSource = namedAdd;
        removeNamedSource = namedRemove;
        update = apiType.getMethod("attributeUpdate", LivingEntity.class);

        if (namedAdd == null || namedRemove == null) {
            loadListData = apiType.getMethod("loadListData", List.class);
            Class<?> dataType = loadListData.getReturnType();
            setLegacyData = apiType.getMethod("setEntityAPIData", Class.class, UUID.class, dataType);
            removeLegacyData = apiType.getMethod("removeEntityAPIData", Class.class, UUID.class);
        } else {
            loadListData = null;
            setLegacyData = null;
            removeLegacyData = null;
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @Override
    public String name() {
        return "SX-Attribute";
    }

    @Override
    public List<String> lines(TitleDefinition.Attributes attributes) {
        return attributes.sxAttribute();
    }

    @Override
    public void apply(Player player, String source, List<String> lines) throws ReflectiveOperationException {
        if (addNamedSource != null) {
            Object result = addNamedSource.invoke(api, player, source, lines, true);
            if (result instanceof Boolean success && !success) {
                throw new IllegalStateException("SX-Attribute 拒绝添加称号属性来源");
            }
            return;
        }
        Object data = loadListData.invoke(api, lines);
        setLegacyData.invoke(api, CloudTitle.class, player.getUniqueId(), data);
        update.invoke(api, player);
    }

    @Override
    public void remove(Player player, String source) throws ReflectiveOperationException {
        if (removeNamedSource != null) {
            removeNamedSource.invoke(api, player, source, true);
            return;
        }
        removeLegacyData.invoke(api, CloudTitle.class, player.getUniqueId());
        update.invoke(api, player);
    }
}
