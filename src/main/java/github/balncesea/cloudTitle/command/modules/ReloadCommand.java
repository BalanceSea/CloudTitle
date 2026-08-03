package github.balncesea.cloudTitle.command.modules;

import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import org.bukkit.command.CommandSender;

/** 只处理配置热重载，资源和数据库生命周期由主类编排。 */
public final class ReloadCommand implements CommandModule {
    private final CommandContext context;

    public ReloadCommand(CommandContext context) { this.context = context; }

    @Override
    public String name() { return "reload"; }

    @Override
    public boolean execute(CommandSender sender, String invokedName, String[] args) {
        if (!context.admin(sender)) return true;
        context.plugin().reloadPlugin();
        context.messages().send(sender, "reload-success");
        return true;
    }
}
