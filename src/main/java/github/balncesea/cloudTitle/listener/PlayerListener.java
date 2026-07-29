package github.balncesea.cloudTitle.listener;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerListener implements Listener {
    private final CloudTitle plugin; private final TitleService titles;
    public PlayerListener(CloudTitle plugin, TitleService titles) { this.plugin = plugin; this.titles = titles; }
    @EventHandler public void join(PlayerJoinEvent event) { titles.load(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { titles.unload(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, titles::refreshBuffs); }
}