package github.balncesea.cloudTitle;

import github.balncesea.cloudTitle.command.TitleCommand;
import github.balncesea.cloudTitle.config.ConfigManager;
import github.balncesea.cloudTitle.gui.MenuManager;
import github.balncesea.cloudTitle.hook.CloudTitleExpansion;
import github.balncesea.cloudTitle.hook.AttributeManager;
import github.balncesea.cloudTitle.listener.PlayerListener;
import github.balncesea.cloudTitle.service.*;
import github.balncesea.cloudTitle.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public final class CloudTitle extends JavaPlugin {
    private ConfigManager configs; private Database database; private MessageService messages; private EconomyService economy; private AttributeManager attributes; private TitleService titles; private MenuManager menus;
    @Override public void onEnable() {
        configs = new ConfigManager(this); configs.load(); messages = new MessageService(configs);
        try { database = new Database(this, configs.storage()); } catch (SQLException | RuntimeException e) { getLogger().severe("数据库初始化失败，插件已停用: " + e.getMessage()); Bukkit.getPluginManager().disablePlugin(this); return; }
        economy = new EconomyService(this); ItemExchangeService itemExchange = new ItemExchangeService(this); PlaceholderConditionService placeholderConditions = new PlaceholderConditionService(this); attributes = new AttributeManager(this); attributes.reload(); titles = new TitleService(this,database,messages,economy,itemExchange,placeholderConditions,attributes); menus = new MenuManager(this,titles,messages,economy,placeholderConditions);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this,titles),this); Bukkit.getPluginManager().registerEvents(menus,this);
        TitleCommand executor = new TitleCommand(this,titles,menus,messages); PluginCommand command=getCommand("cloudtitle"); if(command!=null){command.setExecutor(executor);command.setTabCompleter(executor);}
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) { new CloudTitleExpansion(this).register(); getLogger().info("已接入 PlaceholderAPI"); }
        long refresh=Math.max(20,getConfig().getLong("buffs.refresh-ticks",100)); Bukkit.getScheduler().runTaskTimer(this,titles::refreshBuffs,refresh,refresh);
        Bukkit.getOnlinePlayers().forEach(titles::load); getLogger().info("云称号已启用，存储类型: " + configs.storage().getString("type","sqlite"));
    }
    @Override public void onDisable() { if(menus!=null) menus.closeAll(); if(titles!=null) Bukkit.getOnlinePlayers().forEach(titles::unload); if(attributes!=null) attributes.close(); if(database!=null) database.close(); }
    public void reloadPlugin(){ menus.closeAll(); configs.reload(); economy.setup(); attributes.reload(); titles.reapplyAll(); }
    public ConfigManager configs(){return configs;} public MessageService messages(){return messages;} public TitleService titles(){return titles;}
}
