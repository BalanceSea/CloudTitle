package github.balncesea.cloudTitle.service;

import github.balncesea.cloudTitle.CloudTitle;
import github.balncesea.cloudTitle.model.TitleDefinition;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class PlaceholderConditionService {
    private final CloudTitle plugin;

    public PlaceholderConditionService(CloudTitle plugin) {
        this.plugin = plugin;
    }

    public Evaluation evaluate(Player player, List<TitleDefinition.PapiCondition> conditions) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return Evaluation.unavailable();
        }
        List<ConditionResult> results = new ArrayList<>();
        boolean passed = !conditions.isEmpty();
        for (TitleDefinition.PapiCondition condition : conditions) {
            String raw;
            try {
                raw = PlaceholderAPI.setPlaceholders(player, condition.placeholder()).trim();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("解析 PAPI 条件失败 " + condition.placeholder() + ": " + exception.getMessage());
                raw = condition.placeholder();
            }
            BigDecimal actual = parseNumber(raw);
            boolean conditionPassed = actual != null && condition.operator().test(actual, condition.expected());
            results.add(new ConditionResult(condition, raw, actual, conditionPassed));
            passed &= conditionPassed;
        }
        return new Evaluation(true, passed, List.copyOf(results));
    }

    private static BigDecimal parseNumber(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record ConditionResult(
            TitleDefinition.PapiCondition condition,
            String rawValue,
            BigDecimal actualValue,
            boolean passed) {}

    public record Evaluation(
            boolean available,
            boolean passed,
            List<ConditionResult> results) {
        public static Evaluation unavailable() {
            return new Evaluation(false, false, List.of());
        }
    }
}
