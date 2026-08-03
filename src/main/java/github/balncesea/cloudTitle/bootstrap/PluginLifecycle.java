package github.balncesea.cloudTitle.bootstrap;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.command.TitleCommand;
import github.balncesea.cloudTitle.config.ConfigManager;
import github.balncesea.cloudTitle.gui.MenuManager;
import github.balncesea.cloudTitle.hook.AttributeManager;
import github.balncesea.cloudTitle.hook.CloudTitleExpansion;
import github.balncesea.cloudTitle.listener.PlayerListener;
import github.balncesea.cloudTitle.service.EconomyService;
import github.balncesea.cloudTitle.service.ItemExchangeService;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.PlaceholderConditionService;
import github.balncesea.cloudTitle.service.TitleCatalogService;
import github.balncesea.cloudTitle.service.TitleService;
import github.balncesea.cloudTitle.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

import java.sql.SQLException;

/**
 * 插件生命周期编排器。
 *
 * <p>CloudTitle 主类只负责把生命周期交给这里；模块初始化顺序和关闭顺序
 * 集中维护，避免命令、GUI 或服务在半初始化状态下被调用。</p>
 */
public final class PluginLifecycle {
    private final CloudTitle plugin;
    private ConfigManager configs;
    private Database database;
    private MessageService messages;
    private EconomyService economy;
    private AttributeManager attributes;
    private TitleService titles;
    private MenuManager menus;

    public PluginLifecycle(CloudTitle plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        configs = new ConfigManager(plugin);
        configs.load();
        messages = new MessageService(configs);

        if (!initializeDatabase()) return;
        initializeServices();
        registerListenersAndCommands();
        registerPlaceholders();
        scheduleBuffRefresh();

        Bukkit.getOnlinePlayers().forEach(titles::load);
        plugin.getLogger().info("云称号已启用，存储类型: " + configs.storage().getString("type", "sqlite"));
    }

    private boolean initializeDatabase() {
        try {
            database = new Database(plugin, configs.storage());
            return true;
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("数据库初始化失败，插件已停用: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(plugin);
            return false;
        }
    }

    private void initializeServices() {
        economy = new EconomyService(plugin);
        TitleCatalogService catalog = new TitleCatalogService(plugin);
        ItemExchangeService itemExchange = new ItemExchangeService(plugin);
        PlaceholderConditionService placeholderConditions = new PlaceholderConditionService(plugin);
        attributes = new AttributeManager(plugin);
        attributes.reload();
        titles = new TitleService(plugin, database, catalog, messages, economy, itemExchange,
                placeholderConditions, attributes);
        menus = new MenuManager(plugin, titles, messages, economy, placeholderConditions);
    }

    private void registerListenersAndCommands() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(plugin, titles), plugin);
        Bukkit.getPluginManager().registerEvents(menus, plugin);

        TitleCommand executor = new TitleCommand(plugin, titles, menus, messages);
        PluginCommand command = plugin.getCommand("cloudtitle");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CloudTitleExpansion(plugin).register();
            plugin.getLogger().info("已接入 PlaceholderAPI");
        }
    }

    private void scheduleBuffRefresh() {
        long refresh = Math.max(20, plugin.getConfig().getLong("buffs.refresh-ticks", 100));
        Bukkit.getScheduler().runTaskTimer(plugin, titles::refreshBuffs, refresh, refresh);
    }

    public void reload() {
        if (menus != null) menus.closeAll();
        configs.reload();
        if (economy != null) economy.setup();
        if (attributes != null) attributes.reload();
        if (titles != null) titles.reapplyAll();
    }

    public void disable() {
        if (menus != null) menus.closeAll();
        if (titles != null) Bukkit.getOnlinePlayers().forEach(titles::unload);
        if (attributes != null) attributes.close();
        if (database != null) database.close();
    }

    public ConfigManager configs() { return configs; }
    public MessageService messages() { return messages; }
    public TitleService titles() { return titles; }
}
