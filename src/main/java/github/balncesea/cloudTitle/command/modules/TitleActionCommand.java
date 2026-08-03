package github.balncesea.cloudTitle.command.modules;

import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** 处理玩家佩戴和卸下称号。 */
public final class TitleActionCommand implements CommandModule {
    private final CommandContext context;

    public TitleActionCommand(CommandContext context) { this.context = context; }

    @Override
    public String name() { return "set"; }

    @Override
    public List<String> aliases() { return List.of("clear"); }

    @Override
    public boolean execute(CommandSender sender, String invokedName, String[] args) {
        Player player = context.player(sender);
        if (player == null) return true;
        if (invokedName.equalsIgnoreCase("clear")) {
            context.titles().clear(player);
            return true;
        }
        if (args.length < 1) {
            context.messages().help(sender);
            return true;
        }
        context.titles().select(player, args[0]);
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        var data = context.titles().data(player.getUniqueId());
        if (data == null) return List.of();
        return data.owned().stream()
                .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .toList();
    }

}
