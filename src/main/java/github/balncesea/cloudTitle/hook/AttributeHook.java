package github.balncesea.cloudTitle.hook;

import github.balncesea.cloudTitle.model.TitleDefinition;
import org.bukkit.entity.Player;

import java.util.List;

interface AttributeHook {
    String name();

    List<String> lines(TitleDefinition.Attributes attributes);

    void apply(Player player, String source, List<String> lines) throws ReflectiveOperationException;

    void remove(Player player, String source) throws ReflectiveOperationException;
}
