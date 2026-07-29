package github.balncesea.cloudTitle.command;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.gui.MenuManager;
import github.balncesea.cloudTitle.service.MessageService;
import github.balncesea.cloudTitle.service.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class TitleCommand implements CommandExecutor, TabCompleter {
    private final CloudTitle plugin; private final TitleService titles; private final MenuManager menus; private final MessageService messages;
    public TitleCommand(CloudTitle plugin, TitleService titles, MenuManager menus, MessageService messages) { this.plugin=plugin; this.titles=titles; this.menus=menus; this.messages=messages; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { if (player(sender) instanceof Player p) menus.openWarehouse(p,0); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "shop" -> { if (player(sender) instanceof Player p) menus.openShop(p,0); }
            case "custom" -> { if (player(sender) instanceof Player p) menus.openCustom(p); }
            case "set" -> { if (player(sender) instanceof Player p) { if (args.length < 2) messages.help(sender); else titles.select(p,args[1]); } }
            case "clear" -> { if (player(sender) instanceof Player p) titles.clear(p); }
            case "reload" -> { if (!admin(sender)) return true; plugin.reloadPlugin(); messages.send(sender,"reload-success"); }
            case "grant", "revoke" -> adminManage(sender,args);
            default -> messages.help(sender);
        }
        return true;
    }
    private void adminManage(CommandSender sender, String[] args) {
        if (!admin(sender)) return; if (args.length < 3) { messages.help(sender); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); String id=args[2]; boolean grant=args[0].equalsIgnoreCase("grant");
        var callback = (java.util.function.Consumer<Boolean>) ok -> messages.send(sender, ok ? "admin-success" : "admin-failed", Map.of("player",MessageService.escape(target.getName()==null?args[1]:target.getName()),"id",MessageService.escape(id)));
        if (grant) titles.grant(target.getUniqueId(),id,callback); else titles.revoke(target.getUniqueId(),id,callback);
    }
    private Player player(CommandSender sender) { if (!(sender instanceof Player p)) { messages.send(sender,"player-only"); return null; } if (!p.hasPermission("cloudtitle.use")) { messages.send(sender,"no-permission"); return null; } return p; }
    private boolean admin(CommandSender sender) { if (!sender.hasPermission("cloudtitle.admin")) { messages.send(sender,"no-permission"); return false; } return true; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length==1) return match(args[0], sender.hasPermission("cloudtitle.admin") ? List.of("shop","custom","set","clear","grant","revoke","reload") : List.of("shop","custom","set","clear"));
        if (args.length==2 && args[0].equalsIgnoreCase("set") && sender instanceof Player p && titles.data(p.getUniqueId())!=null) return match(args[1],new ArrayList<>(titles.data(p.getUniqueId()).owned()));
        if (args.length==2 && (args[0].equalsIgnoreCase("grant")||args[0].equalsIgnoreCase("revoke"))) return match(args[1],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if (args.length==3 && args[0].equalsIgnoreCase("grant")) return match(args[2],new ArrayList<>(plugin.configs().definitions().keySet()));
        return List.of();
    }
    private static List<String> match(String prefix,Collection<String> values) { String lower=prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }
}