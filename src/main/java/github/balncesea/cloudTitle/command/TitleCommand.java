package github.balncesea.cloudTitle.command;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import github.balncesea.cloudTitle.command.modules.AdminTitleCommand;
import github.balncesea.cloudTitle.command.modules.HelpCommand;
import github.balncesea.cloudTitle.command.modules.MenuCommand;
import github.balncesea.cloudTitle.command.modules.ReloadCommand;
import github.balncesea.cloudTitle.command.modules.TitleActionCommand;
import github.balncesea.cloudTitle.gui.MenuManager;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * `/cloudtitle` 根路由。
 *
 * <p>根类只负责查找模块、转发参数和补全，不直接访问数据库或实现具体业务。
 * 旧的 shop/custom/grant/revoke 标签仍作为模块别名保留。</p>
 */
public final class TitleCommand implements CommandExecutor, TabCompleter {
    private final CommandContext context;
    private final Map<String, CommandModule> routes = new LinkedHashMap<>();

    public TitleCommand(CloudTitle plugin, TitleService titles, MenuManager menus, MessageService messages) {
        this.context = new CommandContext(plugin, titles, menus, messages);
        register(new HelpCommand(context));
        register(new ReloadCommand(context));
        register(new MenuCommand(context));
        register(new TitleActionCommand(context));
        register(new AdminTitleCommand(context));
    }

    private void register(CommandModule module) {
        routes.put(module.name(), module);
        module.aliases().forEach(alias -> routes.put(alias.toLowerCase(Locale.ROOT), module));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // 兼容插件原有 UX：无参数直接打开仓库；/cloudtitle help 显示帮助。
            return routes.get("menu").execute(sender, "menu", new String[0]);
        }
        String invokedName = args[0].toLowerCase(Locale.ROOT);
        CommandModule module = routes.get(invokedName);
        if (module == null) {
            routes.get("help").execute(sender, "help", new String[0]);
            return true;
        }
        String[] moduleArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
        return module.execute(sender, invokedName, moduleArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0 || args.length == 1) {
            List<String> values = new ArrayList<>(List.of(
                    "help", "menu", "shop", "custom", "set", "clear"));
            if (sender.hasPermission("cloudtitle.admin")) {
                values.addAll(List.of("add", "remove", "grant", "revoke", "reload"));
            }
            return match(args.length == 0 ? "" : args[0], values);
        }
        CommandModule module = routes.get(args[0].toLowerCase(Locale.ROOT));
        if (module == null) return List.of();
        return module.complete(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    private static List<String> match(String prefix, List<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct()
                .sorted()
                .toList();
    }
}
