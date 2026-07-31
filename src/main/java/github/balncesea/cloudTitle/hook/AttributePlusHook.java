package github.balncesea.cloudTitle.hook;

import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

final class AttributePlusHook implements AttributeHook {
    private final Method getData;
    private final Method addSource;
    private final Method removeSource;
    private final Method update;

    AttributePlusHook(Plugin dependency) throws ReflectiveOperationException {
        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> api = Class.forName("org.serverct.ersha.api.AttributeAPI", true, loader);
        getData = api.getMethod("getAttrData", LivingEntity.class);
        Class<?> dataType = getData.getReturnType();
        addSource = findAddMethod(api, dataType);
        removeSource = api.getMethod("takeSourceAttribute", dataType, String.class);
        update = api.getMethod("updateAttribute", LivingEntity.class);
    }

    private static Method findAddMethod(Class<?> api, Class<?> dataType) throws NoSuchMethodException {
        try {
            return api.getMethod("addStaticAttributeSource", dataType, String.class, List.class);
        } catch (NoSuchMethodException ignored) {
            return api.getMethod("addSourceAttribute", dataType, String.class, List.class);
        }
    }

    @Override
    public String name() {
        return "AttributePlus";
    }

    @Override
    public List<String> lines(TitleDefinition.Attributes attributes) {
        return attributes.attributePlus();
    }

    @Override
    public void apply(Player player, String source, List<String> lines) throws ReflectiveOperationException {
        Object data = getData.invoke(null, player);
        if (data == null) throw new IllegalStateException("AttributePlus 尚未初始化该玩家的属性数据");
        addSource.invoke(null, data, source, lines);
        update.invoke(null, player);
    }

    @Override
    public void remove(Player player, String source) throws ReflectiveOperationException {
        Object data = getData.invoke(null, player);
        if (data == null) return;
        removeSource.invoke(null, data, source);
        update.invoke(null, player);
    }
}
