package github.balncesea.cloudTitle.hook;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.PlayerTitleData;
import github.balncesea.cloudTitle.model.TitleDefinition;
import github.balncesea.cloudTitle.service.MessageService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class CloudTitleExpansion extends PlaceholderExpansion {
    private final CloudTitle plugin;
    public CloudTitleExpansion(CloudTitle plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "cloudtitle"; }
    @Override public @NotNull String getAuthor() { return String.join(",", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return ""; PlayerTitleData data = plugin.titles().data(player.getUniqueId()); if (data == null) return "";
        TitleDefinition title = plugin.titles().displayedDefinition(data);
        return switch (params.toLowerCase()) {
            case "title", "name" -> title == null ? "" : plugin.messages().legacy(title.name());
            case "title_minimessage", "minimessage" -> title == null ? "" : title.name();
            case "title_plain", "plain" -> title == null ? "" : plugin.messages().plain(title.name());
            case "description" -> title == null ? "" : plugin.messages().plain(String.join(" ", title.description()));
            case "selected_id" -> data.selected() == null ? "" : data.selected();
            case "displayed_id" -> title == null ? "" : title.id();
            case "owned_count" -> String.valueOf(data.owned().size());
            default -> null;
        };
    }
}
