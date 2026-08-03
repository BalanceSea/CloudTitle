package github.balncesea.cloudTitle.listener;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 处理玩家生命周期，保证称号数据和效果与在线状态同步。 */
public final class PlayerListener implements Listener {
    private final CloudTitle plugin;
    private final TitleService titles;

    public PlayerListener(CloudTitle plugin, TitleService titles) {
        this.plugin = plugin;
        this.titles = titles;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        titles.load(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        titles.unload(event.getPlayer());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        // RespawnEvent 触发时玩家实体仍在重建，延迟到下一 tick 再补回效果。
        Bukkit.getScheduler().runTask(plugin, () -> titles.reapply(event.getPlayer()));
    }
}
