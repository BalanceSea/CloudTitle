package github.balncesea.cloudTitle.command.modules;

import github.balncesea.cloudTitle.command.common.CommandContext;
import github.balncesea.cloudTitle.command.common.CommandModule;
import org.bukkit.command.CommandSender;

/** 显示可编辑的语言文件帮助。 */
public final class HelpCommand implements CommandModule {
    private final CommandContext context;

    public HelpCommand(CommandContext context) { this.context = context; }

    @Override
    public String name() { return "help"; }

    @Override
    public boolean execute(CommandSender sender, String invokedName, String[] args) {
        context.messages().help(sender);
        return true;
    }
}
