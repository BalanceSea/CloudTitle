package github.balncesea.cloudTitle.command.modules;

import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import github.balncesea.cloudTitle.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 管理员发放和回收称号，兼容 grant/revoke 与 add/remove 两套名称。 */
public final class AdminTitleCommand implements CommandModule {
    private final CommandContext context;

    public AdminTitleCommand(CommandContext context) { this.context = context; }

    @Override
    public String name() { return "add"; }

    @Override
    public List<String> aliases() { return List.of("remove", "grant", "revoke"); }

    @Override
    public boolean execute(CommandSender sender, String invokedName, String[] args) {
        if (!context.admin(sender)) return true;
        if (args.length < 2) {
            context.messages().help(sender);
            return true;
        }
        String operation = invokedName.toLowerCase(Locale.ROOT);
        OfflinePlayer target = offlinePlayer(args[0]);
        String id = args[1];
        boolean grant = operation.equals("add") || operation.equals("grant");
        var callback = (java.util.function.Consumer<Boolean>) ok -> context.messages().send(
                sender,
                ok ? "admin-success" : "admin-failed",
                Map.of(
                        "player", MessageService.escape(target.getName() == null ? args[0] : target.getName()),
                        "id", MessageService.escape(id)));
        if (grant) context.titles().grant(target.getUniqueId(), id, callback);
        else context.titles().revoke(target.getUniqueId(), id, callback);
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(player -> player.getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (args.length == 2) {
            return context.plugin().configs().definitions().keySet().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("deprecation")
    private static OfflinePlayer offlinePlayer(String name) {
        // Spigot 1.20 仍需使用名称解析，以兼容从未在线过的玩家。
        return Bukkit.getOfflinePlayer(name);
    }

}
