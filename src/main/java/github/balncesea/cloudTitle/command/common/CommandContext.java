package github.balncesea.cloudTitle.command.common;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.gui.MenuManager;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 命令模块共享上下文，统一权限检查和玩家类型检查。 */
public final class CommandContext {
    private final CloudTitle plugin;
    private final TitleService titles;
    private final MenuManager menus;
    private final MessageService messages;

    public CommandContext(CloudTitle plugin, TitleService titles, MenuManager menus, MessageService messages) {
        this.plugin = plugin;
        this.titles = titles;
        this.menus = menus;
        this.messages = messages;
    }

    public CloudTitle plugin() { return plugin; }
    public TitleService titles() { return titles; }
    public MenuManager menus() { return menus; }
    public MessageService messages() { return messages; }

    public Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return null;
        }
        if (!player.hasPermission("cloudtitle.use")) {
            messages.send(sender, "no-permission");
            return null;
        }
        return player;
    }

    public boolean admin(CommandSender sender) {
        if (!sender.hasPermission("cloudtitle.admin")) {
            messages.send(sender, "no-permission");
            return false;
        }
        return true;
    }
}
