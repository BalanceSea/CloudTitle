package github.balncesea.cloudTitle.model;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

public record TitleDefinition(
        String id, String name, List<String> description, Material icon,
        List<Buff> buffs, Shop shop, boolean custom) {
    public record Buff(PotionEffectType type, int amplifier, boolean particles, boolean icon) {}
    public record Shop(
            boolean enabled,
            boolean displayed,
            CostType type,
            double price,
            String permission,
            String bypassPermission,
            String requirementDisplay,
            List<ItemRequirement> items,
            List<PapiCondition> papiConditions) {}
    public record PapiCondition(
            String placeholder,
            NumericOperator operator,
            BigDecimal expected,
            String display) {}
    public enum NumericOperator {
        GREATER_THAN(">"),
        GREATER_OR_EQUAL(">="),
        LESS_THAN("<"),
        LESS_OR_EQUAL("<="),
        EQUAL("=="),
        NOT_EQUAL("!=");

        private final String symbol;

        NumericOperator(String symbol) { this.symbol = symbol; }
        public String symbol() { return symbol; }

        public boolean test(BigDecimal actual, BigDecimal expected) {
            int comparison = actual.compareTo(expected);
            return switch (this) {
                case GREATER_THAN -> comparison > 0;
                case GREATER_OR_EQUAL -> comparison >= 0;
                case LESS_THAN -> comparison < 0;
                case LESS_OR_EQUAL -> comparison <= 0;
                case EQUAL -> comparison == 0;
                case NOT_EQUAL -> comparison != 0;
            };
        }

        public static NumericOperator parse(String value) {
            return switch (value == null ? "" : value.trim()) {
                case ">" -> GREATER_THAN;
                case ">=", "≥" -> GREATER_OR_EQUAL;
                case "<" -> LESS_THAN;
                case "<=", "≤" -> LESS_OR_EQUAL;
                case "==", "=" -> EQUAL;
                case "!=", "≠" -> NOT_EQUAL;
                default -> null;
            };
        }
    }
    public record ItemRequirement(ItemSource source, String id, int amount, String display) {
        public String key() {
            return source.name().toLowerCase(Locale.ROOT) + ":" + id.toLowerCase(Locale.ROOT);
        }
    }
    public enum ItemSource {
        VANILLA, CRAFTENGINE;

        public static ItemSource parse(String value) {
            if (value == null) return VANILLA;
            return switch (value.toLowerCase()) {
                case "craftengine", "ce" -> CRAFTENGINE;
                default -> VANILLA;
            };
        }
    }
    public enum CostType { MONEY, POINTS, PERMISSION, ITEM, PAPI, FREE;
        public static CostType parse(String value) {
            try { return valueOf(value.toUpperCase()); } catch (Exception ignored) { return FREE; }
        }
    }
}
