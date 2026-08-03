package github.balncesea.cloudTitle;

import github.balncesea.cloudTitle.bootstrap.PluginLifecycle;
import github.balncesea.cloudTitle.config.ConfigManager;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.plugin.java.JavaPlugin;

/** 云称号插件入口，只负责委托生命周期和暴露模块访问器。 */
public final class CloudTitle extends JavaPlugin {
    private PluginLifecycle lifecycle;

    @Override
    public void onEnable() {
        lifecycle = new PluginLifecycle(this);
        lifecycle.enable();
    }

    @Override
    public void onDisable() {
        if (lifecycle != null) lifecycle.disable();
    }

    public void reloadPlugin() {
        if (lifecycle != null) lifecycle.reload();
    }

    public ConfigManager configs() { return lifecycle == null ? null : lifecycle.configs(); }
    public MessageService messages() { return lifecycle == null ? null : lifecycle.messages(); }
    public TitleService titles() { return lifecycle == null ? null : lifecycle.titles(); }
}
