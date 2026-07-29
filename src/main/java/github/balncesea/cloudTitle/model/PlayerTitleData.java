package github.balncesea.cloudTitle.model;

import java.util.*;

public final class PlayerTitleData {
    private final UUID uuid;
    private final Set<String> owned;
    private final Map<String, TitleDefinition> customTitles;
    private final Map<String, Map<String, Integer>> itemProgress;
    private String selected;
    private String appliedBuffs;
    private final String appliedServer;

    public PlayerTitleData(UUID uuid, Set<String> owned, String selected,
                           Map<String, TitleDefinition> customTitles,
                           Map<String, Map<String, Integer>> itemProgress,
                           String appliedBuffs,
                           String appliedServer) {
        this.uuid = uuid;
        this.owned = new HashSet<>(owned);
        this.selected = selected;
        this.customTitles = new HashMap<>(customTitles);
        this.itemProgress = new HashMap<>();
        itemProgress.forEach((titleId, progress) -> this.itemProgress.put(titleId, new HashMap<>(progress)));
        this.appliedBuffs = appliedBuffs == null ? "" : appliedBuffs;
        this.appliedServer = appliedServer == null ? "" : appliedServer;
    }
    public UUID uuid() { return uuid; }
    public Set<String> owned() { return Collections.unmodifiableSet(owned); }
    public String selected() { return selected; }
    public void selected(String selected) { this.selected = selected; }
    public Map<String, TitleDefinition> customTitles() { return Collections.unmodifiableMap(customTitles); }
    public Map<String, Integer> itemProgress(String titleId) {
        return Collections.unmodifiableMap(itemProgress.getOrDefault(titleId, Map.of()));
    }
    public void itemProgress(String titleId, Map<String, Integer> progress) {
        itemProgress.put(titleId, new HashMap<>(progress));
    }
    public void clearItemProgress(String titleId) { itemProgress.remove(titleId); }
    public String appliedBuffs() { return appliedBuffs; }
    public void appliedBuffs(String value) { appliedBuffs = value; }
    public String appliedServer() { return appliedServer; }
    public void grant(String id) { owned.add(id); }
    public void revoke(String id) { owned.remove(id); customTitles.remove(id); }
    public void addCustom(TitleDefinition title) { customTitles.put(title.id(), title); owned.add(title.id()); }
}
