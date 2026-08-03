package github.balncesea.cloudTitle.command.common;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** 一个可独立测试、补全和替换的子命令模块。 */
public interface CommandModule {
    String name();

    default Collection<String> aliases() { return List.of(); }

    boolean execute(CommandSender sender, String invokedName, String[] args);

    default boolean execute(CommandSender sender, String[] args) {
        return execute(sender, name(), args);
    }

    default List<String> complete(CommandSender sender, String[] args) { return List.of(); }

    default boolean matches(String value) {
        return name().equalsIgnoreCase(value)
                || aliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(value));
    }
}
