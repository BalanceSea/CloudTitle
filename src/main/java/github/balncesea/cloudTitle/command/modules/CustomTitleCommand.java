package github.balncesea.cloudTitle.command.modules;

import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 删除自定义称号，不允许通过此命令删除 titles.yml 中的静态称号。 */
public final class CustomTitleCommand implements CommandModule {
    private final CommandContext context;

    public CustomTitleCommand(CommandContext context) { this.context = context; }

    @Override
    public String name() { return "delete"; }

    @Override
    public List<String> aliases() { return List.of(); }

    @Override
    public boolean execute(CommandSender sender, String invokedName, String[] args) {
        if (args.length == 1) {
            Player player = context.player(sender);
            if (player == null) return true;
            if (!context.titles().ready(player.getUniqueId())) {
                context.messages().send(player, "loading");
                return true;
            }
            context.titles().deleteCustom(player, args[0], result ->
                    context.messages().send(player, selfMessage(result), Map.of(
                            "id", MessageService.escape(args[0]))));
            return true;
        }

        if (args.length == 2) {
            if (!context.admin(sender)) return true;
            OfflinePlayer target = offlinePlayer(args[0]);
            String id = args[1];
            context.titles().deleteCustom(target.getUniqueId(), id, result ->
                    context.messages().send(sender, adminMessage(result), Map.of(
                            "player", MessageService.escape(target.getName() == null ? args[0] : target.getName()),
                            "id", MessageService.escape(id))));
            return true;
        }

        context.messages().help(sender);
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            List<String> values = new ArrayList<>();
            var data = context.titles().data(player.getUniqueId());
            if (data != null) values.addAll(data.customTitles().keySet());
            if (sender.hasPermission("cloudtitle.admin")) {
                values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
            return match(args[0], values);
        }
        if (args.length == 2 && sender.hasPermission("cloudtitle.admin")) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return List.of();
            var data = context.titles().data(target.getUniqueId());
            return data == null ? List.of() : match(args[1], data.customTitles().keySet());
        }
        return List.of();
    }

    private String selfMessage(TitleService.CustomDeleteResult result) {
        return switch (result) {
            case DELETED -> "custom-delete-success";
            case NOT_FOUND -> "custom-delete-not-found";
            case FAILED -> "custom-delete-failed";
        };
    }

    private String adminMessage(TitleService.CustomDeleteResult result) {
        return switch (result) {
            case DELETED -> "admin-custom-delete-success";
            case NOT_FOUND -> "admin-custom-delete-not-found";
            case FAILED -> "admin-custom-delete-failed";
        };
    }

    private static List<String> match(String prefix, Iterable<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower) && !result.contains(value)) result.add(value);
        }
        return result.stream().sorted().toList();
    }

    @SuppressWarnings("deprecation")
    private static OfflinePlayer offlinePlayer(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}
