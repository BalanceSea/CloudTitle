package github.balncesea.cloudTitle.command.modules;

import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** 打开仓库、商城和自定义称号 GUI。 */
public final class MenuCommand implements CommandModule {
    private final CommandContext context;

    public MenuCommand(CommandContext context) { this.context = context; }

    @Override
    public String name() { return "menu"; }

    @Override
    public List<String> aliases() { return List.of("shop", "custom"); }

    @Override
    public boolean execute(CommandSender sender, String invokedName, String[] args) {
        Player player = context.player(sender);
        if (player == null) return true;
        String target = args.length == 0 ? invokedName.toLowerCase(Locale.ROOT) : args[0].toLowerCase(Locale.ROOT);
        if (target.equals("menu")) target = "warehouse";
        switch (target) {
            case "warehouse", "仓库" -> context.menus().openWarehouse(player, 0);
            case "shop", "商城" -> context.menus().openShop(player, 0);
            case "custom", "工坊" -> context.menus().openCustom(player);
            default -> context.messages().help(sender);
        }
        return true;
    }
}
